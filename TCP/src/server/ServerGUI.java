package server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import com.sun.net.httpserver.*;
import util.Base64Util;

public class ServerGUI {
    // 服务器配置
    private static final String SERVER_VERSION = "1.1";
    private static final int TCP_SERVER_PORT = 54321;
    private static final int HTTP_SERVER_PORT = 54322;
    private static final String DOWNLOAD_PATH = "/download/client.jar";

    // UI组件
    private JFrame frame;
    private JTextArea textArea;
    private JTextField sendField;
    private JButton sendButton;

    // 网络组件
    private ServerSocket tcpServerSocket;
    private HttpServer httpServer;
    private ExecutorService executor;
    private final List<BufferedWriter> clientWriters = new ArrayList<>();

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                SqliteUtil.initDatabase();
                ServerGUI window = new ServerGUI();
                window.frame.setVisible(true);
                window.startServers();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "服务器启动失败: " + e.getMessage());
            }
        });
    }

    public ServerGUI() {
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("TCP Server (TCP:" + TCP_SERVER_PORT + " HTTP:" + HTTP_SERVER_PORT + ")");
        frame.setBounds(100, 100, 600, 550);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout(0, 0));
        frame.setLocationRelativeTo(null);

        textArea = new JTextArea();
        textArea.setEditable(false);
        frame.getContentPane().add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        sendField = new JTextField();
        sendButton = new JButton("发送给所有客户端");
        bottomPanel.add(sendField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendBroadcastMessage());

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                shutdownServers();
            }
        });
    }

    private void startServers() {
        executor = Executors.newCachedThreadPool();

        // 启动TCP服务器
        executor.execute(this::startTcpServer);

        // 启动HTTP服务器
        executor.execute(this::startHttpServer);
    }

    private void startTcpServer() {
        try {
            tcpServerSocket = new ServerSocket(TCP_SERVER_PORT);
            appendMessage("[TCP] 服务器启动，监听端口：" + TCP_SERVER_PORT);

            while (!tcpServerSocket.isClosed()) {
                Socket clientSocket = tcpServerSocket.accept();
                appendMessage("[TCP] 客户端已连接：" + clientSocket.getInetAddress());

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream()));

                synchronized (clientWriters) {
                    clientWriters.add(writer);
                }

                executor.execute(new ClientHandler(clientSocket, writer));
            }
        } catch (IOException e) {
            if (!tcpServerSocket.isClosed()) {
                appendMessage("[TCP] 服务器异常: " + e.getMessage());
            }
        }
    }

    private void startHttpServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(HTTP_SERVER_PORT), 0);
            httpServer.createContext(DOWNLOAD_PATH, new HttpFileHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            appendMessage("[HTTP] 文件服务器启动，端口：" + HTTP_SERVER_PORT);
            appendMessage("[HTTP] 文件下载地址: http://" + getLocalIP() + ":" + HTTP_SERVER_PORT + DOWNLOAD_PATH);
        } catch (IOException e) {
            appendMessage("[HTTP] 服务器启动失败: " + e.getMessage());
        }
    }

    private String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    class HttpFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                File file = new File("client.jar");
                if (file.exists()) {
                    exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                    exchange.sendResponseHeaders(200, file.length());

                    try (OutputStream os = exchange.getResponseBody();
                         FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                    appendMessage("[HTTP] 客户端下载文件: " + file.getName());
                } else {
                    String response = "404 File Not Found";
                    exchange.sendResponseHeaders(404, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    appendMessage("[HTTP] 文件未找到: client.jar");
                }
            }
            exchange.close();
        }
    }

    private void sendBroadcastMessage() {
        String raw = sendField.getText().trim();
        if (!raw.isEmpty()) {
            String encoded = Base64Util.encode(raw);
            broadcastToClients(encoded);
            appendMessage("[TCP] 服务器发送: " + raw);
            sendField.setText("");
        }
    }

    private void broadcastToClients(String encodedMessage) {
        synchronized (clientWriters) {
            for (BufferedWriter writer : new ArrayList<>(clientWriters)) {
                try {
                    writer.write(encodedMessage + "\n");
                    writer.flush();
                } catch (IOException e) {
                    appendMessage("[TCP] 向客户端发送失败: " + e.getMessage());
                    clientWriters.remove(writer);
                }
            }
        }
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    private void shutdownServers() {
        try {
            // 关闭TCP服务器
            if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
                tcpServerSocket.close();
            }

            // 关闭HTTP服务器
            if (httpServer != null) {
                httpServer.stop(0);
            }

            // 关闭线程池
            if (executor != null) {
                executor.shutdownNow();
            }

            // 关闭所有客户端连接
            synchronized (clientWriters) {
                for (BufferedWriter writer : clientWriters) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {}
                }
                clientWriters.clear();
            }

            appendMessage("服务器已关闭");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedWriter writer;

        public ClientHandler(Socket socket, BufferedWriter writer) {
            this.socket = socket;
            this.writer = writer;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("VERSION|")) {
                        handleVersionCheck(line, writer);
                    } else if (line.startsWith("FILE|")) {
                        handleFileUpload(line, writer, socket);
                    } else {
                        handleTextMessage(line, writer, socket);
                    }
                }
            } catch (IOException e) {
                appendMessage("[TCP] 客户端连接异常: " + e.getMessage());
            } finally {
                synchronized (clientWriters) {
                    clientWriters.remove(writer);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        private void handleVersionCheck(String line, BufferedWriter writer) throws IOException {
            String[] parts = line.split("\\|");
            String clientVersion = parts.length >= 2 ? parts[1] : "";

            if (isNewerVersionAvailable(clientVersion)) {
                String downloadUrl = "http://" + socket.getLocalAddress().getHostAddress() +
                        ":" + HTTP_SERVER_PORT + DOWNLOAD_PATH;
                String response = "UPDATE|" + SERVER_VERSION + "|" + downloadUrl;
                writer.write(response + "\n");
                appendMessage("[TCP] 客户端请求版本检查，发送更新: " + downloadUrl);
            } else {
                writer.write("CURRENT\n");
                appendMessage("[TCP] 客户端版本已是最新");
            }
            writer.flush();
        }

        private boolean isNewerVersionAvailable(String clientVersion) {
            // 简化的版本比较逻辑
            return !clientVersion.equals(SERVER_VERSION);
        }

        private void handleFileUpload(String line, BufferedWriter writer, Socket socket) throws IOException {
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) {
                appendMessage("[TCP] 文件上传协议错误: " + line);
                return;
            }

            String filename = parts[1];
            String base64Content = parts[2];
            try {
                byte[] fileBytes = Base64Util.decodeToBytes(base64Content);
                File uploadDir = new File("uploads");
                if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                    throw new IOException("无法创建上传目录");
                }

                File outFile = new File(uploadDir, filename);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(fileBytes);
                }

                appendMessage("[TCP] 已保存文件: " + outFile.getAbsolutePath());
                saveFileLog(socket.getInetAddress().toString(), filename, outFile.getAbsolutePath());
                writer.write(Base64Util.encode("文件 " + filename + " 已接收并保存") + "\n");
            } catch (IOException e) {
                appendMessage("[TCP] 保存文件失败: " + e.getMessage());
                writer.write(Base64Util.encode("保存文件失败: " + e.getMessage()) + "\n");
            }
            writer.flush();
        }

        private void handleTextMessage(String line, BufferedWriter writer, Socket socket) throws IOException {
            try {
                String decodedMessage = Base64Util.decode(line);
                appendMessage("[TCP] 来自" + socket.getInetAddress() + "的消息: " + decodedMessage);
                saveChatLog(socket.getInetAddress().toString(), decodedMessage);
                writer.write(Base64Util.encode("服务器已收到: " + decodedMessage) + "\n");
            } catch (IllegalArgumentException e) {
                appendMessage("[TCP] 解码失败，收到非Base64格式数据: " + line);
                writer.write(Base64Util.encode("解码失败：无效的Base64数据") + "\n");
            }
            writer.flush();
        }

        private void saveChatLog(String clientAddr, String message) {
            String sql = "INSERT INTO chat_log(client_addr, message) VALUES(?, ?);";
            try (Connection conn = SqliteUtil.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, clientAddr);
                pstmt.setString(2, message);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                appendMessage("[DB] 保存聊天记录失败: " + e.getMessage());
            }
        }

        private void saveFileLog(String clientAddr, String filename, String path) {
            String sql = "INSERT INTO file_log(client_addr, filename, path) VALUES(?, ?, ?);";
            try (Connection conn = SqliteUtil.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, clientAddr);
                pstmt.setString(2, filename);
                pstmt.setString(3, path);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                appendMessage("[DB] 保存文件记录失败: " + e.getMessage());
            }
        }
    }
}
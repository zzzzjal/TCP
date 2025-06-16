package server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import util.Base64Util;

public class ServerGUI {
    private static final String SERVER_VERSION = "1.1";
    private static final String CLIENT_DOWNLOAD_URL = "http://example.com/client_new.jar";
    private static final int SERVER_PORT = 54321;

    private JFrame frame;
    private JTextArea textArea;
    private JTextField sendField;
    private JButton sendButton;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private final List<BufferedWriter> clientWriters = new ArrayList<>();

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                SqliteUtil.initDatabase();
                ServerGUI window = new ServerGUI();
                window.frame.setVisible(true);
                window.startServer();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "服务器启动失败: " + e.getMessage());
            }
        });
    }

    public ServerGUI() {
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("TCP Server");
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
                shutdownServer();
            }
        });
    }

    private void sendBroadcastMessage() {
        String raw = sendField.getText().trim();
        if (!raw.isEmpty()) {
            String encoded = Base64Util.encode(raw);
            broadcastToClients(encoded);
            appendMessage("服务器发送: " + raw);
            sendField.setText("");
        }
    }

    private void startServer() {
        executor = Executors.newCachedThreadPool();
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                appendMessage("服务器启动，监听端口：" + SERVER_PORT);

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    appendMessage("客户端已连接：" + clientSocket.getInetAddress());

                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(clientSocket.getOutputStream()));

                    synchronized (clientWriters) {
                        clientWriters.add(writer);
                    }

                    executor.execute(new ClientHandler(clientSocket, writer));
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    appendMessage("服务器异常: " + e.getMessage());
                }
            }
        });
    }

    private void broadcastToClients(String encodedMessage) {
        synchronized (clientWriters) {
            for (BufferedWriter writer : new ArrayList<>(clientWriters)) {
                try {
                    writer.write(encodedMessage + "\n");
                    writer.flush();
                } catch (IOException e) {
                    appendMessage("向客户端发送失败: " + e.getMessage());
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

    private void shutdownServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            synchronized (clientWriters) {
                for (BufferedWriter writer : clientWriters) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {}
                }
                clientWriters.clear();
            }
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
                appendMessage("客户端连接异常: " + e.getMessage());
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
            String response = !clientVersion.equals(SERVER_VERSION)
                    ? "YES|" + SERVER_VERSION + "|" + CLIENT_DOWNLOAD_URL
                    : "NO";
            writer.write(response + "\n");
            writer.flush();
        }

        private void handleFileUpload(String line, BufferedWriter writer, Socket socket) throws IOException {
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) {
                appendMessage("文件上传协议错误: " + line);
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

                appendMessage("已保存文件: " + outFile.getAbsolutePath());
                saveFileLog(socket.getInetAddress().toString(), filename, outFile.getAbsolutePath());
                writer.write(Base64Util.encode("文件 " + filename + " 已接收并保存") + "\n");
            } catch (IOException e) {
                appendMessage("保存文件失败: " + e.getMessage());
                writer.write(Base64Util.encode("保存文件失败: " + e.getMessage()) + "\n");
            }
            writer.flush();
        }

        private void handleTextMessage(String line, BufferedWriter writer, Socket socket) throws IOException {
            try {
                String decodedMessage = Base64Util.decode(line);
                appendMessage("来自" + socket.getInetAddress() + "的消息: " + decodedMessage);
                saveChatLog(socket.getInetAddress().toString(), decodedMessage);
                writer.write(Base64Util.encode("服务器已收到: " + decodedMessage) + "\n");
            } catch (IllegalArgumentException e) {
                appendMessage("解码失败，收到非Base64格式数据: " + line);
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
                appendMessage("保存聊天记录失败: " + e.getMessage());
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
                appendMessage("保存文件记录失败: " + e.getMessage());
            }
        }
    }
}
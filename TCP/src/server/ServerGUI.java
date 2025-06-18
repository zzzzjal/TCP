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
    private static final int SERVER_PORT = 54321;  // 服务器监听端口

    private JFrame frame;               // 主窗口
    private JTextArea textArea;        // 消息显示区域
    private JTextField sendField;      // 消息发送输入框
    private JButton sendButton;        // 发送按钮
    private ServerSocket serverSocket; // 服务器套接字
    private ExecutorService executor;  // 线程池
    private final List<BufferedWriter> clientWriters = new ArrayList<>(); // 客户端输出流列表

    // 版本信息内部类
    private static class VersionInfo {
        public static final String CURRENT_VERSION = "v1.1";
        public static final String UPDATE_URL = "https://github.com/zzzzjal/TCP/releases/download/" + CURRENT_VERSION + "/TCP.jar";

        // 版本比较方法
        public static boolean isNewerVersion(String version1, String version2) {
            // 移除版本号中的'v'前缀
            String v1 = version1.replace("v", "");
            String v2 = version2.replace("v", "");
            
            // 分割版本号
            String[] v1Parts = v1.split("\\.");
            String[] v2Parts = v2.split("\\.");
            
            // 比较主版本号
            int v1Major = Integer.parseInt(v1Parts[0]);
            int v2Major = Integer.parseInt(v2Parts[0]);
            if (v1Major != v2Major) {
                return v1Major < v2Major;
            }
            
            // 比较次版本号
            int v1Minor = Integer.parseInt(v1Parts[1]);
            int v2Minor = Integer.parseInt(v2Parts[1]);
            return v1Minor < v2Minor;
        }
    }
    public static void main(String[] args) {
        // 在事件调度线程中初始化GUI
        EventQueue.invokeLater(() -> {
            try {
                SqliteUtil.initDatabase();  // 初始化数据库
                ServerGUI window = new ServerGUI();  // 创建服务器窗口
                window.frame.setVisible(true);  // 显示窗口
                window.startServer();  // 启动服务器
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "服务器启动失败: " + e.getMessage());
            }
        });
    }

    // 构造函数
    public ServerGUI() {
        initializeGUI();  // 初始化GUI界面
    }

    // 初始化GUI界面
    private void initializeGUI() {
        frame = new JFrame("TCP Server");
        frame.setBounds(100, 100, 600, 550);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout(0, 0));
        frame.setLocationRelativeTo(null);  // 窗口居中

        // 消息显示区域
        textArea = new JTextArea();
        textArea.setEditable(false);
        frame.getContentPane().add(new JScrollPane(textArea), BorderLayout.CENTER);

        // 底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        sendField = new JTextField();
        sendButton = new JButton("发送给所有客户端");
        bottomPanel.add(sendField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        // 发送按钮事件监听
        sendButton.addActionListener(e -> sendBroadcastMessage());

        // 窗口关闭事件监听
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                shutdownServer();  // 关闭服务器
            }
        });
    }

    // 广播消息给所有客户端
    private void sendBroadcastMessage() {
        String raw = sendField.getText().trim();
        if (!raw.isEmpty()) {
            String encoded = Base64Util.encode(raw);  // Base64编码
            broadcastToClients(encoded);
            appendMessage("服务器发送: " + raw);
            sendField.setText("");
        }
    }

    // 启动服务器
    private void startServer() {
        executor = Executors.newCachedThreadPool();  // 创建线程池
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                appendMessage("服务器启动，监听端口：" + SERVER_PORT);

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();  // 接受客户端连接
                    appendMessage("客户端已连接：" + clientSocket.getInetAddress());

                    // 创建客户端输出流
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(clientSocket.getOutputStream()));

                    // 将输出流添加到列表
                    synchronized (clientWriters) {
                        clientWriters.add(writer);
                    }

                    // 为客户端创建处理线程
                    executor.execute(new ClientHandler(clientSocket, writer));
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    appendMessage("服务器异常: " + e.getMessage());
                }
            }
        });
    }

    // 广播消息给所有客户端
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

    // 在消息区域追加消息
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    // 关闭服务器
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

    // 客户端处理线程
    class ClientHandler implements Runnable {
        private final Socket socket;      // 客户端套接字
        private final BufferedWriter writer;  // 客户端输出流

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
                    // 根据消息类型处理不同请求
                    if (line.startsWith("VERSION_CHECK|")) {
                        handleVersionCheck(line, writer);  // 处理版本检查
                    } else if (line.startsWith("FILE|")) {
                        handleFileUpload(line, writer, socket);  // 处理文件上传
                    } else {
                        handleTextMessage(line, writer, socket);  // 处理文本消息
                    }
                }
            } catch (IOException e) {
                appendMessage("客户端连接异常: " + e.getMessage());
            } finally {
                // 客户端断开连接时清理资源
                synchronized (clientWriters) {
                    clientWriters.remove(writer);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        // 处理版本检查请求
        private void handleVersionCheck(String line, BufferedWriter writer) throws IOException {
            String[] parts = line.split("\\|");
            String clientVersion = parts[1];

            if (VersionInfo.isNewerVersion(clientVersion, VersionInfo.CURRENT_VERSION)) {
                writer.write("NEED_UPDATE|" + VersionInfo.CURRENT_VERSION + "|" + VersionInfo.UPDATE_URL + "\n");
            } else {
                writer.write("CURRENT_VERSION\n");
            }
            writer.flush();
        }

        // 处理文件上传
        private void handleFileUpload(String line, BufferedWriter writer, Socket socket) throws IOException {
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3) {
                appendMessage("文件上传协议错误: " + line);
                return;
            }

            String filename = parts[1];
            String base64Content = parts[2];
            try {
                byte[] fileBytes = Base64Util.decode(base64Content);  // Base64解码
                File uploadDir = new File("uploads");
                if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                    throw new IOException("无法创建上传目录");
                }

                // 保存文件
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

        // 处理文本消息
        private void handleTextMessage(String line, BufferedWriter writer, Socket socket) throws IOException {
            try {
                String decodedMessage = Base64Util.decodeToString(line);  // Base64解码
                appendMessage("来自" + socket.getInetAddress() + "的消息: " + decodedMessage);
                saveChatLog(socket.getInetAddress().toString(), decodedMessage);  // 保存聊天记录
                writer.write(Base64Util.encode("服务器已收到: " + decodedMessage) + "\n");
            } catch (IllegalArgumentException e) {
                appendMessage("解码失败，收到非Base64格式数据: " + line);
                writer.write(Base64Util.encode("解码失败：无效的Base64数据") + "\n");
            }
            writer.flush();
        }

        // 保存聊天记录到数据库
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

        // 保存文件记录到数据库
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
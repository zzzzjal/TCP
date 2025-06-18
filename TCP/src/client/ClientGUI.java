package client;

import util.Base64Util;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientGUI {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 54321;
    private static final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private static final String UPDATE_URL = "https://ghfile.geekertao.top/https://github.com/zzzzjal/TCP/releases/download/v1.1/TCP.jar";

    private JFrame frame;
    private JTextArea textArea;
    private JTextField inputField;
    private PrintWriter out;
    private Socket socket;

    // 版本信息内部类
    private static class VersionInfo {
        public static final String CURRENT_VERSION = "v1.0";
        public static final String NEW_VERSION = "v1.1";

        public static boolean isNewerVersion(String clientVersion, String serverVersion) {
            // 移除版本号中的'v'前缀
            String cv = clientVersion.replace("v", "");
            String sv = serverVersion.replace("v", "");

            // 分割版本号
            String[] cvParts = cv.split("\\.");
            String[] svParts = sv.split("\\.");

            // 比较主版本号
            int cvMajor = Integer.parseInt(cvParts[0]);
            int svMajor = Integer.parseInt(svParts[0]);
            if (cvMajor != svMajor) {
                return cvMajor < svMajor;
            }

            // 比较次版本号
            int cvMinor = Integer.parseInt(cvParts[1]);
            int svMinor = Integer.parseInt(svParts[1]);
            return cvMinor < svMinor;
        }
    }

    public ClientGUI() {
        initializeGUI();
        connectToServer();
    }

    private void initializeGUI() {
        frame = new JFrame("TCP客户端 v" + VersionInfo.CURRENT_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);

        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);

        inputField = new JTextField();
        JButton sendButton = new JButton("发送");
        JButton uploadButton = new JButton("上传文件");
        JButton updateButton = new JButton("检查更新");

        sendButton.addActionListener(e -> sendMessage());
        uploadButton.addActionListener(e -> uploadFile());
        updateButton.addActionListener(e -> checkVersionWithServer());
        inputField.addActionListener(e -> sendMessage());

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        buttonPanel.add(sendButton);
        buttonPanel.add(uploadButton);
        buttonPanel.add(updateButton);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.startsWith("NEED_UPDATE|") || line.equals("CURRENT_VERSION")) {
                            handleServerResponse(line);
                        } else {
                            String decodedMessage = Base64Util.decodeToString(line);
                            appendMessage("服务器: " + decodedMessage);
                        }
                    }
                } catch (IOException e) {
                    if (!isUpdating.get()) {
                        appendMessage("服务器连接断开: " + e.getMessage());
                        checkVersionWithServer(); // 连接断开时主动检查版本
                        reconnectToServer();
                    }
                } finally {
                    closeConnection();
                }
            }).start();

            appendMessage("已连接到服务器 " + SERVER_HOST + ":" + SERVER_PORT);
            // 连接成功后立即检查版本
            checkVersionWithServer();
        } catch (IOException e) {
            appendMessage("连接服务器失败: " + e.getMessage());
        }
    }

    private void reconnectToServer() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // 5秒后重试
                    socket = new Socket(SERVER_HOST, SERVER_PORT);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    appendMessage("已重新连接到服务器");
                    checkVersionWithServer(); // 重新连接后检查版本
                    break;
                } catch (Exception e) {
                    appendMessage("重新连接服务器失败，5秒后重试...");
                }
            }
        }).start();
    }

    private void checkVersionWithServer() {
        if (out != null) {
            out.println("VERSION_CHECK|" + VersionInfo.CURRENT_VERSION);
        }
    }

    private void checkForUpdates() {
        int choice = JOptionPane.showConfirmDialog(frame,
                "发现新版本 " + VersionInfo.NEW_VERSION + "，是否立即更新？",
                "版本更新",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            startDownload();
        }
    }

    private void handleServerResponse(String response) {
        if (response.startsWith("NEED_UPDATE|")) {
            String[] parts = response.split("\\|");
            String serverVersion = parts[1];
            if (VersionInfo.isNewerVersion(VersionInfo.CURRENT_VERSION, serverVersion)) {
                SwingUtilities.invokeLater(this::checkForUpdates);
            } else {
                appendMessage("当前已是最新版本");
            }
        } else if (response.equals("CURRENT_VERSION")) {
            appendMessage("当前已是最新版本");
        }
    }

    private void startDownload() {
        JDialog progressDialog = createProgressDialog();
        downloadAndUpdate(progressDialog);
    }

    private JDialog createProgressDialog() {
        JDialog dialog = new JDialog(frame, "下载进度", true);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout());

        JLabel label = new JLabel("正在下载更新...", JLabel.CENTER);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(e -> {
            dialog.dispose();
            isUpdating.set(false);
        });

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(cancelButton, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        return dialog;
    }

    private synchronized void downloadAndUpdate(JDialog progressDialog) {
        if (isUpdating.get()) {
            return;
        }
        if (!isUpdating.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));

                // 创建目标目录
                Path targetDir = Paths.get("D:\\TCP\\new");
                Files.createDirectories(targetDir);
                Path newJarPath = targetDir.resolve("TCP.jar");

                appendMessage("开始下载新版本: " + UPDATE_URL);

                // 下载文件
                downloadFileWithProgress(UPDATE_URL, newJarPath.toString(),
                        (progress) -> {
                            SwingUtilities.invokeLater(() -> {
                                JProgressBar bar = (JProgressBar) ((JPanel) progressDialog.getContentPane()
                                        .getComponent(0)).getComponent(1);
                                bar.setValue(progress);
                                bar.setString(String.format("%d%%", progress));
                            });
                        });

                // 下载完成后启动新版本
                appendMessage("下载完成，正在启动新版本...");
                ProcessBuilder pb = new ProcessBuilder(
                        "java", "-jar", newJarPath.toString()
                );
                pb.directory(new File("D:\\TCP\\new"));
                pb.start();

                // 关闭当前应用
                System.exit(0);

            } catch (Exception e) {
                appendMessage("自动更新失败: " + e.getMessage());
                SwingUtilities.invokeLater(progressDialog::dispose);
                isUpdating.set(false);
            }
        }).start();
    }

    private void downloadFileWithProgress(String fileURL, String savePath, ProgressListener listener) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(fileURL).openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(savePath)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;
            int fileSize = connection.getContentLength();

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                if (fileSize > 0) {
                    int progress = (int) (totalBytesRead * 100 / fileSize);
                    listener.onProgressUpdate(progress);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private void sendMessage() {
        String rawMessage = inputField.getText().trim();
        if (!rawMessage.isEmpty() && out != null) {
            String encodedMessage = Base64Util.encode(rawMessage);
            out.println(encodedMessage);
            appendMessage("我: " + rawMessage);
            inputField.setText("");
        }
    }

    private void uploadFile() {
        JFileChooser chooser = new JFileChooser();
        int ret = chooser.showOpenDialog(frame);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String protocol = "FILE|" + file.getName() + "|" + Base64Util.encode(fileBytes);
                out.println(protocol);
                appendMessage("正在上传文件: " + file.getName() + " (" + fileBytes.length + "字节)");
            } catch (IOException e) {
                appendMessage("上传文件失败: " + e.getMessage());
            }
        }
    }

    private void appendMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(msg + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientGUI();
        });
    }

    private interface ProgressListener {
        void onProgressUpdate(int progress);
    }
}
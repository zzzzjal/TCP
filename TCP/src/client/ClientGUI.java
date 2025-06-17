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
    private static final String MIRROR_URL = "https://github.moeyy.xyz/https://github.com/zzzzjal/TCP/releases/download/v1.1/TCP.jar";

    private JFrame frame;
    private JTextArea textArea;
    private JTextField inputField;
    private PrintWriter out;
    private Socket socket;

    // 版本信息内部类
    private static class VersionInfo {
        public static final String CURRENT_VERSION = "v1.0";
        public static final String NEW_VERSION = "v1.1";
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
        updateButton.addActionListener(e -> checkForUpdates());
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
                    }
                } finally {
                    closeConnection();
                }
            }).start();

            appendMessage("已连接到服务器 " + SERVER_HOST + ":" + SERVER_PORT);
            checkForUpdates();
        } catch (IOException e) {
            appendMessage("连接服务器失败: " + e.getMessage());
        }
    }

    private void checkForUpdates() {
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(frame,
                    "发现新版本 " + VersionInfo.NEW_VERSION + "，是否立即更新？\n(使用镜像加速下载)",
                    "版本更新",
                    JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                startDownload();
            }
        });
    }

    private void handleServerResponse(String response) {
        if (response.startsWith("NEED_UPDATE|")) {
            checkForUpdates();
        } else if (response.equals("CURRENT_VERSION")) {
            appendMessage("当前已是最新版本");
        }
    }

    private void startDownload() {
        // 创建下载进度对话框
        JDialog progressDialog = createProgressDialog();
        // 开始下载并更新
        downloadAndUpdate(progressDialog);
    }

    private JDialog createProgressDialog() {
        JDialog dialog = new JDialog(frame, "下载进度", true);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout());

        JLabel label = new JLabel("正在通过镜像下载更新...", JLabel.CENTER);
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
        // 双重检查确保只有一个更新线程运行
        if (isUpdating.get()) {
            return;
        }
        if (!isUpdating.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));

                // 获取当前JAR文件路径
                String currentJarPath = Paths.get(ClientGUI.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString();

                // 创建唯一临时文件
                Path tempDir = Files.createTempDirectory("TCP-update");
                Path tempNewJar = tempDir.resolve("TCP-new.jar");

                appendMessage("开始通过镜像下载新版本: " + MIRROR_URL);

                // 带进度显示的下载
                downloadFileWithProgress(MIRROR_URL, tempNewJar.toString(),
                        (progress) -> {
                            SwingUtilities.invokeLater(() -> {
                                JProgressBar bar = (JProgressBar) ((JPanel) progressDialog.getContentPane()
                                        .getComponent(0)).getComponent(1);
                                bar.setValue(progress);
                                bar.setString(String.format("%d%%", progress));
                            });
                        });

                // 创建更新脚本
                Path updateScript = createUpdateScript(currentJarPath, tempNewJar.toString());

                // 执行脚本并退出
                appendMessage("准备重启应用完成更新...");
                ProcessBuilder pb = new ProcessBuilder(
                        System.getProperty("os.name").toLowerCase().contains("win") ? "cmd" : "bash",
                        "/c",
                        updateScript.toString()
                );
                pb.start();
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

    private Path createUpdateScript(String currentJarPath, String newJarPath) throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        Path updateScript = Paths.get("update_" + timestamp +
                (System.getProperty("os.name").toLowerCase().contains("win") ? ".bat" : ".sh"));

        String scriptContent;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            scriptContent = String.format(
                    "@echo off\r\n" +
                            "timeout /t 3 >nul\r\n" +
                            "del \"%s\"\r\n" +
                            "move /Y \"%s\" \"%s\"\r\n" +
                            "start \"\" \"%s\"\r\n" +
                            "rmdir /s /q \"%s\"\r\n" +  // 删除临时目录
                            "del \"%%~f0\"\r\n",
                    currentJarPath,
                    newJarPath,
                    currentJarPath,
                    currentJarPath,
                    Paths.get(newJarPath).getParent().toString()
            );
        } else {
            scriptContent = String.format(
                    "#!/bin/bash\n" +
                            "sleep 3\n" +
                            "rm -f \"%s\"\n" +
                            "mv -f \"%s\" \"%s\"\n" +
                            "java -jar \"%s\" &\n" +
                            "rm -rf \"%s\"\n" +  // 删除临时目录
                            "rm -f \"$0\"\n",
                    currentJarPath,
                    newJarPath,
                    currentJarPath,
                    currentJarPath,
                    Paths.get(newJarPath).getParent().toString()
            );
        }

        Files.write(updateScript, scriptContent.getBytes());

        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            updateScript.toFile().setExecutable(true);
        }

        return updateScript;
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

    // 进度监听接口
    private interface ProgressListener {
        void onProgressUpdate(int progress);
    }
}
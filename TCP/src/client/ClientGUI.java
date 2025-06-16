package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import util.Base64Util;

public class ClientGUI extends JFrame {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 54321;

    private JTextArea textArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton uploadButton;
    private PrintWriter out;
    private final String currentVersion = "1.0";

    public ClientGUI() {
        initializeGUI();
        if (!checkForUpdate()) {
            connectToServer();
        }
    }

    private void initializeGUI() {
        setTitle("TCP Client - 版本 " + currentVersion);
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        textArea = new JTextArea();
        textArea.setEditable(false);
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        inputField = new JTextField();
        sendButton = new JButton("发送");

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        uploadButton = new JButton("上传文件");
        buttonPanel.add(sendButton);
        buttonPanel.add(uploadButton);

        panel.add(inputField, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);
        add(panel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        uploadButton.addActionListener(e -> uploadFile());

        inputField.addActionListener(e -> sendMessage());

        setVisible(true);
    }

    private boolean checkForUpdate() {
        try (Socket updateSocket = new Socket(SERVER_HOST, SERVER_PORT);
             PrintWriter updateOut = new PrintWriter(updateSocket.getOutputStream(), true);
             BufferedReader updateIn = new BufferedReader(new InputStreamReader(updateSocket.getInputStream()))) {

            updateOut.println("VERSION|" + currentVersion);
            String response = updateIn.readLine();

            if (response != null && response.startsWith("YES|")) {
                String[] parts = response.split("\\|");
                if (parts.length >= 3) {
                    appendMessage("发现新版本: " + parts[1] + " (当前: " + currentVersion + ")");
                    int option = JOptionPane.showConfirmDialog(this,
                            "发现新版本，是否立即下载更新？", "版本更新",
                            JOptionPane.YES_NO_OPTION);

                    if (option == JOptionPane.YES_OPTION) {
                        downloadNewClient(parts[2]);
                        JOptionPane.showMessageDialog(this,
                                "新版本已下载，请重启应用完成更新。");
                        return true;
                    }
                }
            } else {
                appendMessage("当前已是最新版本");
            }
        } catch (IOException e) {
            appendMessage("版本检查失败: " + e.getMessage());
        }
        return false;
    }

    private void downloadNewClient(String fileURL) throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();

        try (InputStream inputStream = httpConn.getInputStream();
             FileOutputStream outputStream = new FileOutputStream("client_new.jar")) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            appendMessage("新版本下载完成");
        } finally {
            httpConn.disconnect();
        }
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()))) {

                    String msg;
                    while ((msg = in.readLine()) != null) {
                        try {
                            String decoded = Base64Util.decode(msg);
                            appendMessage("服务器: " + decoded);
                        } catch (IllegalArgumentException e) {
                            appendMessage("服务器原始响应: " + msg);
                        }
                    }
                } catch (IOException e) {
                    appendMessage("与服务器连接断开: " + e.getMessage());
                }
            }).start();

            appendMessage("已连接到服务器 " + SERVER_HOST + ":" + SERVER_PORT);
        } catch (IOException e) {
            appendMessage("连接服务器失败: " + e.getMessage());
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
        int ret = chooser.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String protocol = "FILE|" + file.getName() + "|" + Base64Util.encodeBytes(fileBytes);
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}
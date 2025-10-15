package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

public class TelegramNotifier {
    private final String botToken;
    private final String chatId;
    private String parseMode = "HTML"; // ✅ По умолчанию HTML

    // --- Конструктор №1 (через Properties) ---
    public TelegramNotifier(Properties props) {
        this.botToken = props.getProperty("telegram.bot.token");
        this.chatId = props.getProperty("telegram.chat.id");
    }

    // --- Конструктор №2 (через аргументы) ---
    public TelegramNotifier(String tgToken, String tgChat) {
        this.botToken = tgToken;
        this.chatId = tgChat;
    }

    // --- Установка режима разметки ---
    public void setParseMode(String mode) {
        this.parseMode = mode;
    }

    // --- Отправка текстового сообщения ---
    public void sendMessage(String message) {
        try {
            // Безопасное кодирование текста
            String encodedText = URLEncoder.encode(message, "UTF-8");
            String urlString = String.format(
                    "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s&parse_mode=%s",
                    botToken, chatId, encodedText, parseMode
            );

            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                System.out.println("📨 Сообщение отправлено в Telegram");
            } else {
                System.out.println("⚠️ Ошибка отправки сообщения: " + responseCode);
                try (InputStream err = conn.getErrorStream()) {
                    if (err != null) {
                        System.out.println("Ответ Telegram: " + new String(err.readAllBytes()));
                    }
                }
            }
            conn.disconnect();

        } catch (Exception e) {
            System.out.println("❌ Ошибка отправки сообщения в Telegram: " + e.getMessage());
        }
    }

    // --- Отправка фото с подписью ---
    public void sendPhoto(String filePath, String caption) {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendPhoto");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {

                // chat_id
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
                out.writeBytes(chatId + "\r\n");

                // caption
                if (caption != null && !caption.isEmpty()) {
                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
                    out.writeBytes(caption + "\r\n");
                }

                // parse_mode
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"parse_mode\"\r\n\r\n");
                out.writeBytes(parseMode + "\r\n");

                // file
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"" + filePath + "\"\r\n");
                out.writeBytes("Content-Type: image/png\r\n\r\n");

                try (FileInputStream fis = new FileInputStream(filePath)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                out.writeBytes("\r\n");
                out.writeBytes("--" + boundary + "--\r\n");
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("📸 Скриншот отправлен в Telegram");
            } else {
                System.out.println("⚠️ Ошибка отправки фото: " + responseCode);
                try (InputStream err = conn.getErrorStream()) {
                    if (err != null) {
                        System.out.println("Ответ Telegram: " + new String(err.readAllBytes()));
                    }
                }
            }

            conn.disconnect();

        } catch (Exception e) {
            System.out.println("❌ Ошибка отправки скриншота в Telegram: " + e.getMessage());
        }
    }
}

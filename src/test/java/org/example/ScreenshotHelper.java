package org.example;

import com.microsoft.playwright.Page;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenshotHelper {

    public static String takeScreenshot(Page page, String testName) {
        try {
            // --- Папка для скриншотов ---
            String folderPath = "screenshots";
            File folder = new File(folderPath);
            if (!folder.exists() && !folder.mkdirs()) {
                System.out.println("⚠️ Не удалось создать папку для скриншотов: " + folderPath);
            }

            // --- Формируем имя файла ---
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "error_" + testName + "_" + timestamp + ".png";
            String filePath = folderPath + File.separator + fileName;

            // --- Делаем скриншот только видимой части (viewport) ---
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(filePath))
                    .setFullPage(false)); // 👈 только видимая область

            System.out.println("📸 Скриншот сохранён: " + filePath);
            return filePath;

        } catch (Exception e) {
            System.out.println("❌ Ошибка при снятии скриншота: " + e.getMessage());
            return null;
        }
    }
}

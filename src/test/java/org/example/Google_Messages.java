package org.example;

import com.microsoft.playwright.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;

public class Google_Messages {
    // читаем путь к сессии из config.properties, если не задано — дефолт
    private static final String DEFAULT_SESSION = "resources/sessions/messages-session.json";

    public static void main(String[] args) {
        // читаем креды (пример — если нужно в логике теста)
        String login = ConfigHelper.get("login");       // :contentReference[oaicite:4]{index=4}
        String password = ConfigHelper.get("password"); // :contentReference[oaicite:5]{index=5}
        String email = ConfigHelper.get("email");       // :contentReference[oaicite:6]{index=6}

        // получаем путь к файлу сессии (если в config.properties задано messages.session.path)
        String sessionFromConfig = null;
        try {
            sessionFromConfig = ConfigHelper.get("messages.session.path");
        } catch (RuntimeException ignored) {
            // свойство может отсутствовать — используем дефолт
        }
        String sessionPathStr = (sessionFromConfig != null && !sessionFromConfig.isBlank())
                ? sessionFromConfig
                : DEFAULT_SESSION;

        Path sessionFile = Paths.get(sessionPathStr);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setArgs(List.of(
                                    "--start-maximized",
                                    "--disable-blink-features=AutomationControlled",
                                    "--disable-infobars",
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage"
                            ))
            );

            // Если есть сохранённая сессия — пробуем открыть с ней
            if (Files.exists(sessionFile)) {
                System.out.println("📁 Найден файл сессии: " + sessionFile.toAbsolutePath());
                System.out.println("Пробуем открыть Google Messages с сохранённой авторизацией...");

                BrowserContext context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setStorageStatePath(sessionFile)
                                .setViewportSize(1920, 1080)
                                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/127.0.0.0 Safari/537.36")
                );

                Page page = context.newPage();
                page.navigate("https://messages.google.com/web/conversations");

                try {
                    page.waitForSelector("mws-conversation-list-item",
                            new Page.WaitForSelectorOptions().setTimeout(7_000));
                    System.out.println("✅ Сессия действительна — вход выполнен автоматически!");
                    System.out.println("Браузер остаётся открытым для проверки.");
                    // дальше можно читать сообщения из page
                    return;
                } catch (PlaywrightException e) {
                    System.out.println("⚠️ Сессия устарела или невалидна — удаляем старый файл и запускаем авторизацию заново...");
                    try {
                        Files.deleteIfExists(sessionFile);
                        System.out.println("🗑️ Старый файл сессии удалён: " + sessionFile);
                    } catch (IOException ioException) {
                        System.out.println("⚠️ Не удалось удалить старый файл: " + ioException.getMessage());
                    }
                } finally {
                    // Закрываем контекст, чтобы создать новый для QR авторизации
                    try { context.close(); } catch (Exception ignored) {}
                }
            }

            // --- Если сюда дошли — авторизация по QR (ручная) ---
            System.out.println("🔵 Открываем Google Messages для авторизации по QR...");
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setViewportSize(1920, 1080)
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/127.0.0.0 Safari/537.36")
            );

            Page page = context.newPage();
            page.navigate("https://messages.google.com/web/conversations");

            System.out.println("""
                =========================================================
                🔹 Авторизуйся в Google Messages вручную:
                   1. Отсканируй QR-код со смартфона
                   2. Дождись загрузки списка чатов (появится элемент mws-conversation-list-item)
                   3. После этого нажми Enter в консоли
                =========================================================
                """);

            // ждём нажатия Enter (ручное подтверждение, когда видишь что список чатов загрузился)
            System.in.read();

            // создаём папку, если её нет
            Files.createDirectories(sessionFile.getParent() == null ? Paths.get("resources/sessions") : sessionFile.getParent());

            // Сохраняем новую сессию
            context.storageState(new BrowserContext.StorageStateOptions()
                    .setPath(sessionFile));

            System.out.println("💾 Сессия успешно сохранена: " + sessionFile.toAbsolutePath());
            System.out.println("✅ Теперь тесты смогут открывать Google Messages уже авторизованным!");
            System.out.println("Браузер остаётся открытым для проверки.");

            // здесь можно добавить логику чтения последнего сообщения/парсинга кода и т.д.

        } catch (IOException e) {
            System.out.println("❌ Ошибка при работе с файлами: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("❌ Ошибка в процессе авторизации: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

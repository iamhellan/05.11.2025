package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class v2_id_authorization {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of("--start-maximized"))
        );
        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(null)
        );
        page = context.newPage();

        // --- Таймауты ---
        page.setDefaultTimeout(60_000);
        page.setDefaultNavigationTimeout(90_000);

        // --- Инициализируем TelegramNotifier ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginWithSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_id_authorization* стартовал (авторизация через Google Messages)");

        try {
            System.out.println("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/");

            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(1000);
            page.click("button#login-form-call");

            System.out.println("Вводим ID");
            String login = ConfigHelper.get("login");
            page.fill("input#auth_id_email", login);

            System.out.println("Вводим пароль");
            String password = ConfigHelper.get("password");
            page.fill("input#auth-form-password", password);

            System.out.println("Жмём 'Войти' в форме авторизации");
            page.locator("button.auth-button:has-text('Войти')").click();

            System.out.println("Теперь решай капчу вручную — жду появление кнопки 'Выслать код' (до 10 минут)...");
            try {
                page.waitForSelector("button.phone-sms-modal-content__send",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Кнопка 'Выслать код' появилась ✅");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Кнопка 'Выслать код' не появилась — капча не решена или изменился UI!");
            }

            System.out.println("Жмём 'Выслать код'");
            Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
            try {
                sendCodeButton.click();
            } catch (Exception e) {
                page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
            }

            System.out.println("Жду поле для кода (до 10 минут)...");
            page.waitForSelector("input.phone-sms-modal-code__input",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000)
                            .setState(WaitForSelectorState.VISIBLE)
            );

            // --- Универсальный поиск файла сессии Google Messages ---
            Path projectRoot = Paths.get(System.getProperty("user.dir"));
            Path[] possiblePaths = new Path[]{
                    projectRoot.resolve("resources/sessions/messages-session.json"),
                    projectRoot.resolve("src/test/resources/sessions/messages-session.json"),
                    projectRoot.resolve("src/test/java/org/example/resources/sessions/messages-session.json")
            };

            Path sessionPath = null;
            for (Path path : possiblePaths) {
                if (path.toFile().exists()) {
                    sessionPath = path;
                    break;
                }
            }

            if (sessionPath == null)
                throw new RuntimeException("❌ Файл сессии Google Messages не найден ни в одном из стандартных путей!");

            System.out.println("📁 Используем файл сессии: " + sessionPath.toAbsolutePath());

            // --- Открываем Google Messages с сохранённой авторизацией ---
            BrowserContext messagesContext = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(sessionPath)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            // Кликаем по верхнему чату
            Locator chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.click();
            messagesPage.waitForTimeout(1000);

            Locator messageNodes = messagesPage.locator(
                    "mws-message-part-content div.text-msg-content div.text-msg.msg-content div.ng-star-inserted"
            );
            int count = messageNodes.count();
            if (count == 0)
                throw new RuntimeException("Сообщения не найдены — проверь авторизацию Google Messages");

            String smsText = messageNodes.nth(count - 1).innerText().trim();
            System.out.println("📩 Последнее сообщение: " + smsText);

            String code = smsText.split("\\s+")[0].trim();
            System.out.println("✅ Код подтверждения: " + code);

            page.bringToFront();
            messagesContext.close();

            page.fill("input.phone-sms-modal-code__input", code);
            page.click("button:has-text('Подтвердить')");

            System.out.println("Авторизация завершена ✅");

            page.click("a.header-lk-box-link[title='Личный кабинет']");
            Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
            if (closeCrossLk.isVisible()) closeCrossLk.click();

            page.click("a.ap-left-nav__item_exit");
            page.waitForTimeout(500);
            page.click("button.swal2-confirm.swal2-styled");

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage(
                    "✅ *Тест завершён:* v2_id_authorization\n" +
                            "• Авторизация — выполнена\n" +
                            "• Код из Google Messages — получен\n" +
                            "• ЛК проверен и выход произведён\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*"
            );

        } catch (Exception e) {
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_id_authorization");
            tg.sendMessage("🚨 Ошибка в тесте *v2_id_authorization*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}

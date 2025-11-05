package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_number_authorization {
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

        // --- Telegram ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginByPhoneAndPassword() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_number_authorization* стартовал (авторизация по номеру телефона + SMS Google Messages)");

        try {
            System.out.println("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/");

            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(800);
            page.click("button#login-form-call");

            System.out.println("Выбираем метод входа по телефону");
            page.waitForTimeout(800);
            page.click("button.c-input-material__custom.custom-functional-button");

            System.out.println("Вводим номер телефона");
            String phone = ConfigHelper.get("phone");
            page.fill("input.phone-input__field[type='tel']", phone);

            System.out.println("Вводим пароль");
            String password = ConfigHelper.get("password");
            page.fill("input[type='password']", password);

            System.out.println("Жмём 'Войти'");
            page.waitForTimeout(800);
            page.click("button.auth-button.auth-button--block.auth-button--theme-secondary");

            // ---- Ждём решение капчи ----
            System.out.println("Жду появления кнопки 'Выслать код' (до 10 минут)...");
            page.waitForSelector("button:has-text('Выслать код')",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000)
                            .setState(WaitForSelectorState.VISIBLE)
            );
            System.out.println("Кнопка 'Выслать код' появилась ✅");

            // ---- Жмём "Выслать код" ----
            System.out.println("Жмём 'Выслать код'");
            Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
            try {
                sendCodeButton.click();
                System.out.println("Кнопка 'Выслать код' нажата ✅");
            } catch (Exception e) {
                System.out.println("Клик не сработал — пробуем через JS");
                page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
            }

            // ---- Ждём поле для кода ----
            System.out.println("Жду поле для кода (до 10 минут)...");
            page.waitForSelector("input.phone-sms-modal-code__input",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000)
                            .setState(WaitForSelectorState.VISIBLE)
            );
            System.out.println("Поле для кода появилось ✅");

            // --- Ищем сессию Google Messages ---
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
                throw new RuntimeException("❌ Файл сессии Google Messages не найден!");

            System.out.println("📁 Используем файл сессии: " + sessionPath.toAbsolutePath());

            // --- Открываем Google Messages ---
            BrowserContext messagesContext = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(sessionPath)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            System.out.println("⌛ Ждём список чатов...");
            boolean chatsLoaded = false;
            for (int i = 0; i < 20; i++) {
                if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                    chatsLoaded = true;
                    break;
                }
                messagesPage.waitForTimeout(1000);
            }
            if (!chatsLoaded) throw new RuntimeException("❌ Чаты не загрузились в Google Messages");
            System.out.println("✅ Список чатов найден");

            System.out.println("🔍 Открываем чат 1xBet...");
            Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
            if (chat.count() == 0) chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.first().click();
            messagesPage.waitForTimeout(1500);

            System.out.println("📩 Ищем последнее сообщение...");
            Locator messageNodes = messagesPage.locator("div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
            int count = messageNodes.count();
            if (count == 0) throw new RuntimeException("❌ Сообщения не найдены");
            String lastMessageText = messageNodes.nth(count - 1).innerText().trim();
            System.out.println("📨 Последнее сообщение: " + lastMessageText);

            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(lastMessageText);
            String code = matcher.find() ? matcher.group() : null;
            if (code == null)
                throw new RuntimeException("❌ Код подтверждения не найден в сообщении!");
            System.out.println("✅ Извлечённый код: " + code);

            tg.sendMessage("✉️ Код из Google Messages получен: `" + code + "`");

            // Возврат на 1xbet и закрытие контекста сообщений
            page.bringToFront();
            messagesContext.close();

            System.out.println("Вводим код подтверждения");
            page.fill("input.phone-sms-modal-code__input", code);
            page.click("button:has-text('Подтвердить')");
            System.out.println("Авторизация завершена ✅");

            // --- Личный кабинет ---
            System.out.println("Открываем 'Личный кабинет'");
            page.waitForTimeout(1000);
            page.click("a.header-lk-box-link[title='Личный кабинет']");

            System.out.println("Закрываем popup (если есть)");
            try {
                Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
                if (closeCrossLk.isVisible()) {
                    closeCrossLk.click();
                    System.out.println("Крестик закрыт ✅");
                }
            } catch (Exception ignored) {}

            // --- Выход ---
            System.out.println("Жмём 'Выход'");
            page.click("a.ap-left-nav__item_exit");
            page.waitForTimeout(800);
            page.click("button.swal2-confirm.swal2-styled");
            System.out.println("Выход завершён ✅");

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage(
                    "✅ *Тест завершён:* v2_number_authorization\n" +
                            "• Авторизация — по номеру\n" +
                            "• Код из Google Messages — получен\n" +
                            "• ЛК — проверен\n" +
                            "• Выход — выполнен\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*\n" +
                            "🌐 [1xbet.kz](https://1xbet.kz)"
            );

        } catch (Exception e) {
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_number_authorization");
            tg.sendMessage("🚨 Ошибка в *v2_number_authorization*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}

package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_email_authorization {
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
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
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
    void loginWithEmailAndSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Старт*: v2_email_authorization (десктоп, Email + SMS, Google Messages)");

        try {
            // --- Навигация на сайт ---
            System.out.println("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/");

            // --- Вход ---
            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(500);
            page.click("button#login-form-call");

            System.out.println("Вводим Email");
            String email = ConfigHelper.get("email");
            page.fill("input#auth_id_email", email);

            System.out.println("Вводим пароль");
            String password = ConfigHelper.get("password");
            page.fill("input#auth-form-password", password);

            System.out.println("Жмём 'Войти' в форме авторизации");
            page.locator("button.auth-button:has-text('Войти')").click();

            // --- Ожидание капчи через появление кнопки 'Выслать код' ---
            System.out.println("Жду решение капчи. Ожидаю кнопку 'Выслать код' (до 10 минут)...");
            try {
                page.waitForSelector("button.phone-sms-modal-content__send",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Кнопка 'Выслать код' появилась ✅");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Кнопка 'Выслать код' не появилась — капча не решена или изменился UI");
            }

            // --- Нажимаем 'Выслать код' ---
            System.out.println("Жмём 'Выслать код'");
            Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
            try {
                sendCodeButton.click();
                System.out.println("Кнопка 'Выслать код' нажата ✅");
            } catch (Exception e) {
                System.out.println("Обычный клик не сработал, пробуем через JS...");
                page.evaluate("document.querySelector(\"button.phone-sms-modal-content__send, button:has-text('Выслать код')\")?.click()");
            }

            // --- Ждём поле для кода ---
            System.out.println("Жду поле для ввода кода (до 10 минут)...");
            try {
                page.waitForSelector("input.phone-sms-modal-code__input",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Поле для кода появилось ✅");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Поле для ввода кода не появилось — капча не решена или изменился UI");
            }

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
            if (sessionPath == null) {
                throw new RuntimeException("❌ Файл сессии Google Messages не найден ни в одном из стандартных путей");
            }
            System.out.println("📁 Используем файл сессии: " + sessionPath.toAbsolutePath());

            // --- Google Messages: открываем во втором контексте ---
            System.out.println("🔐 Открываем Google Messages с сохранённой сессией...");
            BrowserContext messagesContext = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(sessionPath)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            // Ждём список чатов
            System.out.println("⌛ Ждём появления списка чатов...");
            boolean chatsLoaded = false;
            for (int i = 0; i < 20; i++) {
                if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                    chatsLoaded = true;
                    break;
                }
                messagesPage.waitForTimeout(1000);
            }
            if (!chatsLoaded) {
                throw new RuntimeException("❌ Чаты не загрузились в Google Messages");
            }
            System.out.println("✅ Список чатов найден");

            // Открываем чат 1xbet / 1xbet-kz, иначе первый
            System.out.println("🔍 Ищем чат с 1xBet...");
            Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
            if (chat.count() == 0) chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.first().click();
            System.out.println("💬 Чат открыт");
            messagesPage.waitForTimeout(1500);

            // Берём последнее текстовое сообщение и достаём код
            System.out.println("📩 Ищем последнее сообщение...");
            Locator messageNodes = messagesPage.locator(
                    "mws-message-part-content div.text-msg-content div.text-msg.msg-content div.ng-star-inserted"
            );
            int count = 0;
            for (int i = 0; i < 15; i++) {
                count = messageNodes.count();
                if (count > 0) break;
                messagesPage.waitForTimeout(1000);
            }
            if (count == 0) throw new RuntimeException("❌ Сообщения внутри чата не найдены");

            String lastMessageText = messageNodes.nth(count - 1).innerText().trim();
            System.out.println("📨 Последнее сообщение: " + lastMessageText);

            // Код: 4–8 алфавитно-цифровых символов
            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(lastMessageText);
            String code = matcher.find() ? matcher.group() : null;
            if (code == null) throw new RuntimeException("❌ Код подтверждения не найден в тексте сообщения");
            System.out.println("✅ Извлечённый код: " + code);
            tg.sendMessage("✉️ Код из Google Messages получен: `"+code+"`");

            // Возвращаем фокус на 1xbet и закрываем контекст сообщений
            System.out.println("Возврат на 1xbet.kz и закрытие контекста Messages");
            page.bringToFront();
            try {
                messagesContext.close();
            } catch (Exception ignored) { }

            // --- Ввод кода и подтверждение ---
            System.out.println("Вводим код подтверждения");
            page.fill("input.phone-sms-modal-code__input", code);

            System.out.println("Жмём 'Подтвердить'");
            try {
                page.click("button:has-text('Подтвердить')");
            } catch (Exception e) {
                page.evaluate("document.querySelector(\"button.phone-sms-modal-content__send, button:has-text('Подтвердить')\")?.click()");
            }

            // Можно дождаться исчезновения модалки как подтверждение
            try {
                page.waitForSelector("div.phone-sms-modal-content", new Page.WaitForSelectorOptions()
                        .setTimeout(10_000)
                        .setState(WaitForSelectorState.DETACHED));
            } catch (Exception ignored) { }

            System.out.println("Авторизация завершена ✅");
            tg.sendMessage("🟢 Авторизация завершена успешно");

            // --- Личный кабинет ---
            System.out.println("Открываем 'Личный кабинет'");
            page.waitForTimeout(800);
            page.click("a.header-lk-box-link[title='Личный кабинет']");

            // Закрываем возможный попап-крестик
            System.out.println("Пробуем закрыть popup-крестик в ЛК (если есть)");
            try {
                Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
                closeCrossLk.waitFor(new Locator.WaitForOptions().setTimeout(2000).setState(WaitForSelectorState.ATTACHED));
                if (closeCrossLk.isVisible()) {
                    closeCrossLk.click();
                    System.out.println("Крестик в ЛК найден и нажат ✅");
                } else {
                    System.out.println("Крестика в ЛК нет");
                }
            } catch (Exception ignored) { }

            // --- Выход ---
            System.out.println("Жмём 'Выход'");
            page.waitForTimeout(600);
            page.click("a.ap-left-nav__item_exit");

            System.out.println("Подтверждаем выход 'ОК'");
            page.waitForTimeout(600);
            page.click("button.swal2-confirm.swal2-styled");

            System.out.println("Выход завершён ✅");
            long duration = (System.currentTimeMillis() - startTime) / 1000;

            tg.sendMessage(
                    "✅ *Тест завершён:* v2_email_authorization\n" +
                            "• Авторизация — выполнена\n" +
                            "• Код — получен из Google Messages\n" +
                            "• ЛК — проверен\n" +
                            "• Выход — выполнен\n\n" +
                            "🕒 Время: *" + duration + " сек.*\n" +
                            "🌐 [1xbet.kz](https://1xbet.kz)\n" +
                            "_Браузер оставлен открытым_"
            );

        } catch (Exception e) {
            System.out.println("❌ Ошибка в тесте: " + e.getMessage());
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_email_authorization");
            tg.sendMessage("🚨 Ошибка в *v2_email_authorization*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}

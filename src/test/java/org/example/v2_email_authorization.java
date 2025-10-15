package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;

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
                new BrowserType.LaunchOptions().setHeadless(false)
        );
        context = browser.newContext();
        page = context.newPage();

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

            System.out.println("Вводим Email");
            String email = ConfigHelper.get("email");
            page.fill("input#auth_id_email", email);

            System.out.println("Вводим пароль");
            page.waitForTimeout(1000);
            String password = ConfigHelper.get("password");
            page.fill("input#auth-form-password", password);

            System.out.println("Жмём 'Войти' в форме авторизации");
            page.waitForTimeout(1000);
            page.locator("button.auth-button:has-text('Войти')").click();

            System.out.println("Теперь решай капчу вручную — я жду появление кнопки 'Выслать код' (до 10 минут)...");
            try {
                page.waitForSelector("button.phone-sms-modal-content__send",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Кнопка 'Выслать код' появилась ✅");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Кнопка 'Выслать код' не появилась — капча не решена или что-то пошло не так!");
            }

            System.out.println("Ждём модальное окно SMS");
            page.waitForSelector("button:has-text('Выслать код')");

            System.out.println("Жмём 'Выслать код'");
            Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
            try {
                sendCodeButton.click();
                System.out.println("Кнопка 'Выслать код' нажата ✅");
            } catch (Exception e) {
                System.out.println("Первая попытка клика не удалась, пробуем через JS...");
                page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
            }

            System.out.println("Теперь решай капчу вручную — я жду поле для кода (до 10 минут)...");
            try {
                page.waitForSelector("input.phone-sms-modal-code__input",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Поле для кода появилось! Достаём код из Google Messages...");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Поле для ввода кода не появилось — капча не решена или что-то пошло не так!");
            }

            // --- УНИВЕРСАЛЬНЫЙ ПОИСК СЕССИИ GOOGLE MESSAGES ---
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
                throw new RuntimeException("❌ Файл сессии Google Messages не найден ни в одном из стандартных путей!");
            }

            System.out.println("📁 Используем файл сессии: " + sessionPath.toAbsolutePath());

            // --- Открываем Google Messages с сохранённой авторизацией ---
            System.out.println("🔐 Открываем Google Messages с сохранённой сессией...");
            BrowserContext messagesContext = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(sessionPath)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            // 1. Кликаем по самому верхнему (новому) чату в списке:
            Locator chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.click();
            messagesPage.waitForTimeout(1000); // даём загрузиться

            chat = messagesPage.locator("mws-conversation-list-item").first();
            int chatsCount = messagesPage.locator("mws-conversation-list-item").count();
            System.out.println("Чатов найдено: " + chatsCount);
            chat.click();
            messagesPage.waitForTimeout(1000);

            // Новый универсальный селектор — вытаскиваем все коды из всех сообщений!
            Locator messageNodes = messagesPage.locator(
                    "mws-message-part-content div.text-msg-content div.text-msg.msg-content div.ng-star-inserted"
            );
            int count = messageNodes.count();
            System.out.println("Сообщений найдено: " + count);
            for (int i = 0; i < count; i++) {
                System.out.println("[" + i + "] " + messageNodes.nth(i).innerText());
            }

            if (count == 0)
                throw new RuntimeException("Сообщения не найдены! (Проверь, что чат не пуст и аккаунт авторизован)");

            String smsText = messageNodes.nth(count - 1).innerText();
            System.out.println("Содержимое последнего SMS: " + smsText);

            String code = smsText.split("\\s+")[0].trim();
            System.out.println("Извлечённый код подтверждения: " + code);

            System.out.println("Возвращаемся на сайт 1xbet.kz");
            page.bringToFront();

            System.out.println("Вводим код подтверждения");
            page.fill("input.phone-sms-modal-code__input", code);

            System.out.println("Жмём 'Подтвердить'");
            page.click("button:has-text('Подтвердить')");

            System.out.println("Авторизация завершена ✅");

            System.out.println("Открываем 'Личный кабинет'");
            page.waitForTimeout(1000);
            page.click("a.header-lk-box-link[title='Личный кабинет']");

            System.out.println("Пробуем закрыть popup-крестик после входа в ЛК (если он вообще есть)");
            try {
                Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
                closeCrossLk.waitFor(new Locator.WaitForOptions().setTimeout(2000).setState(WaitForSelectorState.ATTACHED));
                if (closeCrossLk.isVisible()) {
                    closeCrossLk.click();
                    System.out.println("Крестик в ЛК найден и нажат ✅");
                } else {
                    System.out.println("Крестика в ЛК нет — идём дальше");
                }
            } catch (Exception e) {
                System.out.println("Всплывашки в ЛК или крестика нет, игнорируем и двигаемся дальше");
            }

            System.out.println("Жмём 'Выход'");
            page.waitForTimeout(1000);
            page.click("a.ap-left-nav__item_exit");

            System.out.println("Подтверждаем выход кнопкой 'ОК'");
            page.waitForTimeout(1000);
            page.click("button.swal2-confirm.swal2-styled");

            System.out.println("Выход завершён ✅ (браузер остаётся открытым)");

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage(
                    "✅ *Тест успешно завершён:* v2_email_authorization\n" +
                            "• Авторизация — выполнена\n" +
                            "• Код из Google Messages — получен\n" +
                            "• Личный кабинет — открыт и проверен\n" +
                            "• Выход — произведён\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*\n" +
                            "🌐 Сайт: [1xbet.kz](https://1xbet.kz)\n" +
                            "_Браузер остаётся открытым для ручной проверки._"
            );

        } catch (Exception e) {
            System.out.println("❌ Ошибка в тесте: " + e.getMessage());
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_email_authorization");
            tg.sendMessage("🚨 Ошибка в тесте *v2_email_authorization*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}

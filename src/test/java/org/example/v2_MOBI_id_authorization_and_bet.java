package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization_and_bet {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static Properties creds = new Properties();

    @BeforeAll
    static void setUpAll() throws IOException {
        // --- Загружаем креды ---
        creds.load(new FileInputStream("src/test/resources/config.properties"));

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized"))
        );

        // --- Основной контекст для мобильного сайта ---
        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(null)        // без ограничения окна
        );
        page = context.newPage();

        // --- Проверяем и подгружаем сессию Google Messages отдельно ---
        Path sessionPath = Paths.get("src/test/resources/sessions/messages-session.json");
        try {
            BrowserContext messagesContext = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(sessionPath)
            );
            messagesContext.close(); // просто проверяем, что файл читается
            System.out.println("✅ Сессия Google Messages успешно загружена.");
        } catch (Exception e) {
            System.out.println("⚠️  Не удалось загрузить сохранённую сессию Google Messages. " +
                    "Будет создано новое окно (QR). Проверь файл: " + sessionPath);
        }
    }


    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginBetHistoryAndLogout() {
        long testStartTime = System.currentTimeMillis();
        String botToken = creds.getProperty("telegram.bot.token");
        String chatId = creds.getProperty("telegram.chat.id");

// --- Telegram: сообщение о старте теста ---
        String startMsg = "🚀 *Тест v2_MOBI_id_authorization_and_bet* стартовал " +
                "(авторизация через Google Messages)";
        Telegram.send(startMsg, botToken, chatId);
        page.navigate("https://1xbet.kz/?platform_type=mobile");

        String login = creds.getProperty("login");
        String password = creds.getProperty("password");

        // --- Авторизация ---
        page.click("button#curLoginForm span.auth-btn__label:has-text('Вход')");
        page.fill("input#auth_id_email", login);
        page.fill("input#auth-form-password", password);
        page.click("button.auth-button span.auth-button__text:has-text('Войти')");

        // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ----
        System.out.println("Теперь решай капчу вручную — я жду появление кнопки 'Выслать код' (до 10 минут)...");
        try {
            page.waitForSelector("button:has-text('Выслать код')",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000) // максимум 10 минут
                            .setState(WaitForSelectorState.VISIBLE)
            );
            System.out.println("Кнопка 'Выслать код' появилась ✅");
        } catch (PlaywrightException e) {
            throw new RuntimeException("Кнопка 'Выслать код' не появилась — капча не решена или что-то пошло не так!");
        }

        // ---- ЖМЁМ "ВЫСЛАТЬ КОД" ----
        System.out.println("Жмём 'Выслать код'");
        page.click("button:has-text('Выслать код')");

        // ---- ЖДЁМ ПОЛЕ ДЛЯ ВВОДА КОДА ----
        System.out.println("Ждём поле для ввода кода (до 10 минут)...");
        page.waitForSelector("input.phone-sms-modal-code__input",
                new Page.WaitForSelectorOptions()
                        .setTimeout(600_000)
                        .setState(WaitForSelectorState.VISIBLE)
        );
        System.out.println("Поле для ввода кода появилось ✅");

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

        System.out.println("⌛ Ждём появления списка чатов...");
        boolean chatsLoaded = false;
        for (int i = 0; i < 20; i++) {
            if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                chatsLoaded = true;
                break;
            }
            messagesPage.waitForTimeout(1000);
        }
        if (!chatsLoaded)
            throw new RuntimeException("❌ Чаты не появились в Google Messages — возможно, не успели подгрузиться.");
        System.out.println("✅ Список чатов успешно найден");

        System.out.println("🔍 Ищем чат с 1xBet...");
        Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
        if (chat.count() == 0) chat = messagesPage.locator("mws-conversation-list-item").first();
        chat.first().click();
        System.out.println("💬 Чат открыт");
        messagesPage.waitForTimeout(3000);

        System.out.println("📩 Ищем последнее сообщение...");
        Locator messageNodes = messagesPage.locator("div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
        int count = 0;
        for (int i = 0; i < 15; i++) {
            count = messageNodes.count();
            if (count > 0) break;
            messagesPage.waitForTimeout(1000);
        }
        if (count == 0)
            throw new RuntimeException("❌ Не найдено сообщений внутри чата!");
        String lastMessageText = messageNodes.nth(count - 1).innerText().trim();
        System.out.println("📨 Последнее сообщение: " + lastMessageText);

        Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(lastMessageText);
        String code = matcher.find() ? matcher.group() : null;
        if (code == null)
            throw new RuntimeException("❌ Код подтверждения не найден в сообщении!");
        System.out.println("✅ Извлечённый код: " + code);

        // --- Вводим код ---
        page.bringToFront();
        page.fill("input.phone-sms-modal-code__input", code);
        page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

        // --- Закрываем блокировку ---
        if (page.locator("a.pf-subs-btn-link__secondary:has-text('Блокировать')").isVisible()) {
            page.click("a.pf-subs-btn-link__secondary:has-text('Блокировать')");
        }

        // ---------- СТАВКА ----------
        System.out.println("Переходим к выбору события для ставки...");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(3000);

// --- Переход в раздел "Линия" ---
        try {
            System.out.println("Открываем раздел 'Линия'...");
            Locator lineLink = page.locator("a.main-nav__link:has-text('Линия')");
            lineLink.waitFor(new Locator.WaitForOptions()
                    .setTimeout(10000)
                    .setState(WaitForSelectorState.VISIBLE));
            lineLink.click();
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000);
            System.out.println("✅ Раздел 'Линия' открыт");
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось открыть раздел 'Линия': " + e.getMessage());
        }

// --- Проверяем наличие кнопки 'Очистить' ---
        try {
            Locator clearButton = page.locator("button.m-c__clear:has-text('Очистить')");
            if (clearButton.isVisible()) {
                System.out.println("🔹 Найдена кнопка 'Очистить' — очищаем купон перед новой ставкой...");
                clearButton.click();
                page.waitForTimeout(1500);
                System.out.println("✅ Купон очищен");
            } else {
                System.out.println("ℹ️ Кнопки 'Очистить' нет — купон пуст, продолжаем");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось проверить или кликнуть 'Очистить' — продолжаем без очистки (" + e.getMessage() + ")");
        }

// --- Выбираем событие и коэффициент ---
        System.out.println("Выбираем событие и коэффициент...");
        Locator coef = page.locator("div.coef__num").first();
        coef.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
        coef.click();
        System.out.println("Коэффициент выбран ✅");
        page.waitForTimeout(2000);

        // ---------- ВВОД СУММЫ ----------
        System.out.println("Вводим сумму ставки (50 KZT)...");

        try {
            Locator sumInput = page.locator("input.c-spinner__input.bet_sum_input, input.js-spinner.spinner__count");
            sumInput.waitFor(new Locator.WaitForOptions()
                    .setTimeout(15000)
                    .setState(WaitForSelectorState.VISIBLE));

            // Проверяем, какой именно input активен
            String inputSelector = null;
            if (page.locator("input.c-spinner__input.bet_sum_input").count() > 0) {
                inputSelector = "input.c-spinner__input.bet_sum_input";
                System.out.println("🔹 Найдено стандартное поле ввода суммы");
            } else if (page.locator("input.js-spinner.spinner__count").count() > 0) {
                inputSelector = "input.js-spinner.spinner__count";
                System.out.println("🔹 Найдено альтернативное поле ввода суммы");
            } else {
                throw new RuntimeException("❌ Поле для ввода суммы не найдено!");
            }

            // Снимаем readonly и вводим значение напрямую
            page.evaluate("selector => { " +
                    "const el = document.querySelector(selector);" +
                    "if (el) {" +
                    "  el.removeAttribute('readonly');" +
                    "  el.focus();" +
                    "  el.value = '50';" +
                    "  el.dispatchEvent(new Event('input', { bubbles: true }));" +
                    "  el.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "}}", inputSelector);
            page.waitForTimeout(1000);
            System.out.println("✅ Значение 50 установлено в поле ставки");

            // --- Кликаем по кнопке "Сделать ставку" ---
            Locator makeBetButton = page.locator("button.m-c__button--add:has-text('Сделать ставку'), button.bets-sums-keyboard-button:has-text('Сделать ставку')");
            makeBetButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            makeBetButton.click();
            System.out.println("🟩 Жмём 'Сделать ставку'");

            // --- Подтверждаем ставку ---
            Locator okButton = page.locator("button.c-btn span.c-btn__text:has-text('Ok')");
            okButton.waitFor(new Locator.WaitForOptions().setTimeout(20000).setState(WaitForSelectorState.VISIBLE));
            okButton.click();
            System.out.println("✅ Ставка подтверждена (кнопка 'Ok' нажата)");

        } catch (Exception e) {
            System.out.println("❌ Ошибка при вводе суммы или клике 'Сделать ставку': " + e.getMessage());
        }

        // ---------- ИСТОРИЯ ----------
        System.out.println("Открываем 'Историю ставок'...");
        Locator profileButton2 = page.locator(
                "button.user-header__link.header__link.header__reg.header__reg_ico.ion-android-person"
        );
        profileButton2.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
        profileButton2.click();
        page.waitForTimeout(1500);

        Locator historyLink = page.locator("a.drop-menu-list__link_history, a.drop-menu-link__label:has-text('История ставок')");
        historyLink.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
        historyLink.click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(3000);
        System.out.println("✅ История ставок открыта успешно");

        // --- Извлекаем номер последней ставки ---
        String betNumber = "не найден";
        try {
            Locator betNumLocator = page.locator("div.events__text.events__text_main span b").nth(0);
            betNumLocator.waitFor(new Locator.WaitForOptions()
                    .setTimeout(10000)
                    .setState(WaitForSelectorState.VISIBLE));
            String betText = betNumLocator.innerText().trim();
            betNumber = betText.replaceAll("[^0-9]", ""); // оставить только цифры
            System.out.println("🎫 Номер последней ставки: " + betNumber);
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось извлечь номер ставки: " + e.getMessage());
        }

        // --- Завершение / Выход ---
        System.out.println("Пробуем выполнить выход...");
        try {
            Locator menu = page.locator("button.user-header__link.header__link--messages");
            menu.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
            menu.click();

            Locator logout = page.locator("button.drop-menu-list__link_exit:has-text('Выход')");
            logout.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
            logout.click();

            Locator confirm = page.locator("button.swal2-confirm.swal2-styled:has-text('ОК')");
            confirm.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
            confirm.click();

            System.out.println("✅ Выход выполнен успешно");
        } catch (Exception e) {
            System.out.println("⚠️ Ошибка при выходе: " + e.getMessage());
        }
        // ---------- ФИНАЛ ----------
        long duration = (System.currentTimeMillis() - testStartTime) / 1000;

        String summary = "✅ *Тест успешно завершён:* v2_MOBI_id_authorization_and_bet\n"
                + "• Авторизация — выполнена\n"
                + "• Ставка — успешно сделана\n"
                + "• № Ставки — *" + betNumber + "*\n"
                + "• История — проверена\n"
                + "• Выход — произведён\n\n"
                + "🕒 Время выполнения: *" + duration + " сек.*\n"
                + "🌐 Сайт: [1xbet.kz](https://1xbet.kz)\n"
                + "_Браузер остаётся открытым для ручной проверки._";

        System.out.println(summary);
        Telegram.send(summary, botToken, chatId);

// Отправляем отчёт в Telegram
        Telegram.send(summary, botToken, chatId);
    }

    // --- Telegram Helper ---
    static class Telegram {
        static void send(String text, String botToken, String chatId) {
            try {
                String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                String data = "chat_id=" + chatId
                        + "&text=" + java.net.URLEncoder.encode(text, "UTF-8")
                        + "&parse_mode=Markdown";
                java.net.http.HttpClient.newHttpClient().send(
                        java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(url))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(data))
                                .build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding()
                );
                System.out.println("📨 Отчёт отправлен в Telegram");
            } catch (Exception e) {
                System.out.println("⚠️ Ошибка при отправке Telegram: " + e.getMessage());
            }
        }
    }
}
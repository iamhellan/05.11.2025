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

public class v2_MOBI_id_authorization_fastgames {
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
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null)
        );
        page = context.newPage();

        // --- Проверяем и подгружаем сессию Google Messages отдельно (как в v2_MOBI_id_authorization_and_bet) ---
        Path sessionPath = resolveMessagesSessionPath();
        if (sessionPath != null) {
            try {
                BrowserContext messagesContext = browser.newContext(
                        new Browser.NewContextOptions().setStorageStatePath(sessionPath)
                );
                messagesContext.close(); // просто проверяем, что файл читается
                System.out.println("✅ Сессия Google Messages успешно загружена: " + sessionPath.toAbsolutePath());
            } catch (Exception e) {
                System.out.println("⚠️  Не удалось загрузить сохранённую сессию Google Messages. Проверь файл: " + sessionPath);
            }
        } else {
            System.out.println("⚠️ Файл сессии Google Messages не найден ни в одном из стандартных путей.");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // ===== УТИЛИТЫ ============================================================

    private static Path resolveMessagesSessionPath() {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path[] possiblePaths = new Path[]{
                projectRoot.resolve("resources/sessions/messages-session.json"),
                projectRoot.resolve("src/test/resources/sessions/messages-session.json"),
                projectRoot.resolve("src/test/java/org/example/resources/sessions/messages-session.json")
        };
        for (Path p : possiblePaths) {
            if (p.toFile().exists()) return p;
        }
        return null;
    }

    private Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Page pg : p.context().pages()) {
                for (Frame f : pg.frames()) {
                    try {
                        if (f.locator(selector).count() > 0) {
                            System.out.println("[DEBUG] Нашли селектор в фрейме: " + f.url());
                            return f;
                        }
                    } catch (Throwable ignore) {}
                }
            }
            p.waitForTimeout(300);
        }
        return null;
    }

    private Locator smartLocator(Page p, String selector, int timeoutMs) {
        Locator direct = p.locator(selector);
        if (direct.count() > 0) return direct;
        Frame f = findFrameWithSelector(p, selector, timeoutMs);
        if (f != null) return f.locator(selector);
        throw new RuntimeException("Элемент не найден: " + selector);
    }

    private void robustClick(Page p, Locator loc, int timeoutMs, String debugName) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        RuntimeException lastErr = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                loc.first().scrollIntoViewIfNeeded();
                loc.first().click(new Locator.ClickOptions().setTimeout(3000));
                return;
            } catch (RuntimeException e1) {
                lastErr = e1;
                try {
                    loc.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                    return;
                } catch (RuntimeException e2) {
                    lastErr = e2;
                    try {
                        loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                        return;
                    } catch (RuntimeException e3) { lastErr = e3; }
                }
            }
            p.waitForTimeout(200);
        }
        if (lastErr != null) throw lastErr;
    }

    private void clickFirstEnabled(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Locator group;
            try {
                group = smartLocator(p, selector, 1500);
            } catch (RuntimeException e) {
                p.waitForTimeout(200);
                continue;
            }
            int count = group.count();
            for (int i = 0; i < count; i++) {
                Locator candidate = group.nth(i);
                boolean visible;
                try { visible = candidate.isVisible(); } catch (Throwable t) { visible = false; }
                if (!visible) continue;
                boolean enabled;
                try { enabled = (Boolean) candidate.evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))"); } catch (Throwable t) { enabled = true; }
                if (enabled) {
                    robustClick(p, candidate, 8000, selector + " [nth=" + i + "]");
                    return;
                }
            }
            p.waitForTimeout(200);
        }
        throw new RuntimeException("Не дождались активного элемента: " + selector);
    }

    private Page clickCardMaybeOpensNewTab(Locator card) {
        int before = context.pages().size();
        robustClick(page, card, 30000, "game-card");
        page.waitForTimeout(600);
        int after = context.pages().size();
        if (after > before) {
            Page newPage = context.pages().get(after - 1);
            newPage.bringToFront();
            return newPage;
        }
        return page;
    }

    private void passTutorialIfPresent(Page gamePage) {
        for (int i = 1; i <= 5; i++) {
            try {
                Locator nextBtn = smartLocator(gamePage, "div[role='button']:has-text('Далее')", 600);
                if (nextBtn.count() == 0 || !nextBtn.first().isVisible()) break;
                robustClick(gamePage, nextBtn.first(), 2000, "Далее");
                gamePage.waitForTimeout(150);
            } catch (RuntimeException ignore) { break; }
        }
        try {
            Locator understood = smartLocator(gamePage, "div[role='button']:has-text('Я всё понял')", 600);
            if (understood.count() > 0 && understood.first().isVisible()) {
                robustClick(gamePage, understood.first(), 2000, "Я всё понял");
            }
        } catch (RuntimeException ignore) {}
    }

    private void setStake50ViaChip(Page gamePage) {
        Locator chip50 = smartLocator(gamePage, "div.chip-text:has-text('50')", 2000);
        robustClick(gamePage, chip50.first(), 12000, "chip-50");
    }

    private void waitRoundToSettle(Page gamePage, int maxMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxMs) {
            Locator anyBet = gamePage.locator("div[role='button'][data-market][data-outcome]:has-text('Сделать ставку')");
            try {
                if (anyBet.count() > 0 && anyBet.first().isVisible()) {
                    boolean enabled = (Boolean) anyBet.first().evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))");
                    if (enabled) return;
                }
            } catch (Throwable ignore) {}
            gamePage.waitForTimeout(150);
        }
    }

    private Page openGameByHrefContains(Page originPage, String hrefContains, String fallbackMenuText) {
        Frame f = findFrameWithSelector(originPage, "a[href*='" + hrefContains + "']", 5000);
        if (f == null && fallbackMenuText != null) {
            f = findFrameWithSelector(originPage, "span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')", 5000);
        }
        if (f == null) throw new RuntimeException("Не нашли игру: " + hrefContains);
        Locator link = f.locator("a[href*='" + hrefContains + "']");
        link.first().scrollIntoViewIfNeeded();
        return clickCardMaybeOpensNewTab(link.first());
    }

    private Page openUniqueBoxingFromHub(Page originPage) {
        String innerSpan = "a.menu-sports-item-inner[href*='productId=boxing'] span.text-hub-header-game-title:has-text('Бокс')";
        Frame f = findFrameWithSelector(originPage, innerSpan, 8000);
        if (f == null) throw new RuntimeException("Не нашли уникальную кнопку 'Бокс'");
        Locator link = f.locator(innerSpan).first().locator("xpath=ancestor::a");
        return clickCardMaybeOpensNewTab(link.first());
    }

    // ===== ТЕСТ ===============================================================

    @Test
    void loginAndPlayFastGames() {
        long testStartTime = System.currentTimeMillis();
        String botToken = creds.getProperty("telegram.bot.token");
        String chatId = creds.getProperty("telegram.chat.id");

        // --- Telegram: сообщение о старте теста (как в v2_MOBI_id_authorization_and_bet) ---
        String startMsg = "🚀 *Тест v2_MOBI_id_authorization_fastgames* стартовал " +
                "(авторизация через Google Messages)";
        Telegram.send(startMsg, botToken, chatId);

        // === Авторизация ===
        page.navigate("https://1xbet.kz/?platform_type=mobile");
        page.click("button#curLoginForm >> text=Войти");

        String login = creds.getProperty("login");
        String password = creds.getProperty("password");
        page.fill("input#auth_id_email", login);
        page.fill("input#auth-form-password", password);
        page.click("button.auth-button:has(span.auth-button__text:has-text('Войти'))");

        // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ----
        System.out.println("Теперь решай капчу вручную — я жду появление кнопки 'Выслать код' (до 10 минут)...");
        try {
            page.waitForSelector("button:has-text('Выслать код')",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000)
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

        // --- Google Messages: универсальный поиск сессии + открытие как в v2_MOBI_id_authorization_and_bet ---
        Path sessionPath = resolveMessagesSessionPath();
        if (sessionPath == null) {
            throw new RuntimeException("❌ Файл сессии Google Messages не найден ни в одном из стандартных путей!");
        }
        System.out.println("📁 Используем файл сессии: " + sessionPath.toAbsolutePath());

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

        // --- Возврат на 1xbet и ввод кода ---
        page.bringToFront();
        page.fill("input.phone-sms-modal-code__input", code);
        page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

        // --- Закрываем блокировку, если есть ---
        if (page.locator("a.pf-subs-btn-link__secondary:has-text('Блокировать')").isVisible()) {
            page.click("a.pf-subs-btn-link__secondary:has-text('Блокировать')");
        }

        // === Быстрые игры ===
        page.click("button.header__hamburger.hamburger");
        page.click("a.drop-menu-list__link[href*='fast-games']");

        // Crash Boxing
        Locator crashTile = page.locator("div.tile__cell img[alt='Crash boxing']").first();
        Page gamePage = clickCardMaybeOpensNewTab(crashTile);
        passTutorialIfPresent(gamePage);
        clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes']", 300000);
        clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes_2']", 300000);
        waitRoundToSettle(gamePage, 300000);

        // Нарды
        Page nardsPage = openGameByHrefContains(gamePage, "nard", "Нарды");
        passTutorialIfPresent(nardsPage);
        setStake50ViaChip(nardsPage);
        clickFirstEnabled(nardsPage, "span[role='button'][data-market='dice'][data-outcome='blue']", 300000);
        waitRoundToSettle(nardsPage, 300000);

        // Дартс
        Page dartsPage = openGameByHrefContains(nardsPage, "darts?cid", "Дартс");
        passTutorialIfPresent(dartsPage);
        setStake50ViaChip(dartsPage);
        clickFirstEnabled(dartsPage, "span[role='button'][data-market='1-4-5-6-9-11-15-16-17-19']", 300000);
        waitRoundToSettle(dartsPage, 300000);

        // Дартс - Фортуна
        Page dartsFortunePage = openGameByHrefContains(dartsPage, "darts-fortune", "Дартс - Фортуна");
        passTutorialIfPresent(dartsFortunePage);
        setStake50ViaChip(dartsFortunePage);
        clickFirstEnabled(dartsFortunePage, "div[data-outcome='ONE_TO_EIGHT']", 300000);
        waitRoundToSettle(dartsFortunePage, 300000);

        // Больше/Меньше
        Page hiloPage = openGameByHrefContains(dartsFortunePage, "darts-hilo", "Больше/Меньше");
        passTutorialIfPresent(hiloPage);
        setStake50ViaChip(hiloPage);
        clickFirstEnabled(hiloPage, "div[role='button'][data-market][data-outcome]:has-text('Больше')", 300000);
        waitRoundToSettle(hiloPage, 300000);

        // Буллиты NHL21
        Page shootoutPage = openGameByHrefContains(hiloPage, "shootout", "Буллиты NHL21");
        passTutorialIfPresent(shootoutPage);
        setStake50ViaChip(shootoutPage);
        clickFirstEnabled(shootoutPage, "div[role='button'].market-button:has-text('Да')", 300000);
        waitRoundToSettle(shootoutPage, 300000);

        // Бокс
        Page boxingPage = openUniqueBoxingFromHub(shootoutPage);
        passTutorialIfPresent(boxingPage);
        setStake50ViaChip(boxingPage);
        clickFirstEnabled(boxingPage, "div[role='button'].contest-panel-outcome-button", 300000);
        waitRoundToSettle(boxingPage, 300000);

        System.out.println("Готово ✅");

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

        // ---------- ФИНАЛ: ИТОГИ В TELEGRAM КАК В v2_MOBI_id_authorization_and_bet ----------
        long duration = (System.currentTimeMillis() - testStartTime) / 1000;

        String summary = "✅ *Тест успешно завершён:* v2_MOBI_id_authorization_fastgames\n"
                + "• Авторизация — выполнена\n"
                + "• Быстрые игры — успешно пройдены\n"
                + "• Выход — произведён\n\n"
                + "🕒 Время выполнения: *" + duration + " сек.*\n"
                + "🌐 Сайт: [1xbet.kz](https://1xbet.kz)\n"
                + "_Браузер остаётся открытым для ручной проверки._";

        System.out.println(summary);
        Telegram.send(summary, botToken, chatId);

        // Отправляем отчёт в Telegram ещё раз, как в примере
        Telegram.send(summary, botToken, chatId);
    }

    // --- Telegram Helper (как в v2_MOBI_id_authorization_and_bet) ---
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
                System.out.println("📨 Сообщение отправлено в Telegram");
            } catch (Exception e) {
                System.out.println("⚠️ Ошибка при отправке в Telegram: " + e.getMessage());
            }
        }
    }
}

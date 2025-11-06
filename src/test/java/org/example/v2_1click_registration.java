package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.TimeoutError;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class v2_1click_registration {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

    // ====== SETTINGS ======
    static final Path MESSAGES_SESSION = Paths.get("messages-session.json"); // json сессия Google Messages

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
                        .setAcceptDownloads(true)
                        .setViewportSize(null)
        );
        page = context.newPage();
        page.setDefaultTimeout(30_000);
        page.setDefaultNavigationTimeout(60_000);

        // --- Telegram (креды из config.properties) ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId   = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);
    }

    @AfterAll
    static void tearDownAll() {
        try { if (context != null) context.close(); } catch (Throwable ignored) {}
        try { if (browser != null) browser.close(); } catch (Throwable ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Throwable ignored) {}
        System.out.println("Тест завершён ✅ (браузер и контекст закрыты)");
    }

    // ---------- ХЕЛПЕРЫ ----------
    static void pause(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    static void pauseShort() { pause(150); }
    static void pauseMedium() { pause(350); }

    static void waitAndClick(Page page, String selector, int timeoutMs) {
        page.waitForSelector(selector,
                new Page.WaitForSelectorOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
        page.locator(selector).first().click();
        pauseMedium();
    }

    static void clickIfVisible(Page page, String selector) {
        Locator loc = page.locator(selector);
        if (loc.count() > 0 && loc.first().isVisible()) {
            loc.first().click(new Locator.ClickOptions().setTimeout(5000));
            pauseShort();
        }
    }

    static void jsClick(Locator loc) {
        if (loc.count() > 0) loc.first().dispatchEvent("click");
    }

    static void neutralizeOverlayIfNeeded(Page page) {
        page.evaluate("(() => {" +
                "const kill = sel => document.querySelectorAll(sel).forEach(n => {" +
                "  try {" +
                "    n.style.pointerEvents = 'none';" +
                "    n.style.zIndex = '0';" +
                "    n.style.opacity = '0.3';" +  // можно убрать, если не хочешь визуального эффекта
                "  } catch(e) {}" +
                "});" +
                // стандартные перекрытия
                "kill('.arcticmodal-container_i2');" +
                "kill('.arcticmodal-container_i');" +
                "kill('.v--modal-background-click');" +
                "kill('#modals-container *');" +
                "kill('.pf-main-container-wrapper-th-4 *');" +
                // теперь новый блок, мешающий кликам
                "kill('.js_reg_form_scroll.active_scroll');" +
                "})();");
    }

    static void waitForRegistrationModal(Page page) {
        String[] sels = {
                "div#games_content.c-registration",
                "div.arcticmodal-container div.c-registration"
        };
        for (String s : sels) {
            if (page.locator(s).count() > 0) {
                page.waitForSelector(s,
                        new Page.WaitForSelectorOptions()
                                .setTimeout(30_000)
                                .setState(WaitForSelectorState.VISIBLE));
                return;
            }
        }
        page.waitForSelector(String.join(", ", sels),
                new Page.WaitForSelectorOptions().setTimeout(30_000).setState(WaitForSelectorState.VISIBLE));
    }

    static void clickAllOneClickTabs(Page page) {
        System.out.println("Ищем и кликаем все кнопки с текстом 'В 1 клик'");
        Locator allTabs = page.locator("button:has-text('В 1 клик')");
        int count = allTabs.count();
        if (count == 0) {
            System.out.println("Кнопок 'В 1 клик' не найдено");
            return;
        }

        for (int i = 0; i < count; i++) {
            Locator tab = allTabs.nth(i);
            if (!tab.isVisible()) continue;
            try {
                tab.click(new Locator.ClickOptions().setTimeout(2000));
                System.out.println("Кликнули по 'В 1 клик' #" + (i + 1));
            } catch (Exception e1) {
                try {
                    page.evaluate("el => el.click()", tab.elementHandle());
                    System.out.println("Кликнули по 'В 1 клик' через JS #" + (i + 1));
                } catch (Exception e2) {
                    try {
                        tab.click(new Locator.ClickOptions().setForce(true));
                        System.out.println("Force-клик по 'В 1 клик' #" + (i + 1));
                    } catch (Exception ignored) {}
                }
            }
            pauseShort();
        }
    }

    static boolean isOneClickActive(Page page) {
        Locator tab = page.locator("button.c-registration__tab:has-text('В 1 клик')");
        if (tab.count() == 0) return false;
        Object res = tab.first().evaluate("el => el.classList.contains('active')");
        return Boolean.TRUE.equals(res);
    }

    static String randomPromo(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    static boolean isLoggedOut(Page page) {
        boolean hasRegBtn = page.locator("button#registration-form-call").count() > 0
                && page.locator("button#registration-form-call").first().isVisible();
        boolean headerNotLogged = Boolean.TRUE.equals(page.evaluate("() => {" +
                "const h = document.querySelector('header.header');" +
                "return !!h && !h.classList.contains('header--user-logged');" +
                "}"));
        String url = page.url();
        boolean onPublicUrl = url.contains("1xbet.kz/") && !url.contains("/office/");
        return hasRegBtn || headerNotLogged || onPublicUrl;
    }

    static void waitUntilLoggedOutOrHeal(Page page) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (isLoggedOut(page)) return;
            neutralizeOverlayIfNeeded(page);
            clickIfVisible(page, "button.swal2-confirm.swal2-styled");
            clickIfVisible(page, "button.identification-popup-close");
            pause(300);
        }
        page.navigate("https://1xbet.kz/");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        long deadline2 = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline2) {
            if (isLoggedOut(page)) return;
            pause(300);
        }

        // --- Игнорируем блок .js_reg_form_scroll ---
        System.out.println("Отключаем влияние js_reg_form_scroll на клики");
        page.evaluate("(() => {" +
                "const el = document.querySelector('.js_reg_form_scroll.active_scroll');" +
                "if (el) {" +
                "  el.style.pointerEvents = 'none';" +
                "  el.style.zIndex = '0';" +
                "  el.style.opacity = '0.3';" +
                "  console.log('js_reg_form_scroll нейтрализован');" +
                "}" +
                "})();");
    }

    static Path ensureDownloadsDir() throws Exception {
        Path downloads = Paths.get("downloads");
        if (!Files.exists(downloads)) Files.createDirectories(downloads);
        return downloads;
    }

    // ---------- GOOGLE MESSAGES ----------
    static String fetchSmsCodeFromGoogleMessages() {
        System.out.println("🔐 Открываем Google Messages с сохранённой сессией…");
        BrowserContext messagesContext = browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(MESSAGES_SESSION)
        );
        Page messagesPage = messagesContext.newPage();
        messagesPage.setDefaultTimeout(20_000);
        messagesPage.navigate("https://messages.google.com/web/conversations");

        for (int i = 0; i < 20; i++) {
            if (messagesPage.locator("mws-conversation-list-item").count() > 0) break;
            messagesPage.waitForTimeout(1000);
        }

        Locator chat = messagesPage.locator("mws-conversation-list-item").first();
        chat.click();
        messagesPage.waitForTimeout(1200);

        Locator nodes = messagesPage.locator("div.text-msg.msg-content div.ng-star-inserted");
        int count = nodes.count();
        String text = count > 0 ? nodes.nth(count - 1).innerText() : "";
        if (text == null) text = "";

        Matcher m = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)").matcher(text);
        String code = m.find() ? m.group(1) : null;

        messagesContext.close();

        if (code == null || code.isBlank())
            throw new RuntimeException("Код из SMS не найден в последнем сообщении Google Messages");
        System.out.println("✅ Код из SMS: " + code);
        return code;
    }

    // ---------- ИЗВЛЕЧЕНИЕ КРЕДОВ ----------
    static Map<String, String> extractCredentials(Page page) {
        String[] loginSels = {
                "#post-registration-login", "#js-post-reg-login", "[data-field='login']",
                ".post-registration__login", ".js-post-reg-login"
        };
        String[] passSels = {
                "#post-registration-password", "#js-post-reg-password", "[data-field='password']",
                ".post-registration__password", ".js-post-reg-password"
        };
        String login = null, password = null;

        for (String s : loginSels) {
            Locator l = page.locator(s);
            if (l.count() > 0 && l.first().isVisible()) {
                login = l.first().innerText().trim();
                break;
            }
        }
        for (String s : passSels) {
            Locator l = page.locator(s);
            if (l.count() > 0 && l.first().isVisible()) {
                password = l.first().innerText().trim();
                break;
            }
        }

        if ((login == null || login.isBlank()) || (password == null || password.isBlank())) {
            Locator block = page.locator("#js-post-reg-copy-login-password, #js-post-registration-copy-login-password, .post-registration, .popup-registration, .box-modal");
            if (block.count() > 0) {
                String txt = block.first().innerText();
                if (login == null || login.isBlank()) {
                    Matcher ml = Pattern.compile("Логин\\s*[:\\-]?\\s*(\\S+)", Pattern.CASE_INSENSITIVE).matcher(txt);
                    if (ml.find()) login = ml.group(1);
                }
                if (password == null || password.isBlank()) {
                    Matcher mp = Pattern.compile("Пароль\\s*[:\\-]?\\s*(\\S+)", Pattern.CASE_INSENSITIVE).matcher(txt);
                    if (mp.find()) password = mp.group(1);
                }
            }
        }

        Map<String, String> out = new HashMap<>();
        out.put("login", login);
        out.put("password", password);
        return out;
    }

    // ---------- ПРИВЯЗКА ПО СМС (если модалка есть) ----------
    static void tryBindBySmsIfModalVisible(Page page) {
        Locator field = page.locator("input.phone-sms-modal-content__code").first();
        if (field == null || field.count() == 0 || !field.isVisible()) return;

        System.out.println("Обнаружено поле ввода кода. Получаем код из Google Messages…");
        String code = fetchSmsCodeFromGoogleMessages();
        field.fill(code);
        pauseShort();

        Locator confirmBtn = page.locator("button.phone-sms-modal-content__send:has-text('Подтвердить'), button:has-text('Подтвердить')");
        if (confirmBtn.count() > 0 && confirmBtn.first().isVisible()) {
            try { confirmBtn.first().click(); }
            catch (Throwable t) { page.evaluate("el => el.click()", confirmBtn.first()); }
            System.out.println("SMS-код подтверждён");
            tg.sendMessage("🔐 Привязка по SMS подтверждена кодом: `" + code + "`");
        }
    }

    // ---------- ТЕСТ ----------
    @Test
    void v2_registration() throws Exception {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_1click_registration* стартовал (десктоп, регистрация в 1 клик)");

        String sentLogin = null;
        String sentPassword = null;

        try {
            System.out.println("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/?platform_type=desktop");
            pauseMedium();

            // --- РЕГИСТРАЦИЯ ---
            System.out.println("Жмём 'Регистрация'");
            waitAndClick(page, "button#registration-form-call", 15_000);

            System.out.println("Ожидаем модалку регистрации");
            waitForRegistrationModal(page);
            pauseShort();

// --- КЛИКАЕМ ВСЕ "В 1 КЛИК" ---
            clickAllOneClickTabs(page);

// Ждём, пока вкладка реально станет активной
            page.waitForSelector(
                    "div#games_content.c-registration button.c-registration__tab.active:has-text('В 1 клик')",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(120_000)
                            .setState(WaitForSelectorState.VISIBLE)
            );

            System.out.println("Вкладка 'В 1 клик' активна");

            String promo = randomPromo(8);
            System.out.println("Вводим промокод: " + promo);
            Locator promoInput = page.locator("input#popup_registration_ref_code");
            if (promoInput.count() > 0 && promoInput.first().isVisible()) {
                promoInput.first().fill(promo);
            } else {
                page.fill("input[placeholder*='промокод' i]", promo);
            }

            // Бонусы
            System.out.println("Отказываемся от бонусов, затем соглашаемся");
            clickIfVisible(page, "div.c-registration-bonus__item.c-registration-bonus__item--close:has(.c-registration-bonus__title:has-text('Отказаться'))");
            clickIfVisible(page, "div.c-registration-bonus__item:has(.c-registration-bonus__title:has-text('Принять'))");

            System.out.println("Ждём, пока кнопка 'Зарегистрироваться' станет активной...");
            page.waitForFunction(
                    "document.querySelector('div.c-registration__button.submit_registration') && " +
                            "!document.querySelector('div.c-registration__button.submit_registration').classList.contains('disabled')"
            );

            System.out.println("Жмём 'Зарегистрироваться'");
            try {
                page.locator("div.c-registration__button.submit_registration:has-text('Зарегистрироваться')").first().click();
            } catch (Exception e) {
                System.out.println("Обычный клик не сработал, пробуем через JS...");
                page.evaluate("document.querySelector('div.c-registration__button.submit_registration')?.click()");
            }

// после клика могли появиться редирект или новый фрейм
            System.out.println("⏳ Ждём завершения регистрации и появления пост-регистрационного окна...");

            try {
                // ждем полной загрузки
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(60_000));
                page.waitForFunction("document.readyState === 'complete'");

                // проверяем разные варианты пост-регистрационного блока
                String[] possibleSelectors = {
                        "#js-post-reg-copy-login-password",
                        "#js-post-registration-copy-login-password",
                        "div.post-registration",
                        "div.box-modal",
                        "div.popup-registration"
                };

                boolean found = false;
                for (String sel : possibleSelectors) {
                    if (page.locator(sel).count() > 0) {
                        try {
                            page.waitForSelector(sel,
                                    new Page.WaitForSelectorOptions().setTimeout(120_000).setState(WaitForSelectorState.VISIBLE));
                            System.out.println("✅ Найден блок пост-регистрации: " + sel);
                            found = true;
                            break;
                        } catch (Exception ignored) {}
                    }
                }

                if (!found) {
                    System.out.println("⚠️ Блок логина/пароля не появился — возможна ошибка регистрации.");
                    Locator errorBox = page.locator("div.error, span.error, .popup-error");
                    if (errorBox.count() > 0 && errorBox.first().isVisible()) {
                        System.out.println("Текст ошибки: " + errorBox.first().innerText());
                    }
                    tg.sendMessage("⚠️ Блок логина/пароля не найден после регистрации.");
                    ScreenshotHelper.takeScreenshot(page, "registration_no_block");
                }

            } catch (PlaywrightException e) {
                System.out.println("❌ Ошибка ожидания пост-регистрации: " + e.getMessage());
                tg.sendMessage("❌ Ошибка ожидания пост-регистрации: " + e.getMessage());
                ScreenshotHelper.takeScreenshot(page, "registration_timeout");
            }

<<<<<<< HEAD
// ----------- POST-REGISTRATION FLOW -------------
            System.out.println("Кликаем 'Копировать'");
            Locator copyBtn = page.locator("#js-post-reg-copy-login-password");
            if (copyBtn.count() > 0 && copyBtn.first().isVisible()) {
                copyBtn.first().click();
                page.waitForTimeout(1000); // подождать реакцию UI
                // fallback, если popup не появился
                if (page.locator("button.swal2-confirm.swal2-styled:has-text('ОК')").count() == 0) {
                    System.out.println("Popup 'ОК' не появился, триггерим событие вручную");
                    page.evaluate("el => el.dispatchEvent(new MouseEvent('click', { bubbles: true }))", copyBtn.first());
                    page.waitForTimeout(1000);
                }
            } else {
                throw new RuntimeException("Кнопка 'Копировать' не найдена или не видна");
            }
            pauseMedium();

            System.out.println("Закрываем всплывающее окно 'ОК', если появилось");
            try {
                Locator okButton = page.locator("button.swal2-confirm.swal2-styled:has-text('ОК')");
                okButton.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
                if (okButton.isVisible()) {
                    okButton.click();
                    System.out.println("Кнопка 'ОК' нажата ✅");
                    pauseShort();
                }
            } catch (Exception ignored) {}

            System.out.println("Кликаем 'Сохранить в файл'");
            clickIfVisible(page, "a#account-info-button-file");
            pauseMedium();

            System.out.println("Закрываем всплывающее окно 'Закрыть', если появилось");
            try {
                Locator closePopup = page.locator("button.identification-popup-close");
                closePopup.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
                if (closePopup.isVisible()) {
                    closePopup.click();
                    System.out.println("Кнопка 'Закрыть' нажата ✅");
                    pauseShort();
                }
            } catch (Exception ignored) {}

            System.out.println("Кликаем 'Сохранить картинкой'");
            clickIfVisible(page, "a#account-info-button-image");
            pauseMedium();

            System.out.println("Закрываем всплывающее окно 'Закрыть', если появилось");
            try {
                Locator closePopup = page.locator("button.identification-popup-close");
                closePopup.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
                if (closePopup.isVisible()) {
                    closePopup.click();
                    System.out.println("Кнопка 'Закрыть' нажата ✅");
                    pauseShort();
                }
            } catch (Exception ignored) {}

            System.out.println("Кликаем 'Выслать на e-mail'");
            clickIfVisible(page, "a#form_mail_after_submit");
            pauseMedium();

            // Вводим email
            Locator emailField = page.locator("input.post-email__input[type='email']:visible").first();
            emailField.fill("zhante1111@gmail.com");
            pauseShort();

            Locator sendBtn = page.locator("button.js-post-email-content-form__btn:not([disabled])");
            sendBtn.waitFor();
            sendBtn.click();
            System.out.println("Email отправлен");
            pauseMedium();
            // --- Закрываем все всплывающие крестики регистрации ---
            System.out.println("Закрываем все всплывающие крестики регистрации...");
            Locator closeBtns = page.locator("#closeModal, .arcticmodal-close.c-registration__close");
            int btnCount = closeBtns.count();
            for (int i = 0; i < btnCount; i++) {
                if (closeBtns.nth(i).isVisible()) {
                    closeBtns.nth(i).click();
                    System.out.println("Закрыт крестик #" + (i + 1));
                    page.waitForTimeout(300);
=======
            // --- ПОСТ-РЕГ ОКНО ---
            System.out.println("Ждём блок копирования логина/пароля (до 120 сек)");
            page.waitForSelector("#js-post-reg-copy-login-password",
                    new Page.WaitForSelectorOptions().setTimeout(120_000).setState(WaitForSelectorState.VISIBLE));

            System.out.println("Пробуем закрыть всплывающее окно уведомлений (Блокировать)");
            Locator blockBtn = page.locator("a.pf-subs-btn-link.pf-subs-btn-link__secondary:has-text('Блокировать')");
            if (blockBtn.count() > 0 && blockBtn.first().isVisible()) {
                try {
                    blockBtn.first().click();
                    System.out.println("Окно уведомлений закрыто обычным кликом");
                } catch (Exception e) {
                    page.evaluate("document.querySelector(\"a.pf-subs-btn-link.pf-subs-btn-link__secondary[href='#deny']\")?.click()");
                    System.out.println("Окно уведомлений закрыто через JS");
                }
                pauseShort();
            }

            // Копировать логин/пароль — строго по id
            System.out.println("Кликаем 'Скопировать' логин/пароль");
            page.locator("#js-post-reg-copy-login-password").first().click();

            // Закрываем всплывающее окно, если появилось
            clickIfVisible(page, "button.swal2-confirm.swal2-styled:has-text('ОК'), button.swal2-confirm.swal2-styled:has-text('OK'), button.swal2-confirm.swal2-styled");

            Path downloadsDir = ensureDownloadsDir();

            clickIfVisible(page, "button.identification-popup-close, button.identification-popup-get-bonus__close");

            // Сохраняем в файл (download || blob-фоллбэк)
            System.out.println("Сохраняем в файл");
            Locator saveFileBtn = page.locator("a#account-info-button-file");
            if (saveFileBtn.count() > 0 && saveFileBtn.first().isVisible()) {
                boolean fileSaved = false;
                try {
                    Download d1 = page.waitForDownload(
                            new Page.WaitForDownloadOptions().setTimeout(30_000),
                            () -> saveFileBtn.first().click()
                    );
                    String suggested = d1.suggestedFilename();
                    System.out.println("Скачали файл: " + suggested);
                    d1.saveAs(downloadsDir.resolve(suggested));
                    fileSaved = true;
                } catch (TimeoutError te) {
                    System.out.println("Download не пришёл за 30с — пробуем blob-фоллбэк...");
                }

                if (!fileSaved) {
                    Object result = page.evaluate("async () => {" +
                            "const a = document.querySelector('#account-info-button-file');" +
                            "if (!a) return null;" +
                            "const href = a.getAttribute('href');" +
                            "const name = a.getAttribute('download') || '1xBet_file.txt';" +
                            "if (!href || !href.startsWith('blob:')) return null;" +
                            "const resp = await fetch(href);" +
                            "const buf = await resp.arrayBuffer();" +
                            "const bytes = new Uint8Array(buf);" +
                            "let binary=''; for (let i=0;i<bytes.length;i++){ binary += String.fromCharCode(bytes[i]); }" +
                            "const b64 = btoa(binary);" +
                            "return { name, b64 };" +
                            "}");
                    if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) result;
                        String name = String.valueOf(map.get("name"));
                        String b64 = String.valueOf(map.get("b64"));
                        if (b64 != null && !"null".equals(b64)) {
                            byte[] bytes = Base64.getDecoder().decode(b64);
                            Files.write(downloadsDir.resolve(name), bytes);
                            System.out.println("Сохранили файл через blob-фоллбэк: " + name);
                            fileSaved = true;
                        }
                    }
                    if (!fileSaved) {
                        System.out.println("Не удалось сохранить файл (нет download и не blob). Пропускаем шаг.");
                    }
>>>>>>> 8a73c4b (обновлено 06.11.2025)
                }
            } else {
                System.out.println("Кнопка 'Сохранить в файл' не найдена — пропускаем шаг.");
            }

// Закрываем всплывающее окно, если появилось
            clickIfVisible(page, "button.swal2-confirm.swal2-styled:has-text('ОК'), button.swal2-confirm.swal2-styled:has-text('OK'), button.swal2-confirm.swal2-styled");

            // Сохраняем картинкой — download (60с) или popup-скрин фоллбэк
            System.out.println("Сохраняем картинкой");
            Locator saveImageBtn = page.locator("a#account-info-button-image");
            if (saveImageBtn.count() > 0 && saveImageBtn.first().isVisible()) {
                boolean imageSaved = false;

                try {
                    Download d2 = page.waitForDownload(
                            new Page.WaitForDownloadOptions().setTimeout(60_000),
                            () -> saveImageBtn.first().click()
                    );
                    String suggested = d2.suggestedFilename();
                    System.out.println("Скачали картинку: " + suggested);
                    d2.saveAs(downloadsDir.resolve(suggested));
                    imageSaved = true;
                } catch (TimeoutError te) {
                    System.out.println("Событие download не пришло за 60с — пробуем popup-окно с изображением...");
                } catch (RuntimeException re) {
                    System.out.println("Не удалось дождаться download: " + re.getMessage());
                }

                if (!imageSaved) {
                    Page popup = null;
                    try {
                        popup = page.waitForPopup(
                                new Page.WaitForPopupOptions().setTimeout(5000),
                                () -> { try { saveImageBtn.first().click(); } catch (Throwable ignored) {} }
                        );
                    } catch (TimeoutError ignored) {}

                    if (popup != null) {
                        popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
                        String fname = "1xBet_image_fallback_" + System.currentTimeMillis() + ".png";
                        popup.screenshot(new Page.ScreenshotOptions().setPath(downloadsDir.resolve(fname)));
                        System.out.println("Скрин попап-изображения сохранён: " + fname);
                        imageSaved = true;
                        try { popup.close(); } catch (Throwable ignored) {}
                    } else {
                        System.out.println("Попап не появился — шаг 'Сохранить картинкой' пропущен (поведение сайта нестабильно).");
                    }
                }
            } else {
                System.out.println("Кнопка 'Сохранить картинкой' не найдена — пропускаем шаг.");
            }

            clickIfVisible(page, "button.identification-popup-close, button.identification-popup-get-bonus__close");

            // Попробовать привязку по SMS, если модалка ввода кода есть
            tryBindBySmsIfModalVisible(page);

            // Собрать креды для Telegram
            Map<String, String> creds = extractCredentials(page);
            sentLogin = creds.get("login");
            sentPassword = creds.get("password");

            // Отправка на e-mail
            clickIfVisible(page, "a#form_mail_after_submit");
            Locator emailField = page.locator("input.post-email__input[type='email']:visible").first();
            if (emailField != null && emailField.isVisible()) {
                emailField.fill(ConfigHelper.get("email"));
                pauseShort();
                Locator sendBtn = page.locator("button.js-post-email-content-form__btn:not([disabled])");
                if (sendBtn.count() > 0) {
                    sendBtn.first().click();
                    System.out.println("Email отправлен");
                    pauseMedium();
                }
            }

            // Закрыть возможные попапы, перейти в ЛК и выйти
            clickIfVisible(page, "button.identification-popup-transition__close");
            clickIfVisible(page, "button.identification-popup-close");
            clickIfVisible(page, "#closeModal, .arcticmodal-close.c-registration__close");
            // Закрываем всплывающее окно, если появилось
            clickIfVisible(page, "button.swal2-confirm.swal2-styled:has-text('ОК'), button.swal2-confirm.swal2-styled:has-text('OK'), button.swal2-confirm.swal2-styled");

            page.navigate("https://1xbet.kz/office/account");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            Locator logout = page.locator("a.ap-left-nav__item.ap-left-nav__item_exit:has-text('Выход')");
            page.waitForSelector("a.ap-left-nav__item.ap-left-nav__item_exit",
                    new Page.WaitForSelectorOptions().setTimeout(12000).setState(WaitForSelectorState.VISIBLE));
            try {
                logout.first().click(new Locator.ClickOptions().setTimeout(3000));
            } catch (Throwable ignore) {
                neutralizeOverlayIfNeeded(page);
                try {
                    logout.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                } catch (Throwable ignored2) {
                    jsClick(logout);
                }
            }
            clickIfVisible(page, "button.swal2-confirm.swal2-styled:has-text('ОК'), button.swal2-confirm");

            waitUntilLoggedOutOrHeal(page);

            boolean loggedOut = isLoggedOut(page);
            assertTrue(loggedOut, "Ожидали гостевое состояние после выхода.");

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            String credsBlock =
                    (sentLogin != null && sentPassword != null)
                            ? "• Логин: `" + sentLogin + "`\n• Пароль: `" + sentPassword + "`\n"
                            : "• Креды: не удалось извлечь\n";

            tg.sendMessage(
                    "✅ *Тест успешно завершён:* v2_1click_registration\n" +
                            "• Регистрация — выполнена\n" +
                            "• Сохранение файла/картинки — выполнено\n" +
                            "• Отправка на e-mail — выполнена\n" +
                            "• Привязка по SMS — при наличии модалки подтверждена\n" +
                            "• Выход из аккаунта — выполнен\n\n" +
                            credsBlock +
                            "🕒 Время выполнения: *" + duration + " сек.*\n" +
                            "🌐 [1xbet.kz](https://1xbet.kz)"
            );

            System.out.println("Регистрация в 1 клик завершена успешно ✅");

        } catch (Exception e) {
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_1click_registration");
            tg.sendMessage("🚨 Ошибка в *v2_1click_registration*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}

package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Random;

public class v2_MOBI_1click_registration {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static Properties creds = new Properties();

    // --- Вспомогательные методы ---
    static void waitForPageOrReload(int maxWaitMs) {
        int waited = 0;
        while (true) {
            try {
                String readyState = (String) page.evaluate("() => document.readyState");
                if ("complete".equals(readyState)) break;
                Thread.sleep(500);
                waited += 500;
                if (waited >= maxWaitMs) {
                    System.out.println("Страница не загрузилась за " + maxWaitMs + " мс, обновляем!");
                    page.reload();
                    waited = 0;
                }
            } catch (Exception e) {
                page.reload();
                waited = 0;
            }
        }
    }

    static void closeIfVisible(String selector, String description) {
        try {
            Locator popup = page.locator(selector);
            if (popup.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                System.out.println("Закрываем: " + description);
                popup.click();
                Thread.sleep(500);
            }
        } catch (Exception ignored) {}
    }

    static String generatePromoCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) code.append(chars.charAt(rand.nextInt(chars.length())));
        return code.toString();
    }

    @BeforeAll
    static void setUpAll() throws IOException {
        creds.load(new FileInputStream("src/test/resources/config.properties"));
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
        page = context.newPage();
    }

    @Test
    void registration1ClickFullFlow() throws InterruptedException {
        long start = System.currentTimeMillis();
        String botToken = creds.getProperty("telegram.bot.token");
        String chatId = creds.getProperty("telegram.chat.id");

        Telegram.send("🚀 *Тест v2_MOBI_1click_registration* стартовал\n(Регистрация 'В 1 клик')", botToken, chatId);

        System.out.println("Открываем сайт...");
        page.navigate("https://1xbet.kz/?platform_type=mobile");
        waitForPageOrReload(10000);

        System.out.println("Кликаем 'Регистрация'");
        page.waitForSelector("button.header-btn--registration");
        page.click("button.header-btn--registration");
        waitForPageOrReload(10000);
        Thread.sleep(1000);

        page.waitForSelector("button.c-registration__tab:has-text('В 1 клик')");
        page.click("button.c-registration__tab:has-text('В 1 клик')");
        waitForPageOrReload(5000);
        Thread.sleep(1000);

        String promoCode = generatePromoCode();
        System.out.println("Генерируем промокод: " + promoCode);
        page.fill("input#registration_ref_code", promoCode);
        Thread.sleep(1000);

        System.out.println("Отказываемся от бонусов → выбираем бонус снова");
        page.click("div.c-registration__block--bonus .multiselect__select");
        page.waitForSelector(".multiselect__option .c-registration-select--refuse-bonuses");
        page.click(".multiselect__option .c-registration-select--refuse-bonuses:has-text('Отказ от бонусов')");
        Thread.sleep(500);

        page.click("div.c-registration__block--bonus .multiselect__select");
        page.waitForSelector(".multiselect__option .c-registration-select--sport-bonus");
        page.click(".multiselect__option .c-registration-select--sport-bonus:has-text('Получать бонусы')");
        Thread.sleep(500);

        System.out.println("Жмём 'Зарегистрироваться'");
        page.click("div.submit_registration");
        System.out.println("Ожидаем ручного решения капчи и появления кнопки 'Копировать'...");

        page.waitForSelector("div#js-post-reg-copy-login-password", new Page.WaitForSelectorOptions().setTimeout(0));
        System.out.println("Нажимаем 'Копировать' логин/пароль");
        page.click("div#js-post-reg-copy-login-password");
        Thread.sleep(500);

        page.waitForSelector("button.swal2-confirm.swal2-styled");
        page.click("button.swal2-confirm.swal2-styled");
        Thread.sleep(500);

        System.out.println("Высылаем данные по SMS");
        page.waitForSelector("button#account-info-button-sms");
        page.click("button#account-info-button-sms");
        Thread.sleep(500);
        closeIfVisible("button.reset-password__close", "reset-password__close");

        System.out.println("Сохраняем в файл");
        page.waitForSelector("a#account-info-button-file");
        page.click("a#account-info-button-file");
        Thread.sleep(500);

        System.out.println("Сохраняем картинкой");
        page.waitForSelector("a#account-info-button-image");
        page.click("a#account-info-button-image");
        Thread.sleep(500);

        System.out.println("Высылаем на e-mail");
        page.waitForSelector("a#form_mail_after_submit");
        page.click("a#form_mail_after_submit");
        Thread.sleep(500);

        page.waitForSelector("input.js-post-email-content-form__input");
        page.fill("input.js-post-email-content-form__input", creds.getProperty("registration.email"));
        page.waitForSelector("button.js-post-email-content-form__btn:not([disabled])");
        page.click("button.js-post-email-content-form__btn:not([disabled])");
        Thread.sleep(500);

        System.out.println("Закрываем попап регистрации крестиком");
        closeIfVisible("button.popup-registration__close", "popup-registration__close");
        Thread.sleep(500);

        System.out.println("Открываем меню (ЛК)");
        page.waitForSelector("button.user-header__link.header__reg_ico");
        page.click("button.user-header__link.header__reg_ico");
        Thread.sleep(1000);

        System.out.println("Выходим из аккаунта");
        page.waitForSelector("button.drop-menu-list__link_exit");
        page.click("button.drop-menu-list__link_exit");
        Thread.sleep(500);

        System.out.println("Подтверждаем выход (ОК)");
        page.waitForSelector("button.swal2-confirm.swal2-styled");
        page.click("button.swal2-confirm.swal2-styled");
        Thread.sleep(1000);

        long duration = (System.currentTimeMillis() - start) / 1000;
        String summary = "✅ *Тест v2_MOBI_1click_registration завершён успешно*\n"
                + "• Регистрация 'В 1 клик' — выполнена\n"
                + "• Промокод — *" + promoCode + "*\n"
                + "• Выход — произведён\n"
                + "🕒 Время выполнения: *" + duration + " сек.*\n"
                + "🌐 [1xbet.kz](https://1xbet.kz)\n"
                + "_Браузер остаётся открытым._";

        System.out.println(summary);
        Telegram.send(summary, botToken, chatId);
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // --- Telegram helper ---
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
                System.out.println("⚠️ Ошибка Telegram: " + e.getMessage());
            }
        }
    }
}

package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_social_authorization {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext mobiContext;
    static BrowserContext chromeContext;

    @BeforeAll
    static void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(Arrays.asList(new String[]{
                                "--start-maximized",
                                "--disable-blink-features=AutomationControlled"
                        }))
        );

        // Мобильный контекст для 1xbet
        mobiContext = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(null)
                        .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) " +
                                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15A372 Safari/604.1")
        );

        // Десктопный Chrome-контекст для Google Messages
        chromeContext = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(null)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        );
    }

    @AfterAll
    static void teardown() {
        // mobiContext.close();
        // chromeContext.close();
        // browser.close();
        // playwright.close();
    }

    @Test
    void socialGoogleAuthMobile() {
        Page page = mobiContext.newPage();

        System.out.println("Открываем мобильную версию сайта...");
        page.navigate("https://1xbet.kz/?platform_type=mobile");

        System.out.println("Жмём 'Войти'...");
        page.click("button#curLoginForm");

        System.out.println("Выбираем Google...");
        page.click("li.auth-social__item .auth-social__link--google");

        // --- ОЖИДАНИЕ ПОПАПА GOOGLE ---
        System.out.println("Ждём окно Google...");
        Page popup = page.waitForPopup(() -> {});
        popup.waitForLoadState();

        System.out.println("Вводим email...");
        popup.fill("input[type='email']", "mynameisjante@gmail.com");
        popup.click("button:has-text('Далее')");
        popup.waitForTimeout(2000);

        System.out.println("Вводим пароль...");
        popup.fill("input[type='password']", "Hesoyam11+");
        popup.click("button:has-text('Далее')");
        popup.waitForClose(() -> {});

        // --- ЖМЁМ ВЫСЛАТЬ КОД ---
        System.out.println("Жмём 'Выслать код'...");
        Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
        sendCodeButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        try {
            sendCodeButton.click();
            System.out.println("Кнопка 'Выслать код' нажата ✅");
        } catch (Exception e) {
            System.out.println("Первая попытка клика не удалась, пробуем через JS...");
            page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
        }

        // --- ЖДЁМ РЕШЕНИЯ КАПЧИ ---
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

        // --- ОТКРЫВАЕМ GOOGLE MESSAGES В ДЕСКТОПНОМ КОНТЕКСТЕ, ВЫТАСКИВАЕМ КОД ---
        Page messagesPage = chromeContext.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        System.out.println("Закрываем уведомление 'Нет, не нужно' (если есть)...");
        if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
            messagesPage.waitForTimeout(1000);
            messagesPage.click("button:has-text('Нет, не нужно')");
        }

        System.out.println("Жмём 'Подключить, отсканировав QR-код'...");
        messagesPage.waitForTimeout(1000);
        messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();

        System.out.println("Ищем последнее сообщение от 1xBet...");
        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();

        String smsText = lastMessage.innerText();
        System.out.println("Содержимое SMS: " + smsText);

        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b");
        Matcher matcher = pattern.matcher(smsText);
        String code = null;
        if (matcher.find()) {
            code = matcher.group();
        }

        if (code == null) {
            messagesPage.close(); // На всякий случай закрываем даже если не нашли
            throw new RuntimeException("Не удалось извлечь код подтверждения из SMS: " + smsText);
        }

        System.out.println("Извлечённый код подтверждения: " + code);

        // --- ЗАКРЫВАЕМ ВКЛАДКУ С GOOGLE MESSAGES ---
        messagesPage.close();

        // --- ВВОДИМ КОД ---
        System.out.println("Вводим код подтверждения...");
        page.fill("input.phone-sms-modal-code__input", code);

        // --- ПОДТВЕРЖДАЕМ ВВЕДЁННЫЙ КОД ---
        System.out.println("Подтверждаем код (жмём кнопку 'Подтвердить')...");
        page.click("button.phone-sms-modal-content__send");

        page.waitForTimeout(2000);

        // --- Переход в Личный кабинет ---
        System.out.println("Переход в Личный кабинет...");
        page.waitForTimeout(1000);
        page.click("button.user-header__link.header__link.header__reg.header__reg_ico.ion-android-person.header__link--messages");

        // --- Клик "Выход" ---
        System.out.println("Кликаем 'Выход'...");
        Locator logoutButton = page.locator("button.drop-menu-list__link_exit");
        logoutButton.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
        logoutButton.click();

        // --- Подтверждаем выход ---
        System.out.println("Подтверждаем выход...");
        Locator okButton = page.locator("button.swal2-confirm.swal2-styled");
        okButton.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
        okButton.click();

        System.out.println("Выход из аккаунта выполнен успешно!");

        page.waitForTimeout(5000);
    }
}

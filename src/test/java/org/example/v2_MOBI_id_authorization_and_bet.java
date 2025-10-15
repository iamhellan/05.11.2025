package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization_and_bet {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized"))
        );

        // отключаем HAR/видео, чтобы убрать ворнинги
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null)
                .setRecordHarPath(null)
                .setRecordVideoDir(null)
        );

        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginBetHistoryAndLogout() {
        page.navigate("https://1xbet.kz/?platform_type=mobile");

        // --- Авторизация ---
        page.click("button#curLoginForm span.auth-btn__label:has-text('Вход')");
        page.fill("input#auth_id_email", "168715375");
        page.fill("input#auth-form-password", "Aezakmi11+");
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

        // --- Google Messages ---
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");
        if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
            messagesPage.click("button:has-text('Нет, не нужно')");
        }
        messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();

        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();
        String smsText = lastMessage.innerText();
        Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b").matcher(smsText);
        String code = matcher.find() ? matcher.group() : null;
        if (code == null) throw new RuntimeException("Код не найден в SMS");
        System.out.println("Код подтверждения: " + code);

        // --- Вводим код ---
        page.bringToFront();
        page.fill("input.phone-sms-modal-code__input", code);
        page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

        // --- Закрываем блокировку ---
        if (page.locator("a.pf-subs-btn-link__secondary:has-text('Блокировать')").isVisible()) {
            page.click("a.pf-subs-btn-link__secondary:has-text('Блокировать')");
        }

        // --- Ставка ---
        page.waitForSelector("div.coef__num", new Page.WaitForSelectorOptions().setTimeout(20000));
        try {
            Locator coefP1 = page.locator("div.coef:has-text('П1')");
            if (coefP1.count() > 0) {
                coefP1.first().click();
                System.out.println("Выбран П1 ✅");
            } else {
                page.locator("div.coef__num").first().click();
                System.out.println("П1 не найден, выбран первый коэффициент ✅");
            }
        } catch (Exception e) {
            page.locator("div.coef__num").first().click();
            System.out.println("Ошибка при клике П1, выбран первый коэффициент ✅");
        }

        // --- Купон ставок ---
        page.click("button.header__hamburger");
        page.click("span.drop-menu-list__coupon:has-text('1')");

        // --- Ввод суммы ---
        System.out.println("Активируем поле суммы ставки");
        page.click("input.bet_sum_input");
        page.waitForSelector("button.hg-button[data-skbtn='5']", new Page.WaitForSelectorOptions().setTimeout(10000));
        page.click("button.hg-button[data-skbtn='5']");
        page.waitForTimeout(500);
        page.click("button.hg-button[data-skbtn='0']");

        // --- Совершаем ставку ---
        System.out.println("Жмём 'Сделать ставку'");
        page.waitForSelector("span.bets-sums-keyboard-button__label:has-text('Сделать ставку')",
                new Page.WaitForSelectorOptions().setTimeout(10000));
        page.click("span.bets-sums-keyboard-button__label:has-text('Сделать ставку')");

        // --- Подтверждаем ставку ---
        System.out.println("Подтверждаем ставку (Ok)");
        page.waitForSelector("button.c-btn span.c-btn__text:has-text('Ok')",
                new Page.WaitForSelectorOptions().setTimeout(10000));
        page.click("button.c-btn span.c-btn__text:has-text('Ok')");

        // --- История ---
        page.click("button.user-header__link.header__reg_ico");
        page.click("a.drop-menu-list__link_history:has-text('История ставок')");

        // --- Выход ---
        page.click("button.user-header__link.header__reg_ico");
        page.click("button.drop-menu-list__link_exit:has-text('Выход')");
        page.click("button.swal2-confirm.swal2-styled:has-text('ОК')");

        System.out.println("Выход завершён ✅");
    }
}

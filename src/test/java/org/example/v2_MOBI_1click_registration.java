package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.Random;

public class v2_MOBI_1click_registration {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

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

    static void closeIdentificationPopupIfVisible() {
        try {
            Locator popupClose = page.locator("button.identification-popup-close");
            if (popupClose.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                System.out.println("Закрываем попап идентификации (identification-popup-close)");
                popupClose.click();
                Thread.sleep(500);
            }
        } catch (Exception e) {}
    }

    static void closeResetPasswordPopupIfVisible() {
        try {
            Locator popupClose = page.locator("button.reset-password__close");
            if (popupClose.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                System.out.println("Закрываем всплывающее окно (reset-password__close)");
                popupClose.click();
                Thread.sleep(500);
            }
        } catch (Exception e) {}
    }

    // Крестик popup-registration__close
    static void closeRegistrationPopupIfVisible() {
        try {
            Locator crossBtn = page.locator("button.popup-registration__close");
            if (crossBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                System.out.println("Кликаем крестик popup-registration__close (вместо 'Продолжить')");
                crossBtn.click();
                Thread.sleep(700);
            }
        } catch (Exception e) {}
    }

    static String generatePromoCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return code.toString();
    }

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
        page = context.newPage();
    }

    @Test
    void registration1ClickFullFlow() throws InterruptedException {
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
        page.fill("input#registration_ref_code", promoCode);
        Thread.sleep(1000);

        page.click("div.c-registration__block--bonus .multiselect__select");
        page.waitForSelector(".multiselect__option .c-registration-select--refuse-bonuses");
        Thread.sleep(500);

        page.click(".multiselect__option .c-registration-select--refuse-bonuses:has-text('Отказ от бонусов')");
        Thread.sleep(1000);

        page.click("div.c-registration__block--bonus .multiselect__select");
        page.waitForSelector(".multiselect__option .c-registration-select--sport-bonus");
        Thread.sleep(500);

        page.click(".multiselect__option .c-registration-select--sport-bonus:has-text('Получать бонусы')");
        Thread.sleep(1000);

        page.click("div.submit_registration");
        System.out.println("Ожидаем ручного ввода капчи (если есть). Как только появится кнопка копирования — продолжаем.");

        page.waitForSelector("div#js-post-reg-copy-login-password", new Page.WaitForSelectorOptions().setTimeout(0)); // Без ограничения времени

        System.out.println("Кликаем 'Копировать' логин/пароль");
        page.click("div#js-post-reg-copy-login-password");
        Thread.sleep(500);

        System.out.println("Ждём появления и кликаем 'ОК' после копирования");
        page.waitForSelector("button.swal2-confirm.swal2-styled");
        page.click("button.swal2-confirm.swal2-styled");
        Thread.sleep(500);

        System.out.println("Кликаем 'Получить по SMS'");
        page.waitForSelector("button#account-info-button-sms");
        page.click("button#account-info-button-sms");
        Thread.sleep(500);

        System.out.println("Закрываем первое всплывающее окно (reset-password__close)");
        closeResetPasswordPopupIfVisible();

        closeIdentificationPopupIfVisible();

        System.out.println("Кликаем 'Сохранить в файл'");
        page.waitForSelector("a#account-info-button-file");
        page.click("a#account-info-button-file");
        Thread.sleep(500);
        closeIdentificationPopupIfVisible();

        System.out.println("Кликаем 'Сохранить картинкой'");
        page.waitForSelector("a#account-info-button-image");
        page.click("a#account-info-button-image");
        Thread.sleep(500);
        closeIdentificationPopupIfVisible();

        System.out.println("Кликаем 'Выслать на e-mail'");
        page.waitForSelector("a#form_mail_after_submit");
        page.click("a#form_mail_after_submit");
        Thread.sleep(500);

        System.out.println("Вводим e-mail и отправляем");
        page.waitForSelector("input.js-post-email-content-form__input");
        page.fill("input.js-post-email-content-form__input", "zhante1111@gmail.com");
        page.waitForSelector("button.js-post-email-content-form__btn:not([disabled])");
        page.click("button.js-post-email-content-form__btn:not([disabled])");
        Thread.sleep(500);
        closeIdentificationPopupIfVisible();

        // --- Вместо "Продолжить" кликаем по крестику popup-registration__close ---
        System.out.println("Кликаем крестик popup-registration__close (вместо 'Продолжить')");
        closeRegistrationPopupIfVisible();
        Thread.sleep(500);

        // --- Далее всё по старому сценарию ---
        System.out.println("Открываем меню (ЛК)");
        page.waitForSelector("button.user-header__link.header__reg_ico");
        page.click("button.user-header__link.header__reg_ico");
        Thread.sleep(1000);

        closeResetPasswordPopupIfVisible();

        System.out.println("Нажимаем 'Выход'");
        page.waitForSelector("button.drop-menu-list__link_exit");
        page.click("button.drop-menu-list__link_exit");
        Thread.sleep(500);

        System.out.println("Подтверждаем выход (ОК)");
        page.waitForSelector("button.swal2-confirm.swal2-styled");
        page.click("button.swal2-confirm.swal2-styled");
        Thread.sleep(1000);

        System.out.println("Тест завершён ✅. Промокод: " + promoCode);
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Браузер остаётся открытым! Закрой его вручную, если нужно.");
    }
}

package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_number_authorization {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginByPhoneAndPassword() {
        System.out.println("Открываем сайт 1xbet.kz");
        page.navigate("https://1xbet.kz/");

        System.out.println("Жмём 'Войти' в шапке");
        page.waitForTimeout(1000);
        page.click("button#login-form-call");

        System.out.println("Выбираем метод входа по телефону");
        page.waitForTimeout(1000);
        page.click("button.c-input-material__custom.custom-functional-button");

        System.out.println("Вводим номер телефона");
        page.fill("input.phone-input__field[type='tel']", "7471530752");

        System.out.println("Вводим пароль");
        page.fill("input[type='password']", "Aezakmi11+");

        System.out.println("Жмём 'Войти' в форме авторизации");
        page.waitForTimeout(1000);
        page.click("button.auth-button.auth-button--block.auth-button--theme-secondary");

        // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ПОСЛЕ 'ВОЙТИ' ----
        System.out.println("Теперь решай капчу вручную — я жду кнопку 'Выслать код' (до 10 минут)...");
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
        Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
        try {
            sendCodeButton.click();
            System.out.println("Кнопка 'Выслать код' нажата ✅");
        } catch (Exception e) {
            System.out.println("Первая попытка клика не удалась, пробуем через JS...");
            page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
        }

        // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ПОСЛЕ 'ВЫСЛАТЬ КОД' ----
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

        // --- КОД С GOOGLE MESSAGES ---
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
            messagesPage.click("button:has-text('Нет, не нужно')");
        }
        messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();

        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();

        String smsText = lastMessage.innerText();
        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b");
        Matcher matcher = pattern.matcher(smsText);
        String code = matcher.find() ? matcher.group() : null;
        if (code == null) throw new RuntimeException("Не удалось извлечь код подтверждения из SMS: " + smsText);

        page.bringToFront();
        page.fill("input.phone-sms-modal-code__input", code);
        page.click("button:has-text('Подтвердить')");

        System.out.println("Авторизация завершена ✅");

        // --- ЛИЧНЫЙ КАБИНЕТ ---
        System.out.println("Открываем 'Личный кабинет'");
        page.waitForTimeout(1000);
        page.click("a.header-lk-box-link[title='Личный кабинет']");

        // --- КЛИК ПО КРЕСТИКУ (если всплыл modal в кабинете) ---
        System.out.println("Пробуем закрыть popup-крестик после входа в ЛК (если он есть)");
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
            System.out.println("Всплывашки или крестика в ЛК нет, игнорируем");
        }

        // --- ВЫХОД ---
        System.out.println("Жмём 'Выход'");
        page.waitForTimeout(1000);
        page.click("a.ap-left-nav__item_exit");

        System.out.println("Подтверждаем выход кнопкой 'ОК'");
        page.waitForTimeout(1000);
        page.click("button.swal2-confirm.swal2-styled");

        System.out.println("Выход завершён ✅ (браузер остаётся открытым)");
    }
}

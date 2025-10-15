package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_number_registration_НЕУСПЕХ {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized")));
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
        page = context.newPage();
    }

    @AfterAll
    static void teardown() {
        // Браузер оставляем открытым для проверки
        // browser.close();
        // playwright.close();
    }

    @Test
    void testNumberRegistration() {
        // --- ПЕРЕХОД НА САЙТ ---
        System.out.println("Открываем сайт 1xbet.kz (полная версия)");
        page.navigate("https://1xbet.kz/");

        // --- ПЕРЕХОД В 'ПЛАТЕЖИ' ---
        System.out.println("Кликаем Платежи");
        page.click("a.header-topbar-widgets-link--payments");

        // --- НАЖИМАЕМ 'РЕГИСТРАЦИЯ' ---
        System.out.println("Нажимаем Регистрация");
        page.click("button#registration-form-call");

        // --- ВВОД НОМЕРА ---
        System.out.println("Вводим номер телефона");
        page.fill("input[type='tel']", "7471530752");

        // --- ОТПРАВИТЬ SMS ---
        System.out.println("Жмём 'Отправить SMS'");
        page.click("button#button_send_sms");

        // --- ЖДЁМ ПОЯВЛЕНИЯ КНОПКИ 'ОК' ---
        System.out.println("Жду, когда ты вручную решишь капчу. Как только появится кнопка 'ОК', нажму её сам...");
        try {
            page.waitForSelector("button.swal2-confirm.swal2-styled",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(900_000) // 15 минут на решение капчи
                            .setState(WaitForSelectorState.VISIBLE));
            System.out.println("Кнопка 'ОК' появилась! Жму на неё...");
            page.click("button.swal2-confirm.swal2-styled");
            // Можно подождать, чтобы убедиться, что модалка пропала
            page.waitForSelector("button.swal2-confirm.swal2-styled",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(10_000)
                            .setState(WaitForSelectorState.DETACHED));
            System.out.println("Кнопка 'ОК' успешно нажата ✅");
        } catch (PlaywrightException e) {
            throw new RuntimeException("Что-то пошло не так: не удалось дождаться или нажать кнопку 'ОК' после капчи!");
        }

        // --- GOOGLE MESSAGES ---
        System.out.println("Открываем Google Messages");
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        System.out.println("Закрываем уведомление 'Нет, не нужно' (если есть)");
        if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
            messagesPage.waitForTimeout(1000);
            messagesPage.click("button:has-text('Нет, не нужно')");
        }

        System.out.println("Жмём кнопку 'Подключить, отсканировав QR-код'");
        messagesPage.waitForTimeout(1000);
        messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();

        System.out.println("Ищем последнее сообщение от 1xBet");
        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();

        String smsText = lastMessage.innerText();
        System.out.println("Содержимое SMS: " + smsText);

        // --- ЛОВИМ КОД ПОДТВЕРЖДЕНИЯ ---
        // Код может быть цифры или буквы, длиной 4-8 символов подряд
        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b");
        Matcher matcher = pattern.matcher(smsText);
        String code = null;
        if (matcher.find()) {
            code = matcher.group();
        }
        if (code == null) {
            throw new RuntimeException("Не удалось извлечь код подтверждения из SMS: " + smsText);
        }
        System.out.println("Извлечённый код подтверждения: " + code);

        // --- ВВОД КОДА НА САЙТЕ ---
        System.out.println("Вводим код подтверждения");
        page.fill("input#popup_registration_phone_confirmation", code);

        System.out.println("Подтверждаем SMS");
        page.click("button.confirm_sms");

        // --- ВВОД ПРОМОКОДА ---
        String promo = generatePromo();
        System.out.println("Вводим промокод: " + promo);
        page.fill("input#popup_registration_ref_code", promo);

        // --- БОНУСЫ ---
        System.out.println("Проверяем 'Отказаться от бонуса'");
        page.locator("div.c-registration-bonus__item--close").click();

        System.out.println("Выбираем 'Принимать участие'");
        page.locator("div.c-registration-bonus__item:has-text('Принять')").click();

        // --- ФИНАЛ ---
        System.out.println("Жмём 'Зарегистрироваться'");
        page.locator("div.c-registration-button:has-text('Зарегистрироваться')").click();

        System.out.println("Регистрация по номеру завершена ✅");
    }

    private String generatePromo() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}

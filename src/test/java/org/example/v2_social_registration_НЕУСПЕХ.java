package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import java.util.List;

public class v2_social_registration_НЕУСПЕХ {

    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeAll
    static void setUpClass() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized")));
    }

    @AfterAll
    static void tearDownClass() {
        // browser.close();
        // playwright.close();
    }

    @BeforeEach
    void setUp() {
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null));
        page = context.newPage();
    }

    @AfterEach
    void tearDown() {
        // context.close();
    }

    @Test
    void registrationByGoogle() {
        // 1. Заходим на сайт
        page.navigate("https://1xbet.kz/");
        page.waitForTimeout(1500);

        // 2. В "Платежи"
        page.click("a.header-topbar-widgets-link--payments");
        page.waitForTimeout(1000);

        // 3. "Регистрация"
        page.click("button#registration-form-call");
        page.waitForTimeout(1000);

        // 4. "Соцсети и мессенджеры"
        page.click("button.c-registration__tab.soc_reg");
        page.waitForTimeout(1000);

        // 5. Отказаться от бонусов
        Locator refuseBonus = page.locator("div.c-registration-bonus__item--close");
        if (refuseBonus.isVisible()) {
            refuseBonus.click();
            page.waitForTimeout(700);
        }

        // 6. Принять бонусы (опционально)
        Locator acceptBonus = page.locator("div.c-registration-bonus__item:has(.c-registration-bonus__ico--sport)");
        if (acceptBonus.isVisible()) {
            acceptBonus.click();
            page.waitForTimeout(700);
        }

        // 7. Кликаем по Google
        Locator googleDiv = page.locator("div.c-registration__social-inner[name='google']");
        googleDiv.evaluate("el => el.scrollIntoView({behavior:'smooth', block:'center'})");
        googleDiv.evaluate("el => el.click()");
        page.waitForTimeout(1000);

        // 8. Жмём "Зарегистрироваться"
        page.click("div.c-registration__button.submit_registration");
        System.out.println("Жмём Зарегистрироваться — жди капчу и решай вручную!");

        // --- 9. Ждём редирект на Google (не по времени, а по URL)
        System.out.println("Жду редирект на Google...");
        // Ждём смены URL на любой *.google.com*
        page.waitForURL("**google.com/**", new Page.WaitForURLOptions().setTimeout(600_000)); // 10 минут на все страдания с капчей

        System.out.println("Редирект на Google пойман, продолжаю!");

        // --- 10. Меняем аккаунт на Google странице (ищем и кликаем по “Сменить аккаунт”)
        // Немного подождём появления элементов
        page.waitForTimeout(2000);

        Locator changeAccountBtn = page.locator("div.VV3oRb.YZVTmd.SmR8:has(div.AsY17b:text('Сменить аккаунт'))");
        // Если не находится сразу — пробуем чуть шире (иногда бывают только 2 класса вместо 3)
        if (!changeAccountBtn.isVisible()) {
            changeAccountBtn = page.locator("div:has(div.AsY17b:text('Сменить аккаунт'))");
        }

        // Скроллим для надёжности
        changeAccountBtn.evaluate("el => el.scrollIntoView({behavior:'smooth', block:'center'})");
        changeAccountBtn.click();

        System.out.println("Клик по 'Сменить аккаунт' на Google выполнен ✅");

        // --- Здесь можно продолжить сценарий: выбор аккаунта, ввод логина/пароля, и т.д. ---
    }
}

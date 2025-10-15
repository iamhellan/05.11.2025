package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.List;

public class v2_MOBI_promo_ПРОЦЕСС {
    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeAll
    static void setupAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized", "--window-size=1920,1080"))
        );
    }

    @AfterAll
    static void tearDownAll() {
        // browser.close();
        // playwright.close();
    }

    @BeforeEach
    void setup() {
        // Отключаем viewport — тогда окно будет реально полноэкранное
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null)
                .setUserAgent("Mozilla/5.0 (Linux; Android 11; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.74 Mobile Safari/537.36")
        );
        page = context.newPage();
    }

    @AfterEach
    void tearDown() {
        // context.close();
    }

    @Test
    void promoTest() {
        System.out.println("Открываем сайт...");
        page.navigate("https://1xbet.kz/?platform_type=mobile");

        // Бургер-меню
        System.out.println("Открываем бургер-меню");
        page.waitForSelector("button.header__hamburger");
        page.click("button.header__hamburger");

        // Акции & Promo (fix: клик через JS из-за перекрытия)
        System.out.println("Жмём 'Акции & Promo' через JS");
        page.waitForSelector("span.drop-menu-list__link");
        page.evaluate(
                "Array.from(document.querySelectorAll('span.drop-menu-list__link')).find(e => e.innerText.includes('Акции')).click()"
        );

        // Ждём появления блока с акциями
        System.out.println("Ждём блок с акциями...");
        Locator promoBlock = page.locator("div.drop-menu-list_inner");
        promoBlock.waitFor(new Locator.WaitForOptions().setTimeout(5000));

        // Получаем все акции
        List<Locator> promoLinks = promoBlock.locator("a.drop-menu-list__link").all();
        System.out.println("Найдено акций: " + promoLinks.size());
        int idx = 1;

        for (Locator link : promoLinks) {
            String title = link.innerText().replace("\n", " ").trim();
            String href = link.getAttribute("href");
            if (href == null || href.isBlank()) continue;
            String url = href.startsWith("http") ? href : "https://1xbet.kz" + href;

            System.out.println("[" + idx + "] Открываем акцию: " + title + " (" + url + ")");

            // Открываем в новой вкладке
            Page tab = context.newPage();
            tab.navigate(url);

            // Скроллим вниз медленно
            slowScroll(tab, true, 1200);
            // Скроллим вверх быстрее
            slowScroll(tab, false, 400);

            System.out.println("[" + idx + "] Закрываем вкладку");
            tab.close();

            idx++;
        }

        System.out.println("Тест завершён!");
    }

    // Скролл вниз (или вверх) с анимацией
    private void slowScroll(Page tab, boolean down, int msPerStep) {
        int steps = 8;
        int scrollHeight = ((Double) tab.evaluate("() => document.body.scrollHeight")).intValue();
        for (int i = 1; i <= steps; i++) {
            int position = down
                    ? scrollHeight * i / steps
                    : scrollHeight * (steps - i) / steps;
            tab.evaluate("window.scrollTo(0, " + position + ")");
            tab.waitForTimeout(msPerStep);
        }
    }
}

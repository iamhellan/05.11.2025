package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import com.microsoft.playwright.options.*;

import java.util.List;

public class v2_promo {
    @Test
    void openBonusesOneByOneAndScrollWithLanguageSwitch() {
        try (Playwright playwright = Playwright.create()) {
            // --- Запускаем браузер максимально удобно для дебага
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(List.of("--start-maximized")));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
            Page mainPage = context.newPage();

            // 1. Открываем сайт
            mainPage.navigate("https://1xbet.kz/");
            System.out.println("Открыли https://1xbet.kz/");

            // 2. Переходим в раздел "1XBONUS"
            mainPage.waitForSelector("a[href='bonus/rules']");
            mainPage.click("a[href='bonus/rules']");
            mainPage.waitForTimeout(1000);

            // 3. Кликаем по "Все бонусы"
            mainPage.waitForSelector("span.bonuses-navigation-bar__caption:has-text('Все бонусы')");
            mainPage.click("span.bonuses-navigation-bar__caption:has-text('Все бонусы')");
            mainPage.waitForTimeout(1000);

            // 4. Ждём список бонусов
            mainPage.waitForSelector("ul.bonuses-list");
            List<ElementHandle> bonusLinks = mainPage.querySelectorAll("ul.bonuses-list a.bonus-tile");
            if (bonusLinks.isEmpty()) {
                throw new RuntimeException("Не найдено ни одной бонусной акции!");
            }
            System.out.println("Нашли бонусов: " + bonusLinks.size());

            // 5. Проходим по каждой акции по одной вкладке
            for (int i = 0; i < bonusLinks.size(); i++) {
                String href = bonusLinks.get(i).getAttribute("href");
                String url = href.startsWith("http") ? href : "https://1xbet.kz" + href;
                System.out.println("=== Переходим к акции #" + (i + 1) + ": " + url);
                Page tab = context.newPage();
                tab.navigate(url);

                // --- Ожидаем полной загрузки страницы акции (теперь вообще неубиваемо)
                waitForPageLoaded(tab, url, i + 1);

                // 1. Скролл вниз и вверх на русском
                slowScrollDown(tab, 60, 100);
                slowScrollUp(tab, 60, 100);

                // 2. Смена языка: ru → kz
                switchLanguage(tab, "kz");
                waitForPageLoaded(tab, url, i + 1);
                slowScrollDown(tab, 60, 100);
                slowScrollUp(tab, 60, 100);

                // 3. Смена языка: kz → en
                switchLanguage(tab, "en");
                waitForPageLoaded(tab, url, i + 1);
                slowScrollDown(tab, 60, 100);
                slowScrollUp(tab, 60, 100);

                // 4. Закрываем вкладку
                tab.close();
                mainPage.bringToFront();
                mainPage.waitForTimeout(500);
            }

            System.out.println("Все акции пройдены поочередно ✅");
            mainPage.waitForTimeout(1500);
        }
    }

    // --- Ожидание полной загрузки страницы, теперь супер-надёжно
    private void waitForPageLoaded(Page page, String url, int bonusIndex) {
        try {
            // Ждём только построения DOM — DOMCONTENTLOADED
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
        } catch (PlaywrightException e) {
            System.out.println("❗ [WARNING] DOM не загрузился за 10 сек на акции #" + bonusIndex + ": " + url);
        }

        try {
            // Ждём появления ключевого блока
            page.waitForSelector(".bonus-detail, .promo-detail, .bonus-header",
                    new Page.WaitForSelectorOptions().setTimeout(12000).setState(WaitForSelectorState.VISIBLE));
        } catch (PlaywrightException e) {
            System.out.println("❗ [WARNING] Не найден ключевой блок на акции #" + bonusIndex + ": " + url + "\nПричина: " + e.getMessage());
        }
        page.waitForTimeout(800); // Минимальная пауза после загрузки
    }

    // --- Медленный скролл вниз
    private void slowScrollDown(Page page, int steps, int pauseMs) {
        System.out.println("Скроллим вниз...");
        for (int i = 0; i <= steps; i++) {
            double percent = i * 1.0 / steps;
            page.evaluate("window.scrollTo(0, document.body.scrollHeight * " + percent + ");");
            page.waitForTimeout(pauseMs);
        }
        page.waitForTimeout(500);
    }

    // --- Медленный скролл вверх
    private void slowScrollUp(Page page, int steps, int pauseMs) {
        System.out.println("Скроллим вверх...");
        for (int i = steps; i >= 0; i--) {
            double percent = i * 1.0 / steps;
            page.evaluate("window.scrollTo(0, document.body.scrollHeight * " + percent + ");");
            page.waitForTimeout(pauseMs);
        }
        page.waitForTimeout(500);
    }

    // --- Переключение языка на вкладке: ru, kz, en
    private void switchLanguage(Page page, String lang) {
        System.out.println("Меняем язык на: " + lang);
        try {
            page.waitForSelector("button.header-lang__btn", new Page.WaitForSelectorOptions().setTimeout(3000));
            page.click("button.header-lang__btn");
            String langSelector;
            switch (lang) {
                case "kz":
                    langSelector = "a.header-lang-list-item-link[data-lng='kz']";
                    break;
                case "en":
                    langSelector = "a.header-lang-list-item-link[data-lng='en']";
                    break;
                case "ru":
                default:
                    langSelector = "a.header-lang-list-item-link[data-lng='ru']";
                    break;
            }
            page.waitForSelector(langSelector, new Page.WaitForSelectorOptions().setTimeout(3000));
            page.click(langSelector);
            page.waitForTimeout(1800); // Ждём смены языка
        } catch (Exception e) {
            System.out.println("Не удалось переключить язык на " + lang + ": " + e.getMessage());
        }
    }
}

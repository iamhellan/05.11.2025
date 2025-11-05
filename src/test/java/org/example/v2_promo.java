package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import com.microsoft.playwright.options.*;
import java.util.regex.Pattern;

import java.nio.file.Paths;
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
            mainPage.navigate("https://1xbet.kz/?whn=mobile&platform_type=desktop");
            System.out.println("Открыли https://1xbet.kz/");

            // 2. Переходим в раздел "1xBONUS"
            mainPage.waitForSelector("a[href='bonus/rules']");
            mainPage.click("a[href='bonus/rules']");
            mainPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            mainPage.waitForTimeout(1500);

// 3. Переходим во вкладку "Все бонусы"
            try {
                Locator allBonusesButton = mainPage.locator("button.bonus-navigation-tabs-item-link:has-text('Все бонусы')");
                allBonusesButton.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                allBonusesButton.click();
                System.out.println("Открыли вкладку 'Все бонусы' ✅");
            } catch (Exception e) {
                System.out.println("❗ Не удалось открыть 'Все бонусы': " + e.getMessage());
                mainPage.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("error_all_bonuses.png")));
            }

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

                // --- Кликаем по контейнеру, в котором расположен iframe ---
                try {
                    System.out.println("Кликаем по контейнеру с iframe (default-layout-container__inner)...");

                    Locator container = tab.locator("div.default-layout-container__inner");
                    container.waitFor(new Locator.WaitForOptions()
                            .setTimeout(10000)
                            .setState(WaitForSelectorState.VISIBLE));

                    BoundingBox box = container.boundingBox();
                    if (box != null) {
                        tab.mouse().click(box.x + box.width / 2, box.y + box.height / 2);
                        System.out.println("Клик по контейнеру выполнен ✅");
                    } else {
                        // fallback: если координаты не доступны, кликаем через JS
                        System.out.println("Клик по координатам не удался, пробуем через JS");
                        tab.evaluate("document.querySelector('div.default-layout-container__inner')?.click()");
                    }

                    tab.waitForTimeout(800); // короткая пауза после клика

                } catch (Exception e) {
                    System.out.println("❗ Не удалось кликнуть по контейнеру: " + e.getMessage());
                }

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

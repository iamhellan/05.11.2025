package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.nio.file.Paths;

public class v2_promo {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page mainPage;
    static TelegramNotifier tg;

    private final String screenshotsFolder = "C:\\Users\\zhntm\\IdeaProjects\\11.11.2025\\1XBONUS";
    private final List<String> promoNames = new ArrayList<>();

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of("--start-maximized"))
        );
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
        mainPage = context.newPage();
        mainPage.setDefaultTimeout(30_000);

        // --- Telegram ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);
    }

    @Test
    void openBonusesAndTakeScreenshotsInAllLanguages() {
        long startTime = System.currentTimeMillis();

        // --- Telegram уведомление о старте ---
        tg.sendMessage(
                "🚀 *Старт*: v2\\_promo (десктоп, раздел 1XBONUS)\n"
                        + "• Время: *" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "*\n"
                        + "• Сайт: [1xbet\\.kz](https://1xbet.kz)\n"
                        + "_Проверка всех доступных акций и создание скриншотов..._"
        );

        try {
            mainPage.navigate("https://1xbet.kz/");
            mainPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            mainPage.waitForTimeout(2000);
            System.out.println("Открыли https://1xbet.kz/");

            // --- Раздел 1XBONUS ---
            mainPage.waitForSelector("a[href='bonus/rules']");
            mainPage.click("a[href='bonus/rules']");
            mainPage.waitForTimeout(1000);

            // --- Кликаем "Все бонусы" ---
            Locator allBonusesBtn = mainPage.locator("button.bonus-navigation-tabs-item-link:has-text('Все бонусы')");
            try {
                allBonusesBtn.waitFor(new Locator.WaitForOptions().setTimeout(5000).setState(WaitForSelectorState.VISIBLE));
                allBonusesBtn.click();
            } catch (Exception e) {
                mainPage.evaluate("Array.from(document.querySelectorAll('button.bonus-navigation-tabs-item-link'))"
                        + ".find(el => el.textContent.includes('Все бонусы'))?.click()");
            }

            // --- Список акций ---
            mainPage.waitForSelector("ul.bonuses-list");
            List<ElementHandle> bonusLinks = mainPage.querySelectorAll("ul.bonuses-list a.bonus-tile");
            if (bonusLinks.isEmpty()) throw new RuntimeException("❌ Не найдено ни одной акции!");

            Locator bonusTitles = mainPage.locator("a.bonus-tile .bonus-tile-content__name div");
            for (int i = 0; i < bonusTitles.count(); i++) {
                try {
                    promoNames.add(bonusTitles.nth(i).innerText().trim());
                } catch (Exception ignored) {}
            }

            System.out.println("Найдено акций: " + promoNames.size());

            // --- Перебор акций ---
            for (int i = 0; i < bonusLinks.size(); i++) {
                String href = bonusLinks.get(i).getAttribute("href");
                String url = href.startsWith("http") ? href : "https://1xbet.kz" + href;
                String promoName = i < promoNames.size() ? promoNames.get(i) : ("Акция #" + (i + 1));

                System.out.println("=== " + promoName + " → " + url);

                Page tab = context.newPage();
                tab.navigate(url);
                waitForPageLoaded(tab, url, i + 1);

                takeScreenshot(tab, promoName, "ru");
                switchLanguage(tab, "kz");
                waitForPageLoaded(tab, url, i + 1);
                takeScreenshot(tab, promoName, "kz");
                switchLanguage(tab, "en");
                waitForPageLoaded(tab, url, i + 1);
                takeScreenshot(tab, promoName, "en");

                tab.close();
                mainPage.bringToFront();
                mainPage.waitForTimeout(700);
            }

            // --- Telegram отчёт ---
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            StringBuilder report = new StringBuilder();
            report.append("✅ *Успешно завершено*: v2\\_promo\n")
                    .append("• Проверено акций: *").append(promoNames.size()).append("*\n\n")
                    .append("📋 *Список акций:*\n");
            for (String name : promoNames) {
                report.append("• ").append(name.replace("-", "\\-")).append("\n");
            }
            report.append("\n📂 *Скриншоты сохранены в:*\n`")
                    .append(screenshotsFolder.replace("\\", "\\\\")).append("`\n")
                    .append("🕒 *Время выполнения:* ").append(elapsed).append(" сек.\n")
                    .append("🌐 [1xbet\\.kz](https://1xbet.kz)");

            tg.sendMessage(report.toString());

        } catch (Exception e) {
            tg.sendMessage("❌ *Ошибка в v2\\_promo*: `" + e.getMessage().replace("_", "\\_") + "`");
            e.printStackTrace();
        }
    }

    private void waitForPageLoaded(Page page, String url, int bonusIndex) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
            page.waitForSelector("header, footer, .bonus-detail, .promo-detail",
                    new Page.WaitForSelectorOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
            page.waitForTimeout(1000);
            System.out.println("✅ Страница #" + bonusIndex + " загружена: " + url);
        } catch (Exception e) {
            System.out.println("⚠ Ошибка загрузки #" + bonusIndex + ": " + url);
        }
    }

    private void takeScreenshot(Page page, String promoName, String lang) {
        try {
            String safeName = promoName.replaceAll("[^a-zA-Z0-9а-яА-Я\\s]", "").replace(" ", "_");
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = String.format("%s\\%s_%s_%s.png", screenshotsFolder, safeName, lang, timestamp);
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(filename)).setFullPage(true));
            System.out.println("📸 Скриншот сохранён: " + filename);
        } catch (Exception e) {
            System.out.println("Ошибка скриншота: " + e.getMessage());
        }
    }

    private void switchLanguage(Page page, String lang) {
        try {
            page.evaluate("document.querySelectorAll('.vfm').forEach(el => el.remove());");
            page.waitForTimeout(800);
            page.click("button.header-lang__btn");
            String selector = "a.header-lang-list-item-link[data-lng='" + lang + "']";
            page.click(selector);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(1200);
            System.out.println("🔁 Язык переключён: " + lang);
        } catch (Exception e) {
            System.out.println("⚠ Не удалось сменить язык: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}

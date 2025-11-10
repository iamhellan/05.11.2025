package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.nio.file.Paths;

public class v2_MOBI_promo {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page mainPage;
    static TelegramNotifier tg;

    private final String screenshotsFolder = "C:\\Users\\b.zhantemirov\\IdeaProjects\\1XBONUS";
    private final List<String> promoNames = new ArrayList<>();

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of("--start-maximized", "--window-size=1920,1080"))
        );

        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setViewportSize(null)
                        .setUserAgent("Mozilla/5.0 (Linux; Android 11; SM-G998B) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/95.0.4638.74 Mobile Safari/537.36")
        );

        mainPage = context.newPage();
        mainPage.setDefaultTimeout(30_000);

        // --- Telegram ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);
    }

    @Test
    void openMobilePromoAndTakeScreenshots() {
        long startTime = System.currentTimeMillis();

        // --- Telegram: старт ---
        tg.sendMessage(
                "📱 *Старт*: v2\\_MOBI\\_promo (мобильная версия)\n"
                        + "• Время: *" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "*\n"
                        + "• Сайт: [1xbet\\.kz](https://1xbet.kz/?platform_type=mobile)\n"
                        + "_Проверка акций и создание скриншотов для мобильной версии..._"
        );

        try {
            // --- Переход на мобильный сайт ---
            mainPage.navigate("https://1xbet.kz/?platform_type=mobile");
            mainPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            mainPage.waitForTimeout(2000);

            // --- Открываем бургер ---
            System.out.println("Открываем бургер-меню...");
            mainPage.click("button.header__hamburger");
            mainPage.waitForTimeout(800);

            // --- Точный клик по стрелке рядом с "Акции & Promo" ---
            System.out.println("Пробуем кликнуть стрелку у 'Акции & Promo' (через JS с ожиданием)");
            try {
                mainPage.waitForSelector("div.drop-menu-list__arrow",
                        new Page.WaitForSelectorOptions().setTimeout(8000).setState(WaitForSelectorState.ATTACHED));

                mainPage.evaluate("""
        const items = Array.from(document.querySelectorAll('div.drop-menu-list__item'));
        const target = items.find(el => el.textContent.includes('Акции'));
        if (target) {
            const arrow = target.querySelector('div.drop-menu-list__arrow');
            if (arrow) {
                const rect = arrow.getBoundingClientRect();
                window.scrollTo(0, rect.top - 100);
                arrow.click();
            }
        }
    """);

                mainPage.waitForSelector("div.drop-menu-list_inner",
                        new Page.WaitForSelectorOptions().setTimeout(8000).setState(WaitForSelectorState.VISIBLE));

                System.out.println("✅ Стрелка 'Акции & Promo' нажата, меню раскрыто");
            } catch (Exception e) {
                System.out.println("⚠ Ошибка при клике на стрелку 'Акции & Promo': " + e.getMessage());
            }

            // --- Ждём блок акций ---
            Locator promoBlock = mainPage.locator("div.drop-menu-list_inner");
            promoBlock.waitFor(new Locator.WaitForOptions().setTimeout(8000));

            List<Locator> promoLinks = promoBlock.locator("a.drop-menu-list__link").all();
            System.out.println("Найдено акций: " + promoLinks.size());
            if (promoLinks.isEmpty()) throw new RuntimeException("❌ Акции не найдены");

            // --- Сохраняем имена акций ---
            for (Locator link : promoLinks) {
                try {
                    promoNames.add(link.innerText().trim());
                } catch (Exception ignored) {}
            }

            // --- Перебор акций ---
            int index = 1;
            for (Locator link : promoLinks) {
                String href = link.getAttribute("href");
                if (href == null || href.isBlank()) continue;
                String url = href.startsWith("http") ? href : "https://1xbet.kz" + href;
                String promoName = index <= promoNames.size() ? promoNames.get(index - 1) : ("Акция #" + index);

                System.out.println("=== " + promoName + " → " + url);
                Page tab = context.newPage();
                tab.navigate(url);
                waitForPageLoaded(tab, url, index);

                takeScreenshot(tab, promoName, "ru");

                switchLanguage(tab, "kz");
                waitForPageLoaded(tab, url, index);
                takeScreenshot(tab, promoName, "kz");

                switchLanguage(tab, "en");
                waitForPageLoaded(tab, url, index);
                takeScreenshot(tab, promoName, "en");

                tab.close();
                mainPage.bringToFront();
                index++;
                mainPage.waitForTimeout(800);
            }

            // --- Telegram: завершение ---
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            StringBuilder report = new StringBuilder();
            report.append("✅ *Завершено*: v2\\_MOBI\\_promo\n")
                    .append("• Проверено акций: *").append(promoNames.size()).append("*\n\n")
                    .append("📋 *Список акций:*\n");
            for (String name : promoNames) {
                report.append("• ").append(name.replace("-", "\\-")).append("\n");
            }
            report.append("\n📂 *Скриншоты сохранены в:*\n`")
                    .append(screenshotsFolder.replace("\\", "\\\\")).append("`\n")
                    .append("🕒 *Время выполнения:* ").append(elapsed).append(" сек.\n")
                    .append("🌐 [1xbet\\.kz](https://1xbet.kz/?platform_type=mobile)");

            tg.sendMessage(report.toString());

        } catch (Exception e) {
            tg.sendMessage("❌ *Ошибка в v2\\_MOBI\\_promo*: `" + e.getMessage().replace("_", "\\_") + "`");
            e.printStackTrace();
        }
    }

    private void waitForPageLoaded(Page page, String url, int index) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
            page.waitForSelector("header, footer, .bonus-detail, .promo-detail",
                    new Page.WaitForSelectorOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
            page.waitForTimeout(1000);
            System.out.println("✅ Страница #" + index + " загружена: " + url);
        } catch (Exception e) {
            System.out.println("⚠ Ошибка загрузки #" + index + ": " + url);
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
            System.out.println("🔁 Меняем язык на: " + lang);

            // --- Открываем бургер ---
            page.waitForTimeout(1000);
            page.click("button.header__hamburger");
            page.waitForSelector("span.drop-menu-list__link");
            System.out.println("Бургер открыт");

            // --- Открываем пункт 'Настройки' ---
            page.evaluate("""
            Array.from(document.querySelectorAll('span.drop-menu-list__link'))
                .find(el => el.innerText.includes('Настройки'))?.click();
        """);
            page.waitForTimeout(800);
            System.out.println("Открыли 'Настройки'");

            // --- Кликаем 'Выбор языка' ---
            page.click("div.drop-menu-list__link--sub:has-text('Выбор языка')");
            page.waitForTimeout(1000);
            System.out.println("Открыли 'Выбор языка'");

            // --- Раскрываем список ---
            page.click("div.multiselect__select");
            page.waitForSelector("div.multiselect__content-wrapper ul.multiselect__content",
                    new Page.WaitForSelectorOptions().setTimeout(5000).setState(WaitForSelectorState.VISIBLE));
            System.out.println("Выпадающий список языков открыт");

            // --- Выбираем язык ---
            String languageText = switch (lang) {
                case "ru" -> "Русский";
                case "kz" -> "Қазақ тілі";
                case "en" -> "English";
                default -> throw new IllegalArgumentException("Неизвестный язык: " + lang);
            };

            page.evaluate("""
            const opts = Array.from(document.querySelectorAll('.multiselect__option span'));
            const target = opts.find(o => o.textContent.trim() === arguments[0]);
            if (target) target.click();
        """, languageText);

            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(1500);
            System.out.println("✅ Язык переключён: " + languageText);

            // --- Закрываем бургер ---
            page.click("button.header__hamburger");
            page.waitForTimeout(800);

        } catch (Exception e) {
            System.out.println("⚠ Ошибка при смене языка: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}

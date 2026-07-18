package com.polls.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageManagerTest {

    @TempDir
    Path dataDirectory;

    @Test
    void copiesBundledFilesAndLoadsEnglishWithReplacements() {
        LanguageManager manager = manager("en_US", this::resource);

        assertEquals("en_US", manager.getLanguageCode());
        assertEquals("&fPage 2 / 5", manager.text(
                "main.pagination.indicator", "page", 2, "pages", 5));
        assertEquals("2d 3h 4m", manager.duration(
                2 * 86_400_000L + 3 * 3_600_000L + 4 * 60_000L));
        assertEquals("45s", manager.duration(45_000L));
        assertEquals("0s", manager.duration(500L));
        assertEquals("Ended", manager.duration(0));
        assertTrue(Files.isRegularFile(dataDirectory.resolve("lang/zh_CN.yml")));
        assertTrue(Files.isRegularFile(dataDirectory.resolve("lang/en_US.yml")));
    }

    @Test
    void fallsBackToChineseForUnsupportedLanguage() {
        LanguageManager manager = manager("fr_FR", this::resource);

        assertEquals("zh_CN", manager.getLanguageCode());
        assertEquals("2天3小时4分钟", manager.duration(
                2 * 86_400_000L + 3 * 3_600_000L + 4 * 60_000L));
        assertEquals("&c只有玩家才能使用此命令。", manager.text("player.only_players"));
    }

    @Test
    void fallsBackToChineseWhenSelectedResourceIsMissing() {
        Function<String, InputStream> onlyChinese = path -> path.endsWith("zh_CN.yml")
                ? resource(path)
                : null;

        LanguageManager manager = manager("en_US", onlyChinese);

        assertEquals("zh_CN", manager.getLanguageCode());
        assertEquals("已结束", manager.duration(0));
    }

    @Test
    void usesBundledSelectedLanguageWhenEditableFileIsInvalid() throws Exception {
        Path languageDirectory = Files.createDirectories(dataDirectory.resolve("lang"));
        Files.writeString(languageDirectory.resolve("en_US.yml"), "main: [");

        LanguageManager manager = manager("en_US", this::resource);

        assertEquals("en_US", manager.getLanguageCode());
        assertEquals("&cOnly players can use this command.", manager.text("player.only_players"));
    }

    @Test
    void preservesEditedDiskFileAndFallsBackPerMissingKey() throws Exception {
        Path languageDirectory = Files.createDirectories(dataDirectory.resolve("lang"));
        Path englishFile = languageDirectory.resolve("en_US.yml");
        String customLanguage = "main:\n  title: '&8Custom Poll Menu'\n";
        Files.writeString(englishFile, customLanguage);

        LanguageManager manager = manager("en_US", this::resource);

        assertEquals("&8Custom Poll Menu", manager.text("main.title"));
        assertEquals("&cOnly players can use this command.", manager.text("player.only_players"));
        assertEquals(customLanguage, Files.readString(englishFile));
        assertEquals("unknown.key", manager.text("unknown.key"));
    }

    @Test
    void rejectsAnUnpairedReplacement() {
        LanguageManager manager = manager("zh_CN", this::resource);

        assertThrows(IllegalArgumentException.class,
                () -> manager.text("main.pagination.total", "count"));
    }

    @Test
    void doesNotReplacePlaceholderTextInsideInsertedValues() {
        LanguageManager manager = manager("en_US", this::resource);

        assertEquals("&fPage {pages} / 5", manager.text(
                "main.pagination.indicator", "page", "{pages}", "pages", 5));
    }

    @Test
    void bundledLanguagesContainTheSameKeys() throws Exception {
        YamlConfiguration chinese = bundledConfiguration("lang/zh_CN.yml");
        YamlConfiguration english = bundledConfiguration("lang/en_US.yml");

        assertEquals(chinese.getKeys(true), english.getKeys(true));
    }

    private LanguageManager manager(String language, Function<String, InputStream> loader) {
        return new LanguageManager(dataDirectory, language, loader, Logger.getAnonymousLogger());
    }

    private InputStream resource(String path) {
        return getClass().getClassLoader().getResourceAsStream(path);
    }

    private YamlConfiguration bundledConfiguration(String path) throws Exception {
        try (InputStream input = resource(path)) {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return configuration;
        }
    }
}

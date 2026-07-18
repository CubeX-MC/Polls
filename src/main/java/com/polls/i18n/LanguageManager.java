package com.polls.i18n;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads editable language files from the plugin data directory.
 */
public final class LanguageManager {

    public static final String DEFAULT_LANGUAGE = "zh_CN";
    private static final List<String> BUNDLED_LANGUAGES = List.of(DEFAULT_LANGUAGE, "en_US");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");

    private final Path languageDirectory;
    private final Function<String, InputStream> resourceLoader;
    private final Logger logger;
    private final YamlConfiguration fallbackMessages;
    private final YamlConfiguration languageDefaults;
    private final YamlConfiguration messages;
    private final String languageCode;

    public LanguageManager(JavaPlugin plugin) {
        this(
                plugin.getDataFolder().toPath(),
                plugin.getConfig().getString("language", DEFAULT_LANGUAGE),
                plugin::getResource,
                plugin.getLogger()
        );
    }

    LanguageManager(Path dataDirectory, String configuredLanguage,
                    Function<String, InputStream> resourceLoader, Logger logger) {
        this.languageDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("lang");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        this.logger = Objects.requireNonNull(logger, "logger");

        createLanguageDirectory();
        for (String bundledLanguage : BUNDLED_LANGUAGES) {
            copyBundledLanguage(bundledLanguage);
        }

        YamlConfiguration loadedFallback = loadLanguage(DEFAULT_LANGUAGE);
        YamlConfiguration bundledFallback = loadBundledLanguage(DEFAULT_LANGUAGE);
        fallbackMessages = bundledFallback != null
                ? bundledFallback
                : loadedFallback != null ? loadedFallback : new YamlConfiguration();

        String requested = configuredLanguage == null ? DEFAULT_LANGUAGE : configuredLanguage.trim();
        if (!BUNDLED_LANGUAGES.contains(requested)) {
            logger.warning("Unsupported language '" + requested + "'; falling back to " + DEFAULT_LANGUAGE + ".");
            requested = DEFAULT_LANGUAGE;
        }

        YamlConfiguration selected = requested.equals(DEFAULT_LANGUAGE)
                ? loadedFallback
                : loadLanguage(requested);
        YamlConfiguration bundledSelected = loadBundledLanguage(requested);
        if (selected == null && bundledSelected != null) {
            logger.warning("Editable language file for '" + requested
                    + "' is unavailable; using the bundled translations.");
            selected = bundledSelected;
        }
        if (selected == null) {
            logger.warning("Language file for '" + requested + "' is unavailable; falling back to "
                    + DEFAULT_LANGUAGE + ".");
            requested = DEFAULT_LANGUAGE;
            selected = loadedFallback != null ? loadedFallback : fallbackMessages;
            bundledSelected = bundledFallback;
        }

        languageCode = requested;
        messages = selected;
        languageDefaults = bundledSelected != null ? bundledSelected : fallbackMessages;
    }

    public String text(String key, String... replacements) {
        return text(key, (Object[]) replacements);
    }

    /**
     * Returns a translated string and replaces {name} placeholders using key/value pairs.
     */
    public String text(String key, Object... replacements) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(replacements, "replacements");
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be provided as key/value pairs");
        }

        String value = messages.getString(key);
        if (value == null && messages != languageDefaults) {
            value = languageDefaults.getString(key);
        }
        if (value == null && languageDefaults != fallbackMessages) {
            value = fallbackMessages.getString(key);
        }
        if (value == null) {
            value = key;
        }

        Map<String, String> replacementValues = new HashMap<>();
        for (int i = 0; i < replacements.length; i += 2) {
            replacementValues.put(
                    String.valueOf(replacements[i]),
                    String.valueOf(replacements[i + 1])
            );
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        while (matcher.find()) {
            String replacement = replacementValues.get(matcher.group(1));
            if (replacement != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Formats a remaining duration using the selected language.
     */
    public String duration(long millis) {
        if (millis <= 0) {
            return text("duration.ended");
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        StringBuilder result = new StringBuilder();

        appendDurationPart(result, days, "duration.days");
        appendDurationPart(result, hours, "duration.hours");
        if (minutes > 0) {
            appendDurationPart(result, minutes, "duration.minutes");
        }
        if (result.isEmpty()) {
            result.append(text("duration.seconds", "value", seconds));
        }
        return result.toString();
    }

    public String getLanguageCode() {
        return languageCode;
    }

    private void appendDurationPart(StringBuilder result, long value, String key) {
        if (value <= 0) return;
        if (!result.isEmpty()) {
            result.append(text("duration.separator"));
        }
        result.append(text(key, "value", value));
    }

    private void createLanguageDirectory() {
        try {
            Files.createDirectories(languageDirectory);
        } catch (IOException e) {
            logger.warning("Unable to create language directory: " + e.getMessage());
        }
    }

    private void copyBundledLanguage(String language) {
        Path destination = languageDirectory.resolve(language + ".yml");
        if (Files.exists(destination)) return;

        String resourcePath = resourcePath(language);
        try (InputStream input = resourceLoader.apply(resourcePath)) {
            if (input == null) {
                logger.warning("Bundled language resource is missing: " + resourcePath);
                return;
            }
            Files.copy(input, destination);
        } catch (IOException | RuntimeException e) {
            logger.warning("Unable to copy language file '" + language + "': " + e.getMessage());
        }
    }

    private YamlConfiguration loadLanguage(String language) {
        Path file = languageDirectory.resolve(language + ".yml");
        if (!Files.isRegularFile(file)) return null;

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file.toFile());
            return configuration;
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            logger.warning("Unable to load language file '" + language + "': " + e.getMessage());
            return null;
        }
    }

    private YamlConfiguration loadBundledLanguage(String language) {
        try (InputStream input = resourceLoader.apply(resourcePath(language))) {
            if (input == null) return null;
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return configuration;
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            logger.warning("Unable to load bundled language '" + language + "': " + e.getMessage());
            return null;
        }
    }

    private String resourcePath(String language) {
        return "lang/" + language + ".yml";
    }
}

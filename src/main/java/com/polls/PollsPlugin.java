package com.polls;

import com.polls.db.Database;
import com.polls.db.PollCache;
import com.polls.gui.MainGui;
import com.polls.i18n.LanguageManager;
import com.polls.platform.PlatformAdapter;
import com.polls.platform.BukkitAdapter;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PollsPlugin extends JavaPlugin implements CommandExecutor {

    private Database database;
    private PollCache pollCache;
    private PollScheduler scheduler;
    private PlatformAdapter platformAdapter;
    private LanguageManager languageManager;
    private final ConcurrentHashMap<UUID, Runnable> inputSessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        boolean configChanged = false;
        if (!getConfig().contains("language", true)) {
            getConfig().set("language", LanguageManager.DEFAULT_LANGUAGE);
            configChanged = true;
        }
        if (!getConfig().contains("submit-templates", true)) {
            getConfig().set("submit-templates.enabled", true);
            getConfig().set("submit-templates.available", List.of("normal", "rental", "loan"));
            getConfig().set("submit-templates.prefill-options", true);
            configChanged = true;
        }
        if (configChanged) {
            saveConfig();
        }
        languageManager = new LanguageManager(this);

        platformAdapter = createPlatformAdapter();
        if (platformAdapter == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info(languageManager.text("log.platform", "platform", platformAdapter.getPlatformName()));

        database = new Database(this);
        try {
            database.init();
        } catch (Exception e) {
            getLogger().severe(languageManager.text("log.database_init_failed", "error", e.getMessage()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pollCache = new PollCache(this);
        pollCache.reload();

        scheduler = new PollScheduler(this);
        scheduler.start();

        var pollsCommand = getCommand("polls");
        if (pollsCommand == null) {
            getLogger().severe(languageManager.text("log.command_missing"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pollsCommand.setExecutor(this);
        pollsCommand.setDescription(languageManager.text("command.description"));
        pollsCommand.setUsage(languageManager.text("command.usage"));
        localizePermission("polls.submit", "permission.submit");
        localizePermission("polls.vote", "permission.vote");
        localizePermission("polls.admin", "permission.admin");

        new Metrics(this, 32574);

        getLogger().info(languageManager.text("log.enabled", "language", languageManager.getLanguageCode()));
    }

    @Override
    public void onDisable() {
        Runnable[] cancellations = inputSessions.values().toArray(Runnable[]::new);
        inputSessions.clear();
        for (Runnable cancellation : cancellations) {
            try {
                cancellation.run();
            } catch (RuntimeException e) {
                getLogger().warning(message("log.input_cleanup_failed", "error", e.getMessage()));
            }
        }
        if (database != null) database.close();
        getLogger().info(message("log.disabled"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message("player.only_players")));
            return true;
        }
        cancelInputSession(player.getUniqueId());
        platformAdapter.runForPlayer(player, () -> new MainGui(this, player).open());
        return true;
    }

    public Database getDatabase() { return database; }
    public PollCache getPollCache() { return pollCache; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public String getAdminPermission() {
        return getConfig().getString("admin-permission", "polls.admin");
    }
    public PlatformAdapter getPlatformAdapter() { return platformAdapter; }

    public void registerInputSession(UUID playerId, Runnable cancellation) {
        Runnable previous = inputSessions.put(playerId, cancellation);
        if (previous != null && previous != cancellation) {
            previous.run();
        }
    }

    public boolean isInputSessionActive(UUID playerId, Runnable cancellation) {
        return inputSessions.get(playerId) == cancellation;
    }

    public void clearInputSession(UUID playerId, Runnable cancellation) {
        inputSessions.remove(playerId, cancellation);
    }

    private void cancelInputSession(UUID playerId) {
        Runnable cancellation = inputSessions.remove(playerId);
        if (cancellation != null) {
            cancellation.run();
        }
    }

    private PlatformAdapter createPlatformAdapter() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> adapterClass = Class.forName("com.polls.platform.PaperAdapter");
            return (PlatformAdapter) adapterClass
                    .getConstructor(JavaPlugin.class)
                    .newInstance(this);
        } catch (ClassNotFoundException e) {
            if (isFoliaServer()) {
                getLogger().severe(languageManager.text("log.folia_api_missing"));
                return null;
            }
            return new BukkitAdapter(this);
        } catch (ReflectiveOperationException | LinkageError e) {
            if (isFoliaServer()) {
                getLogger().severe(languageManager.text("log.folia_adapter_failed", "error", e.getMessage()));
                return null;
            }
            getLogger().warning(languageManager.text("log.paper_adapter_fallback", "error", e.getMessage()));
            return new BukkitAdapter(this);
        }
    }

    private boolean isFoliaServer() {
        String serverName = getServer().getName();
        return serverName != null && serverName.toLowerCase(Locale.ROOT).contains("folia");
    }

    private void localizePermission(String permissionName, String messageKey) {
        Permission permission = getServer().getPluginManager().getPermission(permissionName);
        if (permission != null) {
            permission.setDescription(languageManager.text(messageKey));
        }
    }

    private String message(String key, Object... replacements) {
        return languageManager == null ? key : languageManager.text(key, replacements);
    }
}

package com.polls;

import com.polls.db.Database;
import com.polls.db.PollCache;
import com.polls.gui.MainGui;
import com.polls.platform.PlatformAdapter;
import com.polls.platform.BukkitAdapter;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PollsPlugin extends JavaPlugin implements CommandExecutor {

    private Database database;
    private PollCache pollCache;
    private PollScheduler scheduler;
    private PlatformAdapter platformAdapter;
    private final ConcurrentHashMap<UUID, Runnable> inputSessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        platformAdapter = createPlatformAdapter();
        if (platformAdapter == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("当前平台: " + platformAdapter.getPlatformName());

        database = new Database(this);
        try {
            database.init();
        } catch (Exception e) {
            getLogger().severe("数据库初始化失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pollCache = new PollCache(this);
        pollCache.reload();

        scheduler = new PollScheduler(this);
        scheduler.start();

        var pollsCommand = getCommand("polls");
        if (pollsCommand == null) {
            getLogger().severe("plugin.yml 中缺少 polls 命令，插件无法启动。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pollsCommand.setExecutor(this);

        new Metrics(this, 32574);

        getLogger().info("Polls 已启动。");
    }

    @Override
    public void onDisable() {
        Runnable[] cancellations = inputSessions.values().toArray(Runnable[]::new);
        inputSessions.clear();
        for (Runnable cancellation : cancellations) {
            try {
                cancellation.run();
            } catch (RuntimeException e) {
                getLogger().warning("清理输入会话失败: " + e.getMessage());
            }
        }
        if (database != null) database.close();
        getLogger().info("Polls 已停止。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令。");
            return true;
        }
        cancelInputSession(player.getUniqueId());
        platformAdapter.runForPlayer(player, () -> new MainGui(this, player).open());
        return true;
    }

    public Database getDatabase() { return database; }
    public PollCache getPollCache() { return pollCache; }
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
                getLogger().severe("检测到 Folia，但缺少区域调度器 API，插件无法安全启动。");
                return null;
            }
            return new BukkitAdapter(this);
        } catch (ReflectiveOperationException | LinkageError e) {
            if (isFoliaServer()) {
                getLogger().severe("Folia 适配器初始化失败，拒绝回退到不兼容的 Bukkit 调度器: "
                        + e.getMessage());
                return null;
            }
            getLogger().warning("Paper/Folia 适配器初始化失败，将使用 Bukkit 调度器: " + e.getMessage());
            return new BukkitAdapter(this);
        }
    }

    private boolean isFoliaServer() {
        String serverName = getServer().getName();
        return serverName != null && serverName.toLowerCase(Locale.ROOT).contains("folia");
    }
}

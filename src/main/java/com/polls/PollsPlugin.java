package com.polls;

import com.polls.db.Database;
import com.polls.db.PollCache;
import com.polls.gui.MainGui;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PollsPlugin extends JavaPlugin implements CommandExecutor {

    private Database database;
    private PollCache pollCache;
    private PollScheduler scheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

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

        getCommand("polls").setExecutor(this);

        new Metrics(this, 32574);

        getLogger().info("Polls 已启动。");
    }

    @Override
    public void onDisable() {
        if (database != null) database.close();
        getLogger().info("Polls 已停止。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令。");
            return true;
        }
        getServer().getGlobalRegionScheduler().run(this, t -> new MainGui(this, player).open());
        return true;
    }

    public Database getDatabase() { return database; }
    public PollCache getPollCache() { return pollCache; }
}

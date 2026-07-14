package com.polls.platform;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper 服务器适配器，使用 Adventure API 和 Paper 异步调度器
 */
public class PaperAdapter implements PlatformAdapter {
    
    private final JavaPlugin plugin;
    private final AsyncScheduler asyncScheduler;
    
    public PaperAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.asyncScheduler = Bukkit.getAsyncScheduler();
    }
    
    @Override
    public void sendMessage(Player player, String legacyText) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
        player.sendMessage(component);
    }
    
    @Override
    public void runAsync(Runnable task) {
        asyncScheduler.runNow(plugin, scheduledTask -> task.run());
    }
    
    @Override
    public String getPlatformName() {
        return "Paper";
    }
}

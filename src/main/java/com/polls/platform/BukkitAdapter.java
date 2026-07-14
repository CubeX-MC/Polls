package com.polls.platform;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit/Spigot/Folia 适配器，使用传统 ChatColor
 */
public class BukkitAdapter implements PlatformAdapter {
    
    private final JavaPlugin plugin;
    
    public BukkitAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void sendMessage(Player player, String legacyText) {
        String colored = ChatColor.translateAlternateColorCodes('&', legacyText);
        player.sendMessage(colored);
    }
    
    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    @Override
    public String getPlatformName() {
        return "Bukkit/Spigot/Folia";
    }
}

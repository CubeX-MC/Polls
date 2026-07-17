package com.polls.platform;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Bukkit/Spigot 适配器，使用传统调度器和 ChatColor
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
    public Listener createChatInputListener(Player player, Predicate<String> inputHandler) {
        return new BukkitChatInputListener(player.getUniqueId(), inputHandler);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    @Override
    public String getPlatformName() {
        return "Bukkit/Spigot";
    }

    @SuppressWarnings("deprecation")
    private static final class BukkitChatInputListener implements Listener {

        private final UUID playerId;
        private final Predicate<String> inputHandler;

        private BukkitChatInputListener(UUID playerId, Predicate<String> inputHandler) {
            this.playerId = playerId;
            this.inputHandler = inputHandler;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onAsyncChatInput(AsyncPlayerChatEvent event) {
            handleChat(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onAsyncChatFinal(AsyncPlayerChatEvent event) {
            handleChat(event);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChatInput(PlayerChatEvent event) {
            handleChat(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onChatFinal(PlayerChatEvent event) {
            handleChat(event);
        }

        private void handleChat(AsyncPlayerChatEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerId)) return;
            if (inputHandler.test(event.getMessage().trim())) {
                event.setCancelled(true);
                event.setMessage("");
                try {
                    event.getRecipients().clear();
                } catch (UnsupportedOperationException ignored) {
                    // Cancellation still prevents the standard Bukkit broadcast.
                }
            }
        }

        private void handleChat(PlayerChatEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerId)) return;
            if (inputHandler.test(event.getMessage().trim())) {
                event.setCancelled(true);
                event.setMessage("");
                try {
                    event.getRecipients().clear();
                } catch (UnsupportedOperationException ignored) {
                    // Cancellation still prevents the standard Bukkit broadcast.
                }
            }
        }
    }
}

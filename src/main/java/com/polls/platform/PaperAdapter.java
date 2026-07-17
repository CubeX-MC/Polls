package com.polls.platform;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.event.player.AbstractChatEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
 * Paper/Folia 适配器，使用区域调度器和 Adventure API
 */
public class PaperAdapter implements PlatformAdapter {
    
    private final JavaPlugin plugin;
    private final AsyncScheduler asyncScheduler;
    private final GlobalRegionScheduler globalScheduler;
    
    public PaperAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.asyncScheduler = Bukkit.getAsyncScheduler();
        this.globalScheduler = Bukkit.getGlobalRegionScheduler();
    }
    
    @Override
    public void sendMessage(Player player, String legacyText) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
        player.sendMessage(component);
    }

    @Override
    public Listener createChatInputListener(Player player, Predicate<String> inputHandler) {
        return new PaperChatInputListener(player.getUniqueId(), inputHandler);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    @Override
    public void runGlobal(Runnable task) {
        globalScheduler.run(plugin, scheduledTask -> task.run());
    }

    @Override
    public void runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        globalScheduler.runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                initialDelayTicks,
                periodTicks
        );
    }

    @Override
    public void runAsync(Runnable task) {
        asyncScheduler.runNow(plugin, scheduledTask -> task.run());
    }
    
    @Override
    public String getPlatformName() {
        return "Paper/Folia";
    }

    @SuppressWarnings("deprecation")
    private static final class PaperChatInputListener implements Listener {

        private final UUID playerId;
        private final Predicate<String> inputHandler;

        private PaperChatInputListener(UUID playerId, Predicate<String> inputHandler) {
            this.playerId = playerId;
            this.inputHandler = inputHandler;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onAsyncChatInput(AsyncChatEvent event) {
            handlePaperChat(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onAsyncChatFinal(AsyncChatEvent event) {
            handlePaperChat(event);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChatInput(ChatEvent event) {
            handlePaperChat(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onChatFinal(ChatEvent event) {
            handlePaperChat(event);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onLegacyAsyncChatInput(AsyncPlayerChatEvent event) {
            handleLegacyChat(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onLegacyAsyncChatFinal(AsyncPlayerChatEvent event) {
            handleLegacyChat(event);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onLegacyChatInput(PlayerChatEvent event) {
            handleLegacyChat(event);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onLegacyChatFinal(PlayerChatEvent event) {
            handleLegacyChat(event);
        }

        private void handlePaperChat(AbstractChatEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerId)) return;
            String message;
            try {
                message = PlainTextComponentSerializer.plainText()
                        .serialize(event.message())
                        .trim();
            } catch (RuntimeException e) {
                cancelPaperChat(event);
                return;
            }
            if (shouldConsume(message)) {
                cancelPaperChat(event);
            }
        }

        private void handleLegacyChat(AsyncPlayerChatEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerId)) return;
            String message;
            try {
                message = event.getMessage().trim();
            } catch (RuntimeException e) {
                cancelLegacyChat(event);
                return;
            }
            if (shouldConsume(message)) {
                cancelLegacyChat(event);
            }
        }

        private void handleLegacyChat(PlayerChatEvent event) {
            if (!event.getPlayer().getUniqueId().equals(playerId)) return;
            String message;
            try {
                message = event.getMessage().trim();
            } catch (RuntimeException e) {
                cancelLegacyChat(event);
                return;
            }
            if (shouldConsume(message)) {
                cancelLegacyChat(event);
            }
        }

        private boolean shouldConsume(String message) {
            try {
                return inputHandler.test(message);
            } catch (RuntimeException e) {
                // A broken input session must never fall through to public chat.
                return true;
            }
        }

        private void cancelPaperChat(AbstractChatEvent event) {
            event.setCancelled(true);
            event.message(Component.empty());
            try {
                event.viewers().clear();
            } catch (UnsupportedOperationException ignored) {
                // Cancellation still prevents the standard Paper broadcast.
            }
        }

        private void cancelLegacyChat(AsyncPlayerChatEvent event) {
            event.setCancelled(true);
            event.setMessage("");
            try {
                event.getRecipients().clear();
            } catch (UnsupportedOperationException ignored) {
                // Cancellation still prevents the standard Bukkit broadcast.
            }
        }

        private void cancelLegacyChat(PlayerChatEvent event) {
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

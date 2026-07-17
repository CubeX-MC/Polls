package com.polls.platform;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class BukkitChatInputListenerTest {

    @Test
    void suppressesAsyncChatAtBothPrioritiesWithoutProcessingTwice() throws Exception {
        Player player = player(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        AtomicInteger processed = new AtomicInteger();
        Listener listener = listener(player, processed);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(
                true, player, "private title", new HashSet<>(Set.of(player)));

        invoke(listener, "onAsyncChatInput", AsyncPlayerChatEvent.class, event);
        invoke(listener, "onAsyncChatFinal", AsyncPlayerChatEvent.class, event);

        assertSuppressed(event.isCancelled(), event.getMessage(), event.getRecipients(), processed);
    }

    @Test
    void suppressesSynchronousLegacyChatAtBothPrioritiesWithoutProcessingTwice() throws Exception {
        Player player = player(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        AtomicInteger processed = new AtomicInteger();
        Listener listener = listener(player, processed);
        PlayerChatEvent event = new PlayerChatEvent(
                player, "private description", "%1$s: %2$s", new HashSet<>(Set.of(player)));

        invoke(listener, "onChatInput", PlayerChatEvent.class, event);
        invoke(listener, "onChatFinal", PlayerChatEvent.class, event);

        assertSuppressed(event.isCancelled(), event.getMessage(), event.getRecipients(), processed);
    }

    private Listener listener(Player player, AtomicInteger processed) {
        AtomicBoolean processing = new AtomicBoolean();
        Predicate<String> handler = message -> {
            if (processing.compareAndSet(false, true)) {
                processed.incrementAndGet();
            }
            return true;
        };
        return new BukkitAdapter(null).createChatInputListener(player, handler);
    }

    private void assertSuppressed(boolean cancelled, String message, Set<Player> recipients,
                                  AtomicInteger processed) {
        assertTrue(cancelled);
        assertEquals("", message);
        assertTrue(recipients.isEmpty());
        assertEquals(1, processed.get());
    }

    private void invoke(Listener listener, String methodName, Class<?> eventType, Object event)
            throws Exception {
        Method method = listener.getClass().getDeclaredMethod(methodName, eventType);
        method.setAccessible(true);
        method.invoke(listener, event);
    }

    private Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "TestPlayer[" + playerId + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}

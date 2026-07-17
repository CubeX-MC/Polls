package com.polls.platform;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperChatInputListenerTest {

    @Test
    void suppressesModernPaperChatWhenInputHandlerThrows() throws Exception {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        Player player = player(playerId);
        Listener listener = listener(playerId);
        Set<Audience> viewers = new HashSet<>();
        viewers.add(Audience.empty());
        AsyncChatEvent event = new AsyncChatEvent(
                true, player, viewers, null, Component.text("private title"),
                Component.text("private title"), null);

        invoke(listener, "onAsyncChatInput", AsyncChatEvent.class, event);

        assertTrue(event.isCancelled());
        assertEquals(Component.empty(), event.message());
        assertTrue(event.viewers().isEmpty());
    }

    private Listener listener(UUID playerId) throws Exception {
        Class<?> listenerType = Class.forName("com.polls.platform.PaperAdapter$PaperChatInputListener");
        Constructor<?> constructor = listenerType.getDeclaredConstructor(UUID.class, java.util.function.Predicate.class);
        constructor.setAccessible(true);
        Predicate<String> handler = message -> {
            throw new IllegalStateException("broken input session");
        };
        return (Listener) constructor.newInstance(playerId, handler);
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

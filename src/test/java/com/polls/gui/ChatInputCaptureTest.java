package com.polls.gui;

import org.bukkit.conversations.Conversation;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatInputCaptureTest {

    @Test
    void conversationCapturesInputWithoutEchoingOrPromptOutput() {
        AtomicReference<Conversation> active = new AtomicReference<>();
        AtomicInteger rawMessages = new AtomicInteger();
        Player player = player(active, rawMessages);
        AtomicReference<String> received = new AtomicReference<>();
        ChatInputCapture capture = new ChatInputCapture(null, player, input -> {
            received.set(input);
            return true;
        });

        capture.start();
        assertTrue(player.isConversing());
        assertTrue(active.get().isModal());

        player.acceptConversationInput(" private title ");

        assertEquals("private title", received.get());
        assertEquals(0, rawMessages.get());

        capture.stop();
        assertFalse(player.isConversing());
        assertNull(active.get());
    }

    private Player player(AtomicReference<Conversation> active, AtomicInteger rawMessages) {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "beginConversation" -> {
                        Conversation conversation = (Conversation) args[0];
                        active.set(conversation);
                        conversation.outputNextPrompt();
                        yield true;
                    }
                    case "abandonConversation" -> {
                        active.set(null);
                        yield null;
                    }
                    case "isConversing" -> active.get() != null;
                    case "acceptConversationInput" -> {
                        Conversation conversation = active.get();
                        if (conversation != null) conversation.acceptInput((String) args[0]);
                        yield null;
                    }
                    case "sendRawMessage" -> {
                        rawMessages.incrementAndGet();
                        yield null;
                    }
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

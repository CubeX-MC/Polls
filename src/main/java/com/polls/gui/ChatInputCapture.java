package com.polls.gui;

import com.polls.PollsPlugin;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/** Captures chat before the normal server broadcast pipeline. */
final class ChatInputCapture {

    private final PollsPlugin plugin;
    private final Player player;
    private final Predicate<String> inputHandler;
    private Conversation conversation;

    ChatInputCapture(PollsPlugin plugin, Player player, Predicate<String> inputHandler) {
        this.plugin = plugin;
        this.player = player;
        this.inputHandler = inputHandler;
    }

    synchronized void start() {
        if (conversation != null
                && conversation.getState() == Conversation.ConversationState.STARTED) {
            return;
        }
        conversation = new SilentConversation(plugin, player, new InputPrompt());
        conversation.begin();
    }

    synchronized void stop() {
        Conversation current = conversation;
        conversation = null;
        if (current != null && current.getState() == Conversation.ConversationState.STARTED) {
            current.abandon();
        }
    }

    private final class InputPrompt extends StringPrompt {

        @Override
        public String getPromptText(ConversationContext context) {
            return "";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input != null) {
                inputHandler.test(input.trim());
            }
            return this;
        }
    }

    private static final class SilentConversation extends Conversation {

        private SilentConversation(PollsPlugin plugin, Player player, Prompt firstPrompt) {
            super(plugin, player, firstPrompt);
            modal = false;
            localEchoEnabled = false;
        }

        @Override
        public void outputNextPrompt() {
            if (currentPrompt == null) {
                super.outputNextPrompt();
            }
        }
    }
}

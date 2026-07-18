package com.polls.gui;

import com.polls.PollsPlugin;
import com.polls.model.Poll;
import com.polls.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.polls.gui.GuiUtils.color;
import static com.polls.gui.GuiUtils.makeItem;
import static com.polls.gui.GuiUtils.wrapText;

/**
 * 管理员管理议题界面（polls.admin 权限）
 * 操作：修改标题/描述、修改截止时间、删除议题
 */
public class ManageGui implements Listener {

    private final PollsPlugin plugin;
    private final Player player;
    private final Listener chatInputListener;
    private final ChatInputCapture chatInputCapture;
    private final Runnable sessionCancellation;
    private Poll poll;
    private Inventory inv;
    private boolean registered;

    // 等待输入的状态
    private volatile String pendingAction; // "title", "desc", "time", "confirm_delete"
    private volatile boolean inputProcessing;

    @FunctionalInterface
    private interface DatabaseAction {
        Poll run() throws Exception;
    }

    private static final int SLOT_EDIT_TITLE = 10;
    private static final int SLOT_EDIT_DESC  = 12;
    private static final int SLOT_EDIT_TIME  = 14;
    private static final int SLOT_DELETE     = 16;
    private static final int SLOT_BACK       = 27;

    public ManageGui(PollsPlugin plugin, Player player, Poll poll) {
        this.plugin = plugin;
        this.player = player;
        this.poll = poll;
        this.sessionCancellation = this::cancelReplacedSession;
        this.chatInputListener = plugin.getPlatformAdapter()
                .createChatInputListener(player, this::consumeChatInput);
        this.chatInputCapture = new ChatInputCapture(plugin, player, this::consumeChatInput);
    }

    public void open() {
        chatInputCapture.stop();
        inputProcessing = false;
        inv = Bukkit.createInventory(null, 36, color(text("manage.title")));
        populate();
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getPluginManager().registerEvents(chatInputListener, plugin);
            plugin.registerInputSession(player.getUniqueId(), sessionCancellation);
            registered = true;
        }
        player.openInventory(inv);
    }

    private void populate() {
        inv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, text("manage.filler"), List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // 当前议题信息
        List<String> infoLore = new ArrayList<>();
        if (poll.getDescription().isBlank()) {
            infoLore.add(color(text("manage.poll.no-description")));
        } else {
            infoLore.addAll(wrapText(poll.getDescription(), 30,
                    text("manage.poll.description-prefix")));
        }
        infoLore.add(color(text("manage.poll.deadline", "duration",
                plugin.getLanguageManager().duration(poll.getRemainingMillis()))));
        inv.setItem(4, makeItem(Material.BOOK,
                text("manage.poll.title", "title", poll.getTitle()), infoLore));

        inv.setItem(SLOT_EDIT_TITLE, makeItem(Material.NAME_TAG, text("manage.edit-title.name"),
                List.of(
                        color(text("manage.edit-title.current", "title", poll.getTitle())),
                        color(text("manage.edit-title.lore"))
                )));

        String currentDescription = poll.getDescription().isBlank()
                ? text("manage.edit-description.current-empty")
                : text("manage.edit-description.current", "description", poll.getDescription());
        inv.setItem(SLOT_EDIT_DESC, makeItem(Material.WRITABLE_BOOK,
                text("manage.edit-description.name"),
                List.of(color(currentDescription), color(text("manage.edit-description.lore")))));

        inv.setItem(SLOT_EDIT_TIME, makeItem(Material.CLOCK, text("manage.edit-time.name"),
                List.of(
                        color(text("manage.edit-time.current", "duration",
                                plugin.getLanguageManager().duration(poll.getRemainingMillis()))),
                        color(text("manage.edit-time.lore-input")),
                        color(text("manage.edit-time.lore-reset"))
                )));

        inv.setItem(SLOT_DELETE, makeItem(Material.TNT, text("manage.delete.name"),
                List.of(color(text("manage.delete.warning")), color(text("manage.delete.lore")))));

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, text("manage.back"), List.of()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getInventory().equals(inv)) return;
        event.setCancelled(true);
        if (!player.hasPermission(plugin.getAdminPermission())) {
            unregisterListener();
            player.closeInventory();
            send(text("manage.error.no-permission"));
            return;
        }

        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_EDIT_TITLE -> startInput("title", text("manage.prompt.title"));
            case SLOT_EDIT_DESC  -> startInput("desc", text("manage.prompt.description"));
            case SLOT_EDIT_TIME  -> startInput("time", text("manage.prompt.time"));
            case SLOT_DELETE     -> startInput("confirm_delete", text("manage.prompt.delete"));
            case SLOT_BACK       -> goBack();
        }
    }

    private void startInput(String action, String prompt) {
        synchronized (this) {
            pendingAction = action;
            inputProcessing = false;
        }
        player.closeInventory();
        chatInputCapture.start();
        send(prompt);
        send(text("manage.prompt.cancel"));
    }

    private boolean consumeChatInput(String message) {
        String action;
        synchronized (this) {
            if (pendingAction == null) return inputProcessing;
            action = pendingAction;
            pendingAction = null;
            inputProcessing = true;
        }

        plugin.getPlatformAdapter().runForPlayer(player, () -> {
            if (plugin.isInputSessionActive(player.getUniqueId(), sessionCancellation)) {
                handleInput(action, message);
            }
        });
        return true;
    }

    private void handleInput(String action, String msg) {
        if (!player.hasPermission(plugin.getAdminPermission())) {
            unregisterListener();
            send(text("manage.error.no-permission"));
            return;
        }
        if (msg.equalsIgnoreCase("cancel")) {
            open();
            return;
        }

        try {
            switch (action) {
                case "title" -> {
                    if (msg.isEmpty()) { send(text("manage.error.title-empty")); open(); return; }
                    int max = plugin.getConfig().getInt("max-title-length", 40);
                    if (msg.length() > max) {
                        send(text("manage.error.title-too-long", "max", Integer.toString(max)));
                        open();
                        return;
                    }
                    int pollId = poll.getId();
                    runDatabaseAction(() -> {
                        plugin.getDatabase().updatePollTitle(pollId, msg);
                        return plugin.getDatabase().loadPoll(pollId);
                    }, fresh -> {
                        if (fresh == null) plugin.getPollCache().removePoll(pollId);
                        else plugin.getPollCache().updatePollTitle(pollId, msg);
                    }, fresh -> {
                        if (fresh == null) {
                            handleMissingPoll();
                            return;
                        }
                        send(text("manage.success.title", "title", msg));
                        open();
                    });
                }
                case "desc" -> {
                    String newDescription = msg.equalsIgnoreCase("clear") ? "" : msg;
                    int max = plugin.getConfig().getInt("max-description-length", 200);
                    if (newDescription.length() > max) {
                        send(text("manage.error.description-too-long", "max", Integer.toString(max)));
                        open();
                        return;
                    }
                    int pollId = poll.getId();
                    runDatabaseAction(() -> {
                        plugin.getDatabase().updatePollDescription(pollId, newDescription);
                        return plugin.getDatabase().loadPoll(pollId);
                    }, fresh -> {
                        if (fresh == null) plugin.getPollCache().removePoll(pollId);
                        else plugin.getPollCache().updatePollDescription(pollId, newDescription);
                    }, fresh -> {
                        if (fresh == null) {
                            handleMissingPoll();
                            return;
                        }
                        send(text(newDescription.isEmpty()
                                ? "manage.success.description-cleared"
                                : "manage.success.description-updated"));
                        open();
                    });
                }
                case "time" -> {
                    long millis = DurationParser.parseMillis(msg);
                    long now = System.currentTimeMillis();
                    if (millis <= 0 || millis > Long.MAX_VALUE - now) {
                        send(text("manage.error.duration-invalid"));
                        open();
                        return;
                    }
                    long newEndsAt = now + millis;
                    int pollId = poll.getId();
                    runDatabaseAction(() -> {
                        plugin.getDatabase().updatePollEndsAt(pollId, newEndsAt);
                        return plugin.getDatabase().loadPoll(pollId);
                    }, fresh -> {
                        if (fresh == null) plugin.getPollCache().removePoll(pollId);
                        else plugin.getPollCache().updatePollEndsAt(pollId, newEndsAt);
                    }, fresh -> {
                        if (fresh == null) {
                            handleMissingPoll();
                            return;
                        }
                        send(text("manage.success.time", "duration",
                                plugin.getLanguageManager().duration(millis)));
                        open();
                    });
                }
                case "confirm_delete" -> {
                    if (!msg.equals("DELETE")) {
                        send(text("manage.delete.cancelled"));
                        open();
                        return;
                    }
                    int pollId = poll.getId();
                    runDatabaseAction(() -> {
                        plugin.getDatabase().deletePoll(pollId);
                        return null;
                    }, ignored -> plugin.getPollCache().removePoll(pollId), ignored -> {
                        unregisterListener();
                        send(text("manage.success.deleted"));
                        new MainGui(plugin, player).open();
                    });
                }
            }
        } catch (Exception e) {
            send(text("manage.error.operation"));
            plugin.getLogger().severe(text("log.manage_failed", "error", e.getMessage()));
            open();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getInventory().equals(inv)) return;
        // 如果是因为 startInput 主动关闭，pendingAction 已设置，不注销监听
        if (pendingAction == null && !inputProcessing) unregisterListener();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            unregisterListener();
        }
    }

    private void goBack() {
        unregisterListener();
        player.closeInventory();
        plugin.getPlatformAdapter().runForPlayer(player,
                () -> new DetailGui(plugin, player, poll).open());
    }

    private void send(String msg) {
        plugin.getPlatformAdapter().sendMessage(player, msg);
    }

    private void unregisterListener() {
        chatInputCapture.stop();
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(chatInputListener);
        plugin.clearInputSession(player.getUniqueId(), sessionCancellation);
        pendingAction = null;
        inputProcessing = false;
        registered = false;
    }

    private void cancelReplacedSession() {
        unregisterListener();
    }

    private void runDatabaseAction(DatabaseAction action, Consumer<Poll> cacheUpdate,
                                   Consumer<Poll> onSuccess) {
        try {
            plugin.getPlatformAdapter().runAsync(() -> {
                try {
                    Poll fresh = action.run();
                    cacheUpdate.accept(fresh);
                    plugin.getPlatformAdapter().runForPlayer(player, () -> {
                        if (!plugin.isInputSessionActive(player.getUniqueId(), sessionCancellation)) {
                            return;
                        }
                        if (fresh != null) {
                            Poll cached = plugin.getPollCache().getById(fresh.getId());
                            poll = cached != null ? cached : fresh;
                        }
                        onSuccess.accept(fresh);
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning(text("log.manage_failed", "error", e.getMessage()));
                    plugin.getPlatformAdapter().runForPlayer(player, () -> {
                        if (!plugin.isInputSessionActive(player.getUniqueId(), sessionCancellation)) {
                            return;
                        }
                        send(text("manage.error.retry"));
                        open();
                    });
                }
            });
        } catch (RuntimeException e) {
            plugin.getLogger().warning(text("log.manage_start_failed", "error", e.getMessage()));
            send(text("manage.error.retry"));
            open();
        }
    }

    private void handleMissingPoll() {
        plugin.getPollCache().removePoll(poll.getId());
        unregisterListener();
        send(text("manage.error.missing"));
        new MainGui(plugin, player).open();
    }

    private String text(String key, String... replacements) {
        return plugin.getLanguageManager().text(key, replacements);
    }
}

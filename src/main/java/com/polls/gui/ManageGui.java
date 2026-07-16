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
    private final Runnable sessionCancellation;
    private Poll poll;
    private Inventory inv;
    private boolean registered;

    // 等待输入的状态
    private volatile String pendingAction; // "title", "desc", "time", "confirm_delete"
    private volatile boolean inputProcessing;

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
    }

    public void open() {
        inputProcessing = false;
        inv = Bukkit.createInventory(null, 36, color("&8[ &c管理议题 &8]"));
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
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // 当前议题信息
        List<String> infoLore = new ArrayList<>();
        if (poll.getDescription().isBlank()) {
            infoLore.add(color("&7（无描述）"));
        } else {
            infoLore.addAll(wrapText(poll.getDescription(), 30, "&7"));
        }
        infoLore.add(color("&8截止: &f" + DurationParser.format(poll.getRemainingMillis())));
        inv.setItem(4, makeItem(Material.BOOK, "&e" + poll.getTitle(), infoLore));

        inv.setItem(SLOT_EDIT_TITLE, makeItem(Material.NAME_TAG, "&e修改标题",
                List.of(color("&7当前: &f" + poll.getTitle()), color("&7点击后在聊天框输入新标题"))));

        inv.setItem(SLOT_EDIT_DESC, makeItem(Material.WRITABLE_BOOK, "&e修改描述",
                List.of(color("&7当前: &f" + (poll.getDescription().isBlank() ? "（空）" : poll.getDescription())),
                        color("&7点击后输入新描述，输入 &fclear &7可清除"))));

        inv.setItem(SLOT_EDIT_TIME, makeItem(Material.CLOCK, "&e修改截止时间",
                List.of(color("&7当前剩余: &f" + DurationParser.format(poll.getRemainingMillis())),
                        color("&7点击后输入新的截止时长，如 &f3d"),
                        color("&7将从&f现在&7起重新计算"))));

        inv.setItem(SLOT_DELETE, makeItem(Material.TNT, "&c删除议题",
                List.of(color("&c⚠ 不可恢复！"), color("&7点击后输入 &fDELETE &7确认"))));

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "&7← 返回", List.of()));
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
            send("&c你没有管理议题的权限。");
            return;
        }

        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_EDIT_TITLE -> startInput("title", "&e请输入新的议题标题：");
            case SLOT_EDIT_DESC  -> startInput("desc",  "&e请输入新的议题描述，输入 &fclear &e可清除：");
            case SLOT_EDIT_TIME  -> startInput("time",  "&e请输入新的截止时长（如 &f3d&e / &f12h&e / &f30m&e，仅支持单个单位）：");
            case SLOT_DELETE     -> startInput("confirm_delete", "&c输入 &fDELETE &c确认删除议题，输入其他取消：");
            case SLOT_BACK       -> goBack();
        }
    }

    private void startInput(String action, String prompt) {
        synchronized (this) {
            pendingAction = action;
            inputProcessing = false;
        }
        player.closeInventory();
        send(prompt);
        send("&7输入 &ccancel &7取消");
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
            send("&c你没有管理议题的权限。");
            return;
        }
        if (msg.equalsIgnoreCase("cancel")) {
            open();
            return;
        }

        try {
            switch (action) {
                case "title" -> {
                    if (msg.isEmpty()) { send("&c标题不能为空"); open(); return; }
                    int max = plugin.getConfig().getInt("max-title-length", 40);
                    if (msg.length() > max) {
                        send("&c标题过长（最多 " + max + " 字）");
                        open();
                        return;
                    }
                    plugin.getDatabase().updatePollTitleDesc(poll.getId(), msg, poll.getDescription());
                    refreshCache();
                    send("&a标题已更新为: &f" + msg);
                    open();
                }
                case "desc" -> {
                    String newDescription = msg.equalsIgnoreCase("clear") ? "" : msg;
                    int max = plugin.getConfig().getInt("max-description-length", 200);
                    if (newDescription.length() > max) {
                        send("&c描述过长（最多 " + max + " 字）");
                        open();
                        return;
                    }
                    plugin.getDatabase().updatePollTitleDesc(poll.getId(), poll.getTitle(), newDescription);
                    refreshCache();
                    send(newDescription.isEmpty() ? "&a描述已清除。" : "&a描述已更新。");
                    open();
                }
                case "time" -> {
                    long millis = DurationParser.parseMillis(msg);
                    long now = System.currentTimeMillis();
                    if (millis <= 0 || millis > Long.MAX_VALUE - now) {
                        send("&c格式或时长有误，示例: 3d / 12h / 30m");
                        open();
                        return;
                    }
                    long newEndsAt = now + millis;
                    plugin.getDatabase().updatePollEndsAt(poll.getId(), newEndsAt);
                    refreshCache();
                    send("&a截止时间已更新，剩余: &f" + DurationParser.format(millis));
                    open();
                }
                case "confirm_delete" -> {
                    if (!msg.equals("DELETE")) { send("&7已取消删除。"); open(); return; }
                    plugin.getDatabase().deletePoll(poll.getId());
                    plugin.getPollCache().removePoll(poll.getId());
                    unregisterListener();
                    send("&a议题已删除。");
                    new MainGui(plugin, player).open();
                }
            }
        } catch (Exception e) {
            send("&c操作失败，请联系服务器管理员。");
            plugin.getLogger().severe("管理操作失败: " + e.getMessage());
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

    /** 从 DB 重新加载 poll（含最新票数）并更新缓存 */
    private void refreshCache() {
        try {
            com.polls.model.Poll fresh = plugin.getDatabase().loadPoll(poll.getId());
            if (fresh != null) {
                poll = fresh;
                plugin.getPollCache().updatePoll(fresh);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("刷新议题缓存失败: " + e.getMessage());
        }
    }
}

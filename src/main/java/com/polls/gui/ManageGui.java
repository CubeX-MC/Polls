package com.polls.gui;

import com.polls.PollsPlugin;
import com.polls.model.Poll;
import com.polls.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员管理议题界面（polls.admin 权限）
 * 操作：修改标题/描述、修改截止时间、删除议题
 */
public class ManageGui implements Listener {

    private final PollsPlugin plugin;
    private final Player player;
    private Poll poll;
    private Inventory inv;

    // 等待输入的状态
    private String pendingAction = null; // "title", "desc", "time", "confirm_delete"

    private static final int SLOT_EDIT_TITLE = 10;
    private static final int SLOT_EDIT_DESC  = 12;
    private static final int SLOT_EDIT_TIME  = 14;
    private static final int SLOT_DELETE     = 16;
    private static final int SLOT_BACK       = 27;

    public ManageGui(PollsPlugin plugin, Player player, Poll poll) {
        this.plugin = plugin;
        this.player = player;
        this.poll = poll;
    }

    public void open() {
        inv = Bukkit.createInventory(null, 36,
                LegacyComponentSerializer.legacyAmpersand().deserialize("&8[ &c管理议题 &8]"));
        populate();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inv);
    }

    private void populate() {
        inv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // 当前议题信息
        inv.setItem(4, makeItem(Material.BOOK, "&e" + poll.getTitle(),
                List.of(color("&7" + (poll.getDescription().isBlank() ? "（无描述）" : poll.getDescription())),
                        color("&8截止: &f" + DurationParser.format(poll.getRemainingMillis())))));

        inv.setItem(SLOT_EDIT_TITLE, makeItem(Material.NAME_TAG, "&e修改标题",
                List.of(color("&7当前: &f" + poll.getTitle()), color("&7点击后在聊天框输入新标题"))));

        inv.setItem(SLOT_EDIT_DESC, makeItem(Material.WRITABLE_BOOK, "&e修改描述",
                List.of(color("&7当前: &f" + (poll.getDescription().isBlank() ? "（空）" : poll.getDescription())),
                        color("&7点击后在聊天框输入新描述"))));

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

        int slot = event.getRawSlot();
        switch (slot) {
            case SLOT_EDIT_TITLE -> startInput("title", "&e请输入新的议题标题：");
            case SLOT_EDIT_DESC  -> startInput("desc",  "&e请输入新的议题描述（留空则清除）：");
            case SLOT_EDIT_TIME  -> startInput("time",  "&e请输入新的截止时长（如 &f3d&e / &f12h&e）：");
            case SLOT_DELETE     -> startInput("confirm_delete", "&c输入 &fDELETE &c确认删除议题，输入其他取消：");
            case SLOT_BACK       -> goBack();
        }
    }

    private void startInput(String action, String prompt) {
        pendingAction = action;
        player.closeInventory();
        send(prompt);
        send("&7输入 &ccancel &7取消");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        if (pendingAction == null) return;
        event.setCancelled(true);

        String msg = event.getMessage().trim();
        String action = pendingAction;
        pendingAction = null;

        if (msg.equalsIgnoreCase("cancel")) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> { open(); });
            return;
        }

        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            try {
                switch (action) {
                    case "title" -> {
                        if (msg.isEmpty()) { send("&c标题不能为空"); open(); return; }
                        int max = plugin.getConfig().getInt("max-title-length", 40);
                        if (msg.length() > max) { send("&c标题过长"); open(); return; }
                        poll.setTitle(msg);
                        plugin.getDatabase().updatePollTitleDesc(poll.getId(), poll.getTitle(), poll.getDescription());
                        plugin.getPollCache().reload();
                        send("&a标题已更新为: &f" + msg);
                        open();
                    }
                    case "desc" -> {
                        poll.setDescription(msg);
                        plugin.getDatabase().updatePollTitleDesc(poll.getId(), poll.getTitle(), poll.getDescription());
                        plugin.getPollCache().reload();
                        send("&a描述已更新。");
                        open();
                    }
                    case "time" -> {
                        long millis = DurationParser.parseMillis(msg);
                        if (millis <= 0) { send("&c格式有误，示例: 3d / 12h / 30m"); open(); return; }
                        long newEndsAt = System.currentTimeMillis() + millis;
                        poll.setEndsAt(newEndsAt);
                        plugin.getDatabase().updatePollEndsAt(poll.getId(), newEndsAt);
                        plugin.getPollCache().reload();
                        send("&a截止时间已更新，剩余: &f" + DurationParser.format(millis));
                        open();
                    }
                    case "confirm_delete" -> {
                        if (!msg.equals("DELETE")) { send("&7已取消删除。"); open(); return; }
                        plugin.getDatabase().deletePoll(poll.getId());
                        plugin.getPollCache().reload();
                        send("&a议题已删除。");
                        new MainGui(plugin, player).open();
                    }
                }
            } catch (Exception e) {
                send("&c操作失败，请联系服务器管理员。");
                plugin.getLogger().severe("管理操作失败: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getInventory().equals(inv)) return;
        // 如果是因为 startInput 主动关闭，pendingAction 已设置，不注销监听
        if (pendingAction == null) HandlerList.unregisterAll(this);
    }

    private void goBack() {
        HandlerList.unregisterAll(this);
        player.closeInventory();
        plugin.getServer().getGlobalRegionScheduler().run(plugin,
                t -> new DetailGui(plugin, player, poll).open());
    }

    private void send(String msg) {
        player.sendMessage(color(msg));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
        List<Component> loreComp = new ArrayList<>();
        for (String l : lore) loreComp.add(LegacyComponentSerializer.legacySection().deserialize(l));
        meta.lore(loreComp);
        item.setItemMeta(meta);
        return item;
    }
}

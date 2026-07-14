package com.polls.gui;

import com.polls.PollsPlugin;
import com.polls.model.Poll;
import com.polls.model.PollOption;
import com.polls.util.DurationParser;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
 * 主界面：商店风格，按状态分区展示所有议题
 * 布局（6行54格）：
 *   行0    - 标题装饰行
 *   行1-2  - 进行中（最多 12 个）
 *   行3    - 分隔行
 *   行4-5  - 已结束（最多 9 个），最后一格为提交按钮
 */
public class MainGui implements Listener {

    private final PollsPlugin plugin;
    private final Player player;
    private Inventory inv;

    // 分区起始槽位
    private static final int ACTIVE_START = 9;
    private static final int ENDED_START  = 36;
    private static final int SUBMIT_SLOT  = 53;
    private static final int PAGE_SIZE_ACTIVE = 12;
    private static final int PAGE_SIZE_ENDED  = 8; // 最后一格留给提交

    public MainGui(PollsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inv = Bukkit.createInventory(null, 54,
                LegacyComponentSerializer.legacyAmpersand().deserialize("&8[ &6民意投票 &8]"));
        populate();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inv);
    }

    private void populate() {
        inv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // 标题栏
        inv.setItem(4, makeItem(Material.PAPER, "&6&l民意投票", List.of(
                color("&7查看和参与当前议题"),
                color("&7右下角可提交新议题")
        )));

        // 分隔行（第3行）
        ItemStack divider = makeItem(Material.BLACK_STAINED_GLASS_PANE, "&8——— 已结束 ———", List.of());
        for (int i = 27; i < 36; i++) inv.setItem(i, divider);

        // 区域标签
        inv.setItem(9, makeItem(Material.LIME_STAINED_GLASS_PANE, "&a▶ 进行中", List.of()));
        inv.setItem(36, makeItem(Material.RED_STAINED_GLASS_PANE, "&7▶ 已结束", List.of()));

        List<Poll> all = plugin.getPollCache().getAll();
        List<Poll> active = all.stream().filter(Poll::isActive).toList();
        List<Poll> ended  = all.stream().filter(p -> !p.isActive()).toList();

        // 进行中（槽位 10-21，12格）
        int activeSlot = ACTIVE_START + 1;
        for (int i = 0; i < Math.min(active.size(), PAGE_SIZE_ACTIVE); i++) {
            inv.setItem(activeSlot + i, buildPollItem(active.get(i), true));
        }

        // 已结束（槽位 37-44，8格）
        int endedSlot = ENDED_START + 1;
        for (int i = 0; i < Math.min(ended.size(), PAGE_SIZE_ENDED); i++) {
            inv.setItem(endedSlot + i, buildPollItem(ended.get(i), false));
        }

        // 提交按钮
        if (player.hasPermission("polls.submit")) {
            inv.setItem(SUBMIT_SLOT, makeItem(Material.WRITABLE_BOOK, "&e+ 提交新议题",
                    List.of(color("&7点击发起一个新的投票议题"))));
        }
    }

    private ItemStack buildPollItem(Poll poll, boolean active) {
        Material mat = active ? Material.LIME_DYE : Material.GRAY_DYE;
        List<String> lore = new ArrayList<>();
        if (!poll.getDescription().isBlank()) {
            lore.addAll(wrapText(poll.getDescription(), 30, "&7"));
            lore.add(" ");
        }
        lore.add(color("&8提交者: &f" + poll.getCreatorName()));
        if (active) {
            lore.add(color("&a剩余: &f" + DurationParser.format(poll.getRemainingMillis())));
        } else {
            lore.add(color("&7已结束"));
        }
        int total = poll.getOptions().stream().mapToInt(PollOption::getVoteCount).sum();
        lore.add(color("&8总票数: &f" + total));
        lore.add(" ");
        lore.add(color(active ? "&e▶ 点击参与投票" : "&7▶ 点击查看结果"));
        return makeItem(mat, (active ? "&a" : "&7") + poll.getTitle(), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getInventory().equals(inv)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == SUBMIT_SLOT && player.hasPermission("polls.submit")) {
            HandlerList.unregisterAll(this);
            player.closeInventory();
            new SubmitFlow(plugin, player);
            return;
        }

        // 进行中区域 10-21
        if (slot >= ACTIVE_START + 1 && slot <= ACTIVE_START + PAGE_SIZE_ACTIVE) {
            int idx = slot - (ACTIVE_START + 1);
            List<Poll> active = plugin.getPollCache().getAll().stream().filter(Poll::isActive).toList();
            if (idx < active.size()) openDetail(active.get(idx));
            return;
        }

        // 已结束区域 37-44
        if (slot >= ENDED_START + 1 && slot <= ENDED_START + PAGE_SIZE_ENDED) {
            int idx = slot - (ENDED_START + 1);
            List<Poll> ended = plugin.getPollCache().getAll().stream().filter(p -> !p.isActive()).toList();
            if (idx < ended.size()) openDetail(ended.get(idx));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())
                && event.getInventory().equals(inv)) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            HandlerList.unregisterAll(this);
        }
    }

    private void openDetail(Poll poll) {
        HandlerList.unregisterAll(this);
        player.closeInventory();
        plugin.getServer().getGlobalRegionScheduler().run(plugin,
                t -> new DetailGui(plugin, player, poll).open());
    }
}

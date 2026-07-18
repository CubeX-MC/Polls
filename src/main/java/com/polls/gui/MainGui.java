package com.polls.gui;

import com.polls.PollsPlugin;
import com.polls.model.Poll;
import com.polls.model.PollOption;
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
 *   行1-2  - 进行中（每页 12 个）
 *   行3    - 分隔行
 *   行4-5  - 已结束（每页 8 个）、翻页和提交按钮
 */
public class MainGui implements Listener {

    private final PollsPlugin plugin;
    private final Player player;
    private Inventory inv;
    private List<Poll> activeSnapshot = List.of();
    private List<Poll> endedSnapshot = List.of();
    private int activePage;
    private int endedPage;
    private Runnable removeCacheListener = () -> {};
    private volatile boolean open;

    // 分区起始槽位
    private static final int ACTIVE_START = 9;
    private static final int ENDED_START  = 36;
    private static final int SUBMIT_SLOT  = 53;
    private static final int ACTIVE_PREV_SLOT = 22;
    private static final int ACTIVE_PAGE_SLOT = 24;
    private static final int ACTIVE_NEXT_SLOT = 26;
    private static final int ENDED_PREV_SLOT = 45;
    private static final int ENDED_PAGE_SLOT = 49;
    private static final int ENDED_NEXT_SLOT = 52;
    private static final int PAGE_SIZE_ACTIVE = 12;
    private static final int PAGE_SIZE_ENDED  = 8;

    public MainGui(PollsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        activePage = 0;
        endedPage = 0;
        open = true;
        removeCacheListener = plugin.getPollCache().addChangeListener(this::onCacheChanged);
        refreshSnapshots();

        inv = Bukkit.createInventory(null, 54, color(text("main.title")));
        populate();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inv);
    }

    private void refreshSnapshots() {
        List<Poll> all = plugin.getPollCache().getAll();
        activeSnapshot = all.stream().filter(Poll::isActive).toList();
        endedSnapshot = all.stream().filter(p -> !p.isActive()).toList();
        activePage = Math.min(activePage, pageCount(activeSnapshot.size(), PAGE_SIZE_ACTIVE) - 1);
        endedPage = Math.min(endedPage, pageCount(endedSnapshot.size(), PAGE_SIZE_ENDED) - 1);
    }

    private void onCacheChanged() {
        if (!open) return;
        plugin.getPlatformAdapter().runForPlayer(player, () -> {
            if (!open || inv == null || !player.isOnline()
                    || !player.getOpenInventory().getTopInventory().equals(inv)) {
                return;
            }
            refreshSnapshots();
            populate();
        });
    }

    private void populate() {
        inv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, text("main.filler"), List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // 标题栏
        inv.setItem(4, makeItem(Material.PAPER, text("main.header.name"), List.of(
                color(text("main.header.lore.view")),
                color(text("main.header.lore.submit"))
        )));

        // 分隔行（第3行）
        ItemStack divider = makeItem(Material.BLACK_STAINED_GLASS_PANE,
                text("main.divider.ended"), List.of());
        for (int i = 27; i < 36; i++) inv.setItem(i, divider);

        // 区域标签
        inv.setItem(9, makeItem(Material.LIME_STAINED_GLASS_PANE,
                text("main.section.active"), List.of()));
        inv.setItem(36, makeItem(Material.RED_STAINED_GLASS_PANE,
                text("main.section.ended"), List.of()));

        // 进行中（槽位 10-21，12格）
        int activeSlot = ACTIVE_START + 1;
        int activeOffset = activePage * PAGE_SIZE_ACTIVE;
        int activeCount = Math.min(PAGE_SIZE_ACTIVE, activeSnapshot.size() - activeOffset);
        for (int i = 0; i < activeCount; i++) {
            inv.setItem(activeSlot + i, buildPollItem(activeSnapshot.get(activeOffset + i), true));
        }
        addPageControls(activePage, activeSnapshot.size(), PAGE_SIZE_ACTIVE,
                ACTIVE_PREV_SLOT, ACTIVE_PAGE_SLOT, ACTIVE_NEXT_SLOT);

        // 已结束（槽位 37-44，8格）
        int endedSlot = ENDED_START + 1;
        int endedOffset = endedPage * PAGE_SIZE_ENDED;
        int endedCount = Math.min(PAGE_SIZE_ENDED, endedSnapshot.size() - endedOffset);
        for (int i = 0; i < endedCount; i++) {
            inv.setItem(endedSlot + i, buildPollItem(endedSnapshot.get(endedOffset + i), false));
        }
        addPageControls(endedPage, endedSnapshot.size(), PAGE_SIZE_ENDED,
                ENDED_PREV_SLOT, ENDED_PAGE_SLOT, ENDED_NEXT_SLOT);

        // 提交按钮
        if (player.hasPermission("polls.submit")) {
            inv.setItem(SUBMIT_SLOT, makeItem(Material.WRITABLE_BOOK, text("main.submit.name"),
                    List.of(color(text("main.submit.lore")))));
        }
    }

    private void addPageControls(int page, int itemCount, int pageSize,
                                 int previousSlot, int pageSlot, int nextSlot) {
        int pages = pageCount(itemCount, pageSize);
        if (page > 0) {
            inv.setItem(previousSlot, makeItem(Material.ARROW,
                    text("main.pagination.previous"), List.of()));
        }
        inv.setItem(pageSlot, makeItem(Material.MAP,
                text("main.pagination.indicator",
                        "page", String.valueOf(page + 1),
                        "pages", String.valueOf(pages)),
                List.of(color(text("main.pagination.total",
                        "count", String.valueOf(itemCount))))));
        if (page + 1 < pages) {
            inv.setItem(nextSlot, makeItem(Material.ARROW,
                    text("main.pagination.next"), List.of()));
        }
    }

    private int pageCount(int itemCount, int pageSize) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private ItemStack buildPollItem(Poll poll, boolean active) {
        Material mat = active ? Material.LIME_DYE : Material.GRAY_DYE;
        List<String> lore = new ArrayList<>();
        if (!poll.getDescription().isBlank()) {
            lore.addAll(wrapText(poll.getDescription(), 30,
                    text("main.poll.description-prefix")));
            lore.add(" ");
        }
        lore.add(color(text("main.poll.creator", "creator", poll.getCreatorName())));
        if (active) {
            lore.add(color(text("main.poll.remaining",
                    "duration", plugin.getLanguageManager().duration(poll.getRemainingMillis()))));
        } else {
            lore.add(color(text("main.poll.ended")));
        }
        int total = poll.getOptions().stream().mapToInt(PollOption::getVoteCount).sum();
        lore.add(color(text("main.poll.total-votes", "count", String.valueOf(total))));
        lore.add(" ");
        lore.add(color(text(active ? "main.poll.action.vote" : "main.poll.action.results")));
        return makeItem(mat, text(active ? "main.poll.title.active" : "main.poll.title.ended",
                "title", poll.getTitle()), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getInventory().equals(inv)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == ACTIVE_PREV_SLOT && activePage > 0) {
            activePage--;
            populate();
            return;
        }
        if (slot == ACTIVE_NEXT_SLOT
                && activePage + 1 < pageCount(activeSnapshot.size(), PAGE_SIZE_ACTIVE)) {
            activePage++;
            populate();
            return;
        }
        if (slot == ENDED_PREV_SLOT && endedPage > 0) {
            endedPage--;
            populate();
            return;
        }
        if (slot == ENDED_NEXT_SLOT
                && endedPage + 1 < pageCount(endedSnapshot.size(), PAGE_SIZE_ENDED)) {
            endedPage++;
            populate();
            return;
        }

        if (slot == SUBMIT_SLOT && player.hasPermission("polls.submit")) {
            unregister();
            player.closeInventory();
            new SubmitFlow(plugin, player);
            return;
        }

        // 进行中区域 10-21
        if (slot >= ACTIVE_START + 1 && slot <= ACTIVE_START + PAGE_SIZE_ACTIVE) {
            int idx = activePage * PAGE_SIZE_ACTIVE + slot - (ACTIVE_START + 1);
            if (idx < activeSnapshot.size()) openDetail(activeSnapshot.get(idx));
            return;
        }

        // 已结束区域 37-44
        if (slot >= ENDED_START + 1 && slot <= ENDED_START + PAGE_SIZE_ENDED) {
            int idx = endedPage * PAGE_SIZE_ENDED + slot - (ENDED_START + 1);
            if (idx < endedSnapshot.size()) openDetail(endedSnapshot.get(idx));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())
                && event.getInventory().equals(inv)) {
            unregister();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            unregister();
        }
    }

    private void openDetail(Poll poll) {
        Poll latest = plugin.getPollCache().getById(poll.getId());
        unregister();
        player.closeInventory();
        plugin.getPlatformAdapter().runForPlayer(player,
                () -> new DetailGui(plugin, player, latest != null ? latest : poll).open());
    }

    private void unregister() {
        open = false;
        removeCacheListener.run();
        removeCacheListener = () -> {};
        HandlerList.unregisterAll(this);
    }

    private String text(String key, String... replacements) {
        return plugin.getLanguageManager().text(key, replacements);
    }
}

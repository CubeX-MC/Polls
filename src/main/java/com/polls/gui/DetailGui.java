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
import java.util.Locale;

import static com.polls.gui.GuiUtils.color;
import static com.polls.gui.GuiUtils.makeItem;
import static com.polls.gui.GuiUtils.wrapText;

/**
 * 议题详情 + 投票界面
 * 布局（4行36格）：
 *   行0 - 议题信息
 *   行1-2 - 选项（最多9个）
 *   行3 - 操作栏（返回 / 管理员管理）
 */
public class DetailGui implements Listener {

    private final PollsPlugin plugin;
    private final Player player;
    private final int pollId;
    private Poll poll;
    private Inventory inv;
    private boolean votePending;
    private boolean dataLoaded;
    private int votedOptionId = -1;
    private Runnable removeCacheListener = () -> {};
    private volatile boolean open;

    private static final int BACK_SLOT   = 27;
    private static final int MANAGE_SLOT = 35;

    public DetailGui(PollsPlugin plugin, Player player, Poll poll) {
        this.plugin = plugin;
        this.player = player;
        this.poll = poll;
        this.pollId = poll.getId();
    }

    public void open() {
        inv = Bukkit.createInventory(null, 36, color(text("detail.title")));
        showLoading();
        open = true;
        removeCacheListener = plugin.getPollCache().addChangeListener(this::onCacheChanged);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inv);
        loadLatest();
    }

    private void showLoading() {
        inv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, text("detail.filler"), List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);
        inv.setItem(4, makeItem(Material.BOOK,
                text("detail.poll.title", "title", poll.getTitle()), List.of()));
        inv.setItem(13, makeItem(Material.CLOCK, text("detail.loading"), List.of()));
        inv.setItem(BACK_SLOT, makeItem(Material.ARROW, text("detail.back"), List.of()));
    }

    private void loadLatest() {
        var playerId = player.getUniqueId();
        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                Poll fresh = plugin.getDatabase().loadPoll(pollId);
                int playerVote = fresh == null
                        ? -1
                        : plugin.getDatabase().getPlayerVote(pollId, playerId);
                if (fresh != null) {
                    plugin.getPollCache().updateVoteSnapshot(fresh);
                }
                plugin.getPlatformAdapter().runForPlayer(player, () -> {
                    if (!isViewing()) return;
                    if (fresh == null) {
                        send(text("detail.message.not-found"));
                        goBack();
                        return;
                    }
                    Poll latest = plugin.getPollCache().getById(pollId);
                    poll = latest != null ? latest : fresh;
                    votedOptionId = playerVote;
                    dataLoaded = true;
                    populate();
                });
            } catch (Exception e) {
                plugin.getLogger().warning(text("log.detail_load_failed", "error", e.getMessage()));
                plugin.getPlatformAdapter().runForPlayer(player, () -> {
                    if (!isViewing()) return;
                    inv.setItem(13, makeItem(Material.BARRIER, text("detail.load-failed.name"),
                            List.of(color(text("detail.load-failed.lore")))));
                });
            }
        });
    }

    private void onCacheChanged() {
        if (!open) return;
        plugin.getPlatformAdapter().runForPlayer(player, () -> {
            if (!isViewing() || !dataLoaded || votePending) return;
            Poll latest = plugin.getPollCache().getById(pollId);
            if (latest == null) return;
            poll = latest;
            populate();
        });
    }

    private void populate() {
        inv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, text("detail.filler"), List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // 议题信息格（槽位4）
        List<String> infoLore = new ArrayList<>();
        if (!poll.getDescription().isBlank()) {
            infoLore.addAll(wrapText(poll.getDescription(), 30,
                    text("detail.poll.description-prefix")));
            infoLore.add(" ");
        }
        infoLore.add(color(text("detail.poll.creator", "creator", poll.getCreatorName())));
        if (poll.isActive()) {
            infoLore.add(color(text("detail.poll.remaining",
                    "duration", plugin.getLanguageManager().duration(poll.getRemainingMillis()))));
            infoLore.add(color(text("detail.poll.status.active")));
        } else {
            infoLore.add(color(text("detail.poll.status.ended")));
        }
        int totalVotes = poll.getOptions().stream().mapToInt(PollOption::getVoteCount).sum();
        infoLore.add(color(text("detail.poll.total-votes", "count", String.valueOf(totalVotes))));
        inv.setItem(4, makeItem(Material.BOOK,
                text("detail.poll.title", "title", poll.getTitle()), infoLore));

        // 选项（槽位 9-17，最多9个）
        List<PollOption> opts = poll.getOptions();
        for (int i = 0; i < opts.size() && i < 9; i++) {
            PollOption opt = opts.get(i);
            boolean voted = votedOptionId == opt.getId();
            inv.setItem(9 + i, buildOptionItem(opt, voted, totalVotes, poll.isActive()));
        }

        // 返回按钮
        inv.setItem(BACK_SLOT, makeItem(Material.ARROW, text("detail.back"), List.of()));

        // 管理员管理按钮
        if (player.hasPermission(plugin.getAdminPermission())) {
            inv.setItem(MANAGE_SLOT, makeItem(Material.NETHER_STAR, text("detail.manage.name"),
                    List.of(color(text("detail.manage.lore.edit")),
                            color(text("detail.manage.lore.deadline")))));
        }
    }

    private ItemStack buildOptionItem(PollOption opt, boolean playerVoted, int totalVotes, boolean active) {
        List<String> lore = new ArrayList<>();

        // 选项描述
        if (!opt.getDescription().isBlank()) {
            lore.addAll(wrapText(opt.getDescription(), 30,
                    text("detail.option.description-prefix")));
            lore.add(" ");
        }

        // 票数和占比
        int count = opt.getVoteCount();
        double pct = totalVotes > 0 ? (count * 100.0 / totalVotes) : 0;
        lore.add(color(text("detail.option.votes",
                "count", String.valueOf(count),
                "percent", String.format(Locale.ROOT, "%.1f", pct))));
        lore.add(buildBar(pct));

        if (playerVoted) {
            lore.add(" ");
            lore.add(color(text(active
                    ? "detail.option.voted.active"
                    : "detail.option.voted.ended")));
        } else if (active) {
            lore.add(" ");
            lore.add(color(text("detail.option.action.vote")));
        }

        Material mat;
        String titleKey;
        if (playerVoted) {
            mat = Material.LIME_WOOL;
            titleKey = "detail.option.title.voted";
        } else if (!active) {
            mat = Material.LIGHT_GRAY_WOOL;
            titleKey = "detail.option.title.ended";
        } else {
            mat = Material.WHITE_WOOL;
            titleKey = "detail.option.title.active";
        }
        return makeItem(mat, text(titleKey, "option", opt.getLabel()), lore);
    }

    private String buildBar(double pct) {
        int filled = (int) Math.round(pct / 10);
        StringBuilder bar = new StringBuilder(text("detail.option.bar.start"));
        for (int i = 0; i < 10; i++) {
            bar.append(text(i < filled
                    ? "detail.option.bar.filled"
                    : "detail.option.bar.empty"));
        }
        bar.append(text("detail.option.bar.end"));
        return color(bar.toString());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getInventory().equals(inv)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 36) return;

        if (slot == BACK_SLOT) {
            if (votePending) {
                send(text("detail.message.vote-pending"));
                return;
            }
            goBack(); return;
        }

        if (slot == MANAGE_SLOT && player.hasPermission(plugin.getAdminPermission())) {
            unregister();
            player.closeInventory();
            plugin.getPlatformAdapter().runForPlayer(player,
                    () -> new ManageGui(plugin, player, poll).open());
            return;
        }

        // 选项槽位 9-17
        if (dataLoaded && slot >= 9 && slot <= 17 && poll.isActive()) {
            if (!player.hasPermission("polls.vote")) {
                send(text("detail.message.no-permission"));
                return;
            }
            if (votePending) return;
            int idx = slot - 9;
            List<PollOption> opts = poll.getOptions();
            if (idx >= opts.size()) return;

            PollOption chosen = opts.get(idx);
            int pollId = poll.getId();
            var playerId = player.getUniqueId();
            votePending = true;
            plugin.getPlatformAdapter().runAsync(() -> castVote(pollId, playerId, chosen));
        }
    }

    private void castVote(int pollId, java.util.UUID playerId, PollOption chosen) {
        try {
            boolean voted = plugin.getDatabase().castVote(pollId, playerId, chosen.getId());
            int storedOptionId = voted
                    ? chosen.getId()
                    : plugin.getDatabase().getPlayerVote(pollId, playerId);
            boolean alreadyVoted = !voted && storedOptionId != -1;
            Poll fresh = plugin.getDatabase().loadPoll(pollId);
            if (fresh != null) {
                plugin.getPollCache().updateVoteSnapshot(fresh);
            }
            plugin.getPlatformAdapter().runForPlayer(player, () -> {
                votePending = false;
                if (fresh != null) {
                    Poll latest = plugin.getPollCache().getById(pollId);
                    poll = latest != null ? latest : fresh;
                }
                if (storedOptionId != -1) {
                    votedOptionId = storedOptionId;
                }
                if (!voted) {
                    if (alreadyVoted) {
                        send(text("detail.message.already-voted"));
                    } else if (fresh != null && !fresh.isActive()) {
                        send(text("detail.message.poll-ended"));
                    } else {
                        send(text("detail.message.option-invalid"));
                    }
                    if (fresh != null && isViewing()) populate();
                    return;
                }
                if (fresh == null) {
                    send(text("detail.message.refresh-failed"));
                    return;
                }
                send(text("detail.message.vote-success", "option", chosen.getLabel()));
                if (isViewing()) {
                    populate();
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe(text("log.vote_failed", "error", e.getMessage()));
            plugin.getPlatformAdapter().runForPlayer(player, () -> {
                votePending = false;
                send(text("detail.message.vote-failed"));
            });
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

    private void goBack() {
        unregister();
        player.closeInventory();
        plugin.getPlatformAdapter().runForPlayer(player,
                () -> new MainGui(plugin, player).open());
    }

    private void send(String message) {
        plugin.getPlatformAdapter().sendMessage(player, message);
    }

    private boolean isViewing() {
        return open && player.isOnline()
                && player.getOpenInventory().getTopInventory().equals(inv);
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

package com.polls.gui;

import com.polls.PollsPlugin;
import com.polls.model.Poll;
import com.polls.model.PollOption;
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
        inv = Bukkit.createInventory(null, 36, color("&8[ &6议题详情 &8]"));
        showLoading();
        open = true;
        removeCacheListener = plugin.getPollCache().addChangeListener(this::onCacheChanged);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inv);
        loadLatest();
    }

    private void showLoading() {
        inv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);
        inv.setItem(4, makeItem(Material.BOOK, "&e&l" + poll.getTitle(), List.of()));
        inv.setItem(13, makeItem(Material.CLOCK, "&e正在读取最新票数...", List.of()));
        inv.setItem(BACK_SLOT, makeItem(Material.ARROW, "&7← 返回列表", List.of()));
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
                        send("&c该议题已不存在。");
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
                plugin.getLogger().warning("加载议题详情失败: " + e.getMessage());
                plugin.getPlatformAdapter().runForPlayer(player, () -> {
                    if (!isViewing()) return;
                    inv.setItem(13, makeItem(Material.BARRIER, "&c票数加载失败",
                            List.of(color("&7请返回后重试"))));
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
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // 议题信息格（槽位4）
        List<String> infoLore = new ArrayList<>();
        if (!poll.getDescription().isBlank()) {
            infoLore.addAll(wrapText(poll.getDescription(), 30, "&f"));
            infoLore.add(" ");
        }
        infoLore.add(color("&8提交者: &7" + poll.getCreatorName()));
        if (poll.isActive()) {
            infoLore.add(color("&a剩余: &f" + DurationParser.format(poll.getRemainingMillis())));
            infoLore.add(color("&7状态: &a进行中"));
        } else {
            infoLore.add(color("&7状态: &c已结束"));
        }
        int totalVotes = poll.getOptions().stream().mapToInt(PollOption::getVoteCount).sum();
        infoLore.add(color("&8总票数: &f" + totalVotes));
        inv.setItem(4, makeItem(Material.BOOK, "&e&l" + poll.getTitle(), infoLore));

        // 选项（槽位 9-17，最多9个）
        List<PollOption> opts = poll.getOptions();
        for (int i = 0; i < opts.size() && i < 9; i++) {
            PollOption opt = opts.get(i);
            boolean voted = votedOptionId == opt.getId();
            inv.setItem(9 + i, buildOptionItem(opt, voted, totalVotes, poll.isActive()));
        }

        // 返回按钮
        inv.setItem(BACK_SLOT, makeItem(Material.ARROW, "&7← 返回列表", List.of()));

        // 管理员管理按钮
        if (player.hasPermission(plugin.getAdminPermission())) {
            inv.setItem(MANAGE_SLOT, makeItem(Material.NETHER_STAR, "&c管理此议题",
                    List.of(color("&7修改/删除议题"), color("&7修改截止时间"))));
        }
    }

    private ItemStack buildOptionItem(PollOption opt, boolean playerVoted, int totalVotes, boolean active) {
        List<String> lore = new ArrayList<>();

        // 选项描述
        if (!opt.getDescription().isBlank()) {
            lore.addAll(wrapText(opt.getDescription(), 30, "&7"));
            lore.add(" ");
        }

        // 票数和占比
        int count = opt.getVoteCount();
        double pct = totalVotes > 0 ? (count * 100.0 / totalVotes) : 0;
        lore.add(color("&8票数: &f" + count + " &7(" + String.format("%.1f", pct) + "%)"));
        lore.add(buildBar(pct));

        if (playerVoted) {
            lore.add(" ");
            lore.add(color(active ? "&a✔ 你已投此选项" : "&a✔ 你投给了此选项"));
        } else if (active) {
            lore.add(" ");
            lore.add(color("&e▶ 点击投票"));
        }

        Material mat;
        String prefix;
        if (playerVoted) {
            mat = Material.LIME_WOOL;
            prefix = "&a";
        } else if (!active) {
            mat = Material.LIGHT_GRAY_WOOL;
            prefix = "&7";
        } else {
            mat = Material.WHITE_WOOL;
            prefix = "&f";
        }
        return makeItem(mat, prefix + opt.getLabel(), lore);
    }

    private String buildBar(double pct) {
        int filled = (int) Math.round(pct / 10);
        StringBuilder bar = new StringBuilder("&8[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "&a■" : "&7■");
        bar.append("&8]");
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
                send("&e正在记录投票，请稍候...");
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
                send("&c你没有投票权限。");
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
                        send("&c你已经投过票了，不可更改。");
                    } else if (fresh != null && !fresh.isActive()) {
                        send("&c该议题已经结束。");
                    } else {
                        send("&c该选项已失效，请重新打开界面。");
                    }
                    if (fresh != null && isViewing()) populate();
                    return;
                }
                if (fresh == null) {
                    send("&c投票结果刷新失败，请重新打开界面。");
                    return;
                }
                send("&a已投票：&f" + chosen.getLabel());
                if (isViewing()) {
                    populate();
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("投票失败: " + e.getMessage());
            plugin.getPlatformAdapter().runForPlayer(player, () -> {
                votePending = false;
                send("&c投票失败，请稍后再试。");
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
}

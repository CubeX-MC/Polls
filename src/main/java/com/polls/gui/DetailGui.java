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
    private Poll poll;
    private Inventory inv;

    private static final int BACK_SLOT   = 27;
    private static final int MANAGE_SLOT = 35;

    public DetailGui(PollsPlugin plugin, Player player, Poll poll) {
        this.plugin = plugin;
        this.player = player;
        this.poll = poll;
    }

    public void open() {
        inv = Bukkit.createInventory(null, 36,
                LegacyComponentSerializer.legacyAmpersand().deserialize("&8[ &6" + poll.getTitle() + " &8]"));
        populate();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inv);
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

        // 获取玩家已投选项
        int votedOptionId = -1;
        try {
            votedOptionId = plugin.getDatabase().getPlayerVote(poll.getId(), player.getUniqueId());
        } catch (Exception ignored) {}

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
        if (player.hasPermission("polls.admin")) {
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

        if (active) {
            if (playerVoted) {
                lore.add(" ");
                lore.add(color("&a✔ 你已投此选项"));
            } else {
                lore.add(" ");
                lore.add(color("&e▶ 点击投票"));
            }
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
            goBack(); return;
        }

        if (slot == MANAGE_SLOT && player.hasPermission("polls.admin")) {
            HandlerList.unregisterAll(this);
            player.closeInventory();
            plugin.getServer().getGlobalRegionScheduler().run(plugin,
                    t -> new ManageGui(plugin, player, poll).open());
            return;
        }

        // 选项槽位 9-17
        if (slot >= 9 && slot <= 17 && poll.isActive()) {
            int idx = slot - 9;
            List<PollOption> opts = poll.getOptions();
            if (idx >= opts.size()) return;

            try {
                if (plugin.getDatabase().hasVoted(poll.getId(), player.getUniqueId())) {
                    player.sendMessage(color("&c你已经投过票了，不可更改。"));
                    return;
                }
                PollOption chosen = opts.get(idx);
                plugin.getDatabase().castVote(poll.getId(), player.getUniqueId(), chosen.getId());
                chosen.incrementVoteCount();
                player.sendMessage(color("&a已投票：&f" + chosen.getLabel()));
                // 刷新界面
                poll = plugin.getDatabase().loadPoll(poll.getId());
                populate();
            } catch (Exception e) {
                player.sendMessage(color("&c投票失败，请稍后再试。"));
                plugin.getLogger().severe("投票失败: " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())
                && event.getInventory().equals(inv)) {
            HandlerList.unregisterAll(this);
        }
    }

    private void goBack() {
        HandlerList.unregisterAll(this);
        player.closeInventory();
        plugin.getServer().getGlobalRegionScheduler().run(plugin,
                t -> new MainGui(plugin, player).open());
    }
}

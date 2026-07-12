package com.polls.gui;

import com.polls.PollsPlugin;
import com.polls.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
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
 * 引导式提交议题流程：
 * Step 0 - 等待输入标题
 * Step 1 - 等待输入描述
 * Step 2 - 等待输入截止时长
 * Step 3 - GUI：管理选项（添加/完成）
 * Step 4（子流程）- 等待输入选项名称
 * Step 5（子流程）- 等待输入选项描述
 */
public class SubmitFlow implements Listener {

    private static final int MAX_OPTIONS = 9;

    private final PollsPlugin plugin;
    private final Player player;

    private int step = 0;
    private String title;
    private String description;
    private long endsAt;

    // 每个 entry: [label, desc]
    private final List<String[]> options = new ArrayList<>();

    // 子流程暂存
    private String pendingOptionLabel;

    private Inventory optionInv;

    public SubmitFlow(PollsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startStep0();
    }

    // ─── Steps ───

    private void startStep0() {
        step = 0;
        player.closeInventory();
        send("&e请在聊天框输入议题&6标题&e（最多 " + plugin.getConfig().getInt("max-title-length", 40) + " 字）");
        send("&7输入 &ccancel &7取消");
    }

    private void startStep1() {
        step = 1;
        send("&e请输入议题&6描述&e（可为空，直接回车跳过）");
    }

    private void startStep2() {
        step = 2;
        send("&e请输入&6截止时长&e，格式：&f30m &7/ &f12h &7/ &f3d &7/ &f7d");
    }

    private void openOptionManager() {
        step = 3;
        int rows = 4;
        optionInv = Bukkit.createInventory(null, rows * 9,
                LegacyComponentSerializer.legacyAmpersand().deserialize("&8[ &6添加选项 &8]"));
        refreshOptionManager();
        player.openInventory(optionInv);
    }

    private void refreshOptionManager() {
        optionInv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ", List.of());
        for (int i = 0; i < optionInv.getSize(); i++) optionInv.setItem(i, filler);

        // 已添加的选项
        for (int i = 0; i < options.size(); i++) {
            String[] opt = options.get(i);
            List<String> lore = new ArrayList<>();
            if (!opt[1].isBlank()) lore.add(color("&7" + opt[1]));
            lore.add(color("&8点击&c删除"));
            optionInv.setItem(i, makeItem(Material.PAPER, "&f" + opt[0], lore));
        }

        // 添加按钮
        if (options.size() < MAX_OPTIONS) {
            optionInv.setItem(27, makeItem(Material.LIME_DYE, "&a+ 添加选项",
                    List.of(color("&7点击添加新选项"))));
        }

        // 完成按钮（至少2个选项）
        if (options.size() >= 2) {
            optionInv.setItem(35, makeItem(Material.EMERALD, "&a✔ 完成提交",
                    List.of(color("&7共 " + options.size() + " 个选项"), color("&7点击提交议题"))));
        } else {
            optionInv.setItem(35, makeItem(Material.BARRIER, "&c至少需要 2 个选项",
                    List.of(color("&7还需添加 " + (2 - options.size()) + " 个选项"))));
        }
    }

    private void startOptionLabelInput() {
        step = 4;
        player.closeInventory();
        send("&e请输入&6选项名称&e（简短标题）");
        send("&7输入 &cback &7返回");
    }

    private void startOptionDescInput() {
        step = 5;
        send("&e请输入&6选项描述&e（可留空，直接回车跳过）");
    }

    // ─── 事件处理 ───

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancel")) { abort(); return; }

        switch (step) {
            case 0 -> {
                int max = plugin.getConfig().getInt("max-title-length", 40);
                if (msg.isEmpty()) { send("&c标题不能为空"); return; }
                if (msg.length() > max) { send("&c标题过长（最多 " + max + " 字）"); return; }
                title = msg;
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> startStep1());
            }
            case 1 -> {
                int max = plugin.getConfig().getInt("max-description-length", 200);
                if (msg.length() > max) { send("&c描述过长（最多 " + max + " 字）"); return; }
                description = msg;
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> startStep2());
            }
            case 2 -> {
                long millis = DurationParser.parseMillis(msg);
                if (millis <= 0) { send("&c格式有误，示例：30m / 12h / 3d"); return; }
                endsAt = System.currentTimeMillis() + millis;
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> openOptionManager());
            }
            case 4 -> {
                if (msg.equalsIgnoreCase("back")) {
                    plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> openOptionManager());
                    return;
                }
                int max = plugin.getConfig().getInt("max-title-length", 40);
                if (msg.isEmpty()) { send("&c选项名称不能为空"); return; }
                if (msg.length() > max) { send("&c选项名称过长"); return; }
                pendingOptionLabel = msg;
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> startOptionDescInput());
            }
            case 5 -> {
                int max = plugin.getConfig().getInt("max-option-desc-length", 100);
                if (msg.length() > max) { send("&c描述过长（最多 " + max + " 字）"); return; }
                options.add(new String[]{pendingOptionLabel, msg});
                pendingOptionLabel = null;
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> openOptionManager());
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (optionInv == null || !event.getInventory().equals(optionInv)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= optionInv.getSize()) return;

        if (slot == 27) {
            // 添加选项
            startOptionLabelInput();
        } else if (slot == 35 && options.size() >= 2) {
            // 完成提交
            finish();
        } else if (slot < options.size()) {
            // 删除选项
            options.remove(slot);
            refreshOptionManager();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
        if (optionInv == null || !event.getInventory().equals(optionInv)) return;
        // 只有在 step==3 时关闭才视为放弃（step 4/5 是手动关的）
        if (step == 3) {
            abort();
        }
    }

    // ─── 提交 ───

    private void finish() {
        HandlerList.unregisterAll(this);
        player.closeInventory();
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            try {
                int pollId = plugin.getDatabase().insertPoll(
                        player.getUniqueId(), player.getName(),
                        title, description,
                        System.currentTimeMillis(), endsAt);
                for (int i = 0; i < options.size(); i++) {
                    plugin.getDatabase().insertOption(pollId, i, options.get(i)[0], options.get(i)[1]);
                }
                plugin.getPollCache().reload();
                send("&a议题提交成功！使用 &e/polls &a查看。");
            } catch (Exception e) {
                send("&c提交失败，请联系管理员。");
                plugin.getLogger().severe("提交议题失败: " + e.getMessage());
            }
        });
    }

    private void abort() {
        HandlerList.unregisterAll(this);
        player.closeInventory();
        send("&c已取消提交。");
    }

    // ─── 工具 ───

    private void send(String msg) {
        player.sendMessage(color(msg));
    }

    private String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
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

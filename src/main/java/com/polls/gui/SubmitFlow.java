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

    private final PollsPlugin plugin;
    private final Player player;
    private final Listener chatInputListener;
    private final Runnable sessionCancellation;

    private static final int STEP_PROCESSING = -1;

    private volatile int step;
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
        this.sessionCancellation = this::cancelReplacedSession;
        this.chatInputListener = plugin.getPlatformAdapter()
                .createChatInputListener(player, this::consumeChatInput);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(chatInputListener, plugin);
        plugin.registerInputSession(player.getUniqueId(), sessionCancellation);
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
        send("&e请输入议题&6描述&e，输入 &fskip &e跳过");
    }

    private void startStep2() {
        step = 2;
        send("&e请输入&6截止时长&e，格式：&f30m &7/ &f12h &7/ &f3d &7（仅支持单个单位）");
    }

    private void openOptionManager() {
        step = 3;
        int rows = 4;
        optionInv = Bukkit.createInventory(null, rows * 9, color("&8[ &6添加选项 &8]"));
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
        int maxOptions = Math.clamp(plugin.getConfig().getInt("max-options", 9), 2, 9);
        if (options.size() < maxOptions) {
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
        send("&e请输入&6选项描述&e，输入 &fskip &e跳过");
    }

    // ─── 事件处理 ───

    private boolean consumeChatInput(String message) {
        int inputStep;
        synchronized (this) {
            // step 3 是 GUI 阶段，不需要处理聊天输入
            if (step == 3) return false;
            // 首条输入正在回到玩家线程处理时，后续消息也不能泄露到公屏。
            if (step == STEP_PROCESSING) return true;
            inputStep = step;
            step = STEP_PROCESSING;
        }

        plugin.getPlatformAdapter().runForPlayer(player, () -> {
            if (plugin.isInputSessionActive(player.getUniqueId(), sessionCancellation)) {
                handleChatInput(inputStep, message);
            }
        });
        return true;
    }

    private void handleChatInput(int inputStep, String msg) {
        if (msg.equalsIgnoreCase("cancel")) {
            abort(true);
            return;
        }

        switch (inputStep) {
            case 0 -> {
                int max = plugin.getConfig().getInt("max-title-length", 40);
                if (msg.isEmpty()) { retry(inputStep, "&c标题不能为空"); return; }
                if (msg.length() > max) {
                    retry(inputStep, "&c标题过长（最多 " + max + " 字）");
                    return;
                }
                title = msg;
                startStep1();
            }
            case 1 -> {
                String newDescription = msg.equalsIgnoreCase("skip") ? "" : msg;
                int max = plugin.getConfig().getInt("max-description-length", 200);
                if (newDescription.length() > max) {
                    retry(inputStep, "&c描述过长（最多 " + max + " 字）");
                    return;
                }
                description = newDescription;
                startStep2();
            }
            case 2 -> {
                long millis = DurationParser.parseMillis(msg);
                long now = System.currentTimeMillis();
                if (millis <= 0 || millis > Long.MAX_VALUE - now) {
                    retry(inputStep, "&c格式或时长有误，示例：30m / 12h / 3d");
                    return;
                }
                endsAt = now + millis;
                openOptionManager();
            }
            case 4 -> {
                if (msg.equalsIgnoreCase("back")) {
                    openOptionManager();
                    return;
                }
                int max = plugin.getConfig().getInt("max-option-label-length", 40);
                if (msg.isEmpty()) { retry(inputStep, "&c选项名称不能为空"); return; }
                if (msg.length() > max) {
                    retry(inputStep, "&c选项名称过长（最多 " + max + " 字）");
                    return;
                }
                pendingOptionLabel = msg;
                startOptionDescInput();
            }
            case 5 -> {
                String optionDescription = msg.equalsIgnoreCase("skip") ? "" : msg;
                int max = plugin.getConfig().getInt("max-option-desc-length", 100);
                if (optionDescription.length() > max) {
                    retry(inputStep, "&c描述过长（最多 " + max + " 字）");
                    return;
                }
                options.add(new String[]{pendingOptionLabel, optionDescription});
                pendingOptionLabel = null;
                openOptionManager();
            }
            default -> step = inputStep;
        }
    }

    private void retry(int inputStep, String message) {
        step = inputStep;
        send(message);
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
            int maxOptions = Math.clamp(plugin.getConfig().getInt("max-options", 9), 2, 9);
            if (options.size() < maxOptions) {
                startOptionLabelInput();
            }
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
            abort(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            unregisterListeners();
        }
    }

    // ─── 提交 ───

    private void finish() {
        step = STEP_PROCESSING;
        unregisterListeners();
        player.closeInventory();

        List<String[]> optionSnapshot = options.stream()
                .map(option -> new String[]{option[0], option[1]})
                .toList();
        var creator = player.getUniqueId();
        String creatorName = player.getName();
        long createdAt = System.currentTimeMillis();
        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                int pollId = plugin.getDatabase().insertPollWithOptions(
                        creator, creatorName, title, description, createdAt, endsAt, optionSnapshot);
                Poll newPoll = plugin.getDatabase().loadPoll(pollId);
                if (newPoll != null) plugin.getPollCache().addPoll(newPoll);
                plugin.getPlatformAdapter().runForPlayer(player,
                        () -> send("&a议题提交成功！使用 &e/polls &a查看。"));
            } catch (Exception e) {
                plugin.getLogger().severe("提交议题失败: " + e.getMessage());
                plugin.getPlatformAdapter().runForPlayer(player,
                        () -> send("&c提交失败，请联系管理员。"));
            }
        });
    }

    private void abort(boolean closeInventory) {
        step = STEP_PROCESSING;
        unregisterListeners();
        if (closeInventory) player.closeInventory();
        send("&c已取消提交。");
    }

    private void cancelReplacedSession() {
        step = STEP_PROCESSING;
        unregisterListeners();
    }

    private void unregisterListeners() {
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(chatInputListener);
        plugin.clearInputSession(player.getUniqueId(), sessionCancellation);
    }

    // ─── 工具 ───

    private void send(String msg) {
        plugin.getPlatformAdapter().sendMessage(player, msg);
    }
}

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
 * 模板步骤 - GUI：选择普通、出租或贷款模板
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
    private final ChatInputCapture chatInputCapture;
    private final Runnable sessionCancellation;

    private static final int STEP_PROCESSING = -1;
    private static final int STEP_TEMPLATE = 6;
    private static final int TEMPLATE_BACK_SLOT = 22;

    private volatile int step;
    private String title;
    private String description;
    private long endsAt;
    private PollTemplate template = PollTemplate.NORMAL;

    // 每个 entry: [label, desc]
    private final List<String[]> options = new ArrayList<>();

    // 子流程暂存
    private String pendingOptionLabel;

    private Inventory optionInv;
    private Inventory templateInv;

    public SubmitFlow(PollsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.sessionCancellation = this::cancelReplacedSession;
        this.chatInputListener = plugin.getPlatformAdapter()
                .createChatInputListener(player, this::consumeChatInput);
        this.chatInputCapture = new ChatInputCapture(plugin, player, this::consumeChatInput);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(chatInputListener, plugin);
        plugin.registerInputSession(player.getUniqueId(), sessionCancellation);
        startTemplateSelection();
    }

    // ─── Steps ───

    private void startTemplateSelection() {
        List<PollTemplate> templates = configuredTemplates();
        if (!plugin.getConfig().getBoolean("submit-templates.enabled", true)) {
            selectTemplate(PollTemplate.NORMAL);
            return;
        }
        if (templates.size() == 1) {
            selectTemplate(templates.getFirst());
            return;
        }

        step = STEP_TEMPLATE;
        chatInputCapture.stop();
        templateInv = Bukkit.createInventory(null, 27, color(text("submit.templates.title")));
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE,
                text("submit.templates.filler"), List.of());
        for (int slot = 0; slot < templateInv.getSize(); slot++) {
            templateInv.setItem(slot, filler);
        }
        for (PollTemplate available : templates) {
            List<String> lore = new ArrayList<>();
            lore.add(color(text(available.textKey("lore"))));
            if (available.hasPresetOptions()
                    && plugin.getConfig().getBoolean("submit-templates.prefill-options", true)) {
                lore.add(color(text("submit.templates.prefill-lore")));
            }
            lore.add(color(text("submit.templates.select")));
            templateInv.setItem(templateSlot(available), makeItem(templateMaterial(available),
                    text(available.textKey("name")), lore));
        }
        templateInv.setItem(TEMPLATE_BACK_SLOT, makeItem(Material.ARROW,
                text("submit.templates.back"), List.of()));
        player.openInventory(templateInv);
    }

    private void selectTemplate(PollTemplate selected) {
        template = selected;
        options.clear();
        if (selected.hasPresetOptions()
                && plugin.getConfig().getBoolean("submit-templates.prefill-options", true)) {
            options.add(new String[]{text(selected.textKey("options.approve")), ""});
            options.add(new String[]{text(selected.textKey("options.reject")), ""});
        }
        startStep0();
    }

    private void startStep0() {
        step = 0;
        player.closeInventory();
        chatInputCapture.start();
        send(text(promptKey("title"), "max",
                Integer.toString(plugin.getConfig().getInt("max-title-length", 40))));
        send(text("submit.prompt.cancel"));
    }

    private void startStep1() {
        step = 1;
        chatInputCapture.start();
        send(text(promptKey("description")));
    }

    private void startStep2() {
        step = 2;
        chatInputCapture.start();
        send(text("submit.prompt.duration"));
    }

    private void openOptionManager() {
        step = 3;
        chatInputCapture.stop();
        int rows = 4;
        optionInv = Bukkit.createInventory(null, rows * 9, color(text("submit.options.title")));
        refreshOptionManager();
        player.openInventory(optionInv);
    }

    private void refreshOptionManager() {
        optionInv.clear();
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE,
                text("submit.options.filler"), List.of());
        for (int i = 0; i < optionInv.getSize(); i++) optionInv.setItem(i, filler);

        // 已添加的选项
        for (int i = 0; i < options.size(); i++) {
            String[] opt = options.get(i);
            List<String> lore = new ArrayList<>();
            if (!opt[1].isBlank()) {
                lore.add(color(text("submit.options.entry.description", "description", opt[1])));
            }
            lore.add(color(text("submit.options.entry.remove")));
            optionInv.setItem(i, makeItem(Material.PAPER,
                    text("submit.options.entry.name", "option", opt[0]), lore));
        }

        // 添加按钮
        int maxOptions = Math.clamp(plugin.getConfig().getInt("max-options", 9), 2, 9);
        if (options.size() < maxOptions) {
            optionInv.setItem(27, makeItem(Material.LIME_DYE, text("submit.options.add.name"),
                    List.of(color(text("submit.options.add.lore")))));
        }

        // 完成按钮（至少2个选项）
        if (options.size() >= 2) {
            optionInv.setItem(35, makeItem(Material.EMERALD, text("submit.options.finish.name"),
                    List.of(
                            color(text("submit.options.finish.count", "count",
                                    Integer.toString(options.size()))),
                            color(text("submit.options.finish.lore"))
                    )));
        } else {
            optionInv.setItem(35, makeItem(Material.BARRIER,
                    text("submit.options.minimum.name", "minimum", "2"),
                    List.of(color(text("submit.options.minimum.remaining", "remaining",
                            Integer.toString(2 - options.size()))))));
        }
    }

    private void startOptionLabelInput() {
        step = 4;
        player.closeInventory();
        chatInputCapture.start();
        send(text("submit.prompt.option-label"));
        send(text("submit.prompt.back"));
    }

    private void startOptionDescInput() {
        step = 5;
        chatInputCapture.start();
        send(text("submit.prompt.option-description"));
    }

    // ─── 事件处理 ───

    private boolean consumeChatInput(String message) {
        int inputStep;
        synchronized (this) {
            // GUI 阶段不拦截玩家的普通聊天。
            if (step == 3 || step == STEP_TEMPLATE) return false;
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
                if (msg.isEmpty()) { retry(inputStep, text("submit.error.title-empty")); return; }
                if (msg.length() > max) {
                    retry(inputStep, text("submit.error.title-too-long", "max", Integer.toString(max)));
                    return;
                }
                title = msg;
                startStep1();
            }
            case 1 -> {
                String newDescription = msg.equalsIgnoreCase("skip") ? "" : msg;
                int max = plugin.getConfig().getInt("max-description-length", 200);
                if (newDescription.length() > max) {
                    retry(inputStep, text("submit.error.description-too-long", "max",
                            Integer.toString(max)));
                    return;
                }
                description = newDescription;
                startStep2();
            }
            case 2 -> {
                long millis = DurationParser.parseMillis(msg);
                long now = System.currentTimeMillis();
                if (millis <= 0 || millis > Long.MAX_VALUE - now) {
                    retry(inputStep, text("submit.error.duration-invalid"));
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
                if (msg.isEmpty()) { retry(inputStep, text("submit.error.option-label-empty")); return; }
                if (msg.length() > max) {
                    retry(inputStep, text("submit.error.option-label-too-long", "max",
                            Integer.toString(max)));
                    return;
                }
                pendingOptionLabel = msg;
                startOptionDescInput();
            }
            case 5 -> {
                String optionDescription = msg.equalsIgnoreCase("skip") ? "" : msg;
                int max = plugin.getConfig().getInt("max-option-desc-length", 100);
                if (optionDescription.length() > max) {
                    retry(inputStep, text("submit.error.description-too-long", "max",
                            Integer.toString(max)));
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

        if (templateInv != null && event.getInventory().equals(templateInv)) {
            handleTemplateClick(event);
            return;
        }
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
        if (templateInv != null && event.getInventory().equals(templateInv)) {
            if (step == STEP_TEMPLATE) abort(false);
            return;
        }
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
                        () -> send(text("submit.success")));
            } catch (Exception e) {
                plugin.getLogger().severe(text("log.submit_failed", "error", e.getMessage()));
                plugin.getPlatformAdapter().runForPlayer(player,
                        () -> send(text("submit.error.failed")));
            }
        });
    }

    private void abort(boolean closeInventory) {
        step = STEP_PROCESSING;
        unregisterListeners();
        if (closeInventory) player.closeInventory();
        send(text("submit.cancelled"));
    }

    private void cancelReplacedSession() {
        step = STEP_PROCESSING;
        unregisterListeners();
    }

    private void unregisterListeners() {
        chatInputCapture.stop();
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(chatInputListener);
        plugin.clearInputSession(player.getUniqueId(), sessionCancellation);
    }

    // ─── 工具 ───

    private void send(String msg) {
        plugin.getPlatformAdapter().sendMessage(player, msg);
    }

    private String text(String key, String... replacements) {
        return plugin.getLanguageManager().text(key, replacements);
    }

    private String promptKey(String field) {
        return template == PollTemplate.NORMAL
                ? "submit.prompt." + field
                : template.textKey("prompt." + field);
    }

    private List<PollTemplate> configuredTemplates() {
        return PollTemplate.configured(plugin.getConfig().getStringList("submit-templates.available"));
    }

    private void handleTemplateClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= templateInv.getSize()) return;

        if (slot == TEMPLATE_BACK_SLOT) {
            returnToMain();
            return;
        }
        for (PollTemplate available : configuredTemplates()) {
            if (slot == templateSlot(available)) {
                selectTemplate(available);
                return;
            }
        }
    }

    private void returnToMain() {
        step = STEP_PROCESSING;
        unregisterListeners();
        player.closeInventory();
        new MainGui(plugin, player).open();
    }

    private int templateSlot(PollTemplate selected) {
        return switch (selected) {
            case NORMAL -> 10;
            case RENTAL -> 13;
            case LOAN -> 16;
        };
    }

    private Material templateMaterial(PollTemplate selected) {
        return switch (selected) {
            case NORMAL -> Material.WRITABLE_BOOK;
            case RENTAL -> Material.CHEST;
            case LOAN -> Material.GOLD_INGOT;
        };
    }
}

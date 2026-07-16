package com.polls.platform;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.function.Predicate;

/**
 * 平台适配器接口，屏蔽不同服务端实现差异
 */
public interface PlatformAdapter {

    /**
     * 向玩家发送消息（支持 & 颜色代码）
     * @param player 目标玩家
     * @param legacyText 传统格式文本（&a, &7 等）
     */
    void sendMessage(Player player, String legacyText);

    /**
     * 创建适用于当前平台的聊天输入监听器。
     * 处理器返回 true 时，该聊天消息会被插件消费且不会广播。
     *
     * @param player 目标玩家
     * @param inputHandler 聊天输入处理器
     * @return 尚未注册的 Bukkit 监听器
     */
    Listener createChatInputListener(Player player, Predicate<String> inputHandler);

    /**
     * 在玩家所属线程执行任务
     * @param player 目标玩家
     * @param task 要执行的任务
     */
    void runForPlayer(Player player, Runnable task);

    /**
     * 在服务端全局线程执行任务
     * @param task 要执行的任务
     */
    void runGlobal(Runnable task);

    /**
     * 在服务端全局线程周期执行任务
     * @param task 要执行的任务
     * @param initialDelayTicks 首次执行前的延迟（tick）
     * @param periodTicks 执行间隔（tick）
     */
    void runRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    /**
     * 异步执行任务
     * @param task 要执行的任务
     */
    void runAsync(Runnable task);

    /**
     * 获取当前平台名称
     * @return "Paper/Folia" 或 "Bukkit/Spigot"
     */
    String getPlatformName();
}

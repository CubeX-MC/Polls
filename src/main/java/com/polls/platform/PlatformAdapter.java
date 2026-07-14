package com.polls.platform;

import org.bukkit.entity.Player;

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
     * 异步执行任务
     * @param task 要执行的任务
     */
    void runAsync(Runnable task);
    
    /**
     * 获取当前平台名称
     * @return "Paper" 或 "Bukkit/Spigot/Folia"
     */
    String getPlatformName();
}

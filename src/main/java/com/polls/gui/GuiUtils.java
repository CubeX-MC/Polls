package com.polls.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuiUtils {

    private GuiUtils() {}

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** 将长文本按 maxLen 个字符折行，返回带颜色前缀的 lore 行列表 */
    public static List<String> wrapText(String text, int maxLen, String colorPrefix) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxLen) {
            lines.add(color(colorPrefix + text.substring(i, Math.min(i + maxLen, text.length()))));
        }
        return lines;
    }
}

package com.polls.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    public static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
        List<Component> loreComp = new ArrayList<>();
        for (String l : lore) loreComp.add(LegacyComponentSerializer.legacySection().deserialize(l));
        meta.lore(loreComp);
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

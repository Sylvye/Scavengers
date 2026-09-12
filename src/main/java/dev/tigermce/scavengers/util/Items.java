package dev.tigermce.scavengers.util;

import dev.tigermce.scavengers.model.HuntItem;
import dev.tigermce.scavengers.model.MatchMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private Items() {}

    public static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack button(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name, color));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(text(line, NamedTextColor.GRAY));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack display(HuntItem target, boolean glint, List<Component> extraLore) {
        ItemStack stack = target.item();
        stack.setAmount(Math.min(target.amount(), stack.getMaxStackSize()));
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(extraLore);
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(glint);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    public static int count(ItemStack[] contents, HuntItem target, MatchMode mode) {
        int total = 0;
        ItemStack wanted = target.item();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) continue;
            boolean matches = mode == MatchMode.MATERIAL
                    ? stack.getType() == wanted.getType()
                    : stack.isSimilar(wanted);
            if (matches) total += stack.getAmount();
        }
        return total;
    }
}

package dev.tigermce.scavengers.model;

import org.bukkit.inventory.ItemStack;

public record HuntItem(ItemStack item, int amount) {
    public HuntItem {
        item = item.clone();
        item.setAmount(1);
        amount = Math.max(1, amount);
    }

    @Override public ItemStack item() { return item.clone(); }
}

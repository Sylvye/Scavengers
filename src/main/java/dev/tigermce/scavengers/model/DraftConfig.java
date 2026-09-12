package dev.tigermce.scavengers.model;

import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class DraftConfig {
    public final List<HuntItem> items = new ArrayList<>();
    public final EnumMap<PrizeTier, List<ItemStack>> prizes = new EnumMap<>(PrizeTier.class);
    public MatchMode matchMode = MatchMode.MATERIAL;
    public int stagesPerHunt;
    public boolean announceMilestones;
    public boolean consumeRequiredItems;
    public DraftConfig() { for (PrizeTier tier : PrizeTier.values()) prizes.put(tier, new ArrayList<>()); }
}

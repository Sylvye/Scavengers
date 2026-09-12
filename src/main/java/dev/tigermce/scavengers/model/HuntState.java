package dev.tigermce.scavengers.model;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class HuntState {
    public boolean active;
    public long startedAt;
    public List<HuntItem> sequence = new ArrayList<>();
    public final EnumMap<PrizeTier, List<ItemStack>> prizes = new EnumMap<>(PrizeTier.class);
    public MatchMode matchMode = MatchMode.MATERIAL;
    public final Map<UUID, Integer> progress = new HashMap<>();
    public UUID winner;
    public final List<UUID> finishers = new ArrayList<>();

    public HuntState() { for (PrizeTier tier : PrizeTier.values()) prizes.put(tier, new ArrayList<>()); }

    public int progress(UUID player) { return progress.getOrDefault(player, 0); }
}

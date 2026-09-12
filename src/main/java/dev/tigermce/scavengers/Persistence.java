package dev.tigermce.scavengers;

import dev.tigermce.scavengers.model.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class Persistence {
    private final File folder;
    public Persistence(File folder) { this.folder = folder; }

    public record Loaded(DraftConfig draft, HuntState state, Map<UUID, List<ItemStack>> claims) {}

    public Loaded load() {
        DraftConfig draft = new DraftConfig();
        HuntState state = new HuntState();
        Map<UUID, List<ItemStack>> claims = new HashMap<>();
        File file = new File(folder, "data.yml");
        if (!file.exists()) return new Loaded(draft, state, claims);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        draft.matchMode = enumValue(MatchMode.class, y.getString("draft.match-mode"), MatchMode.MATERIAL);
        draft.sequenceMode = enumValue(SequenceMode.class, y.getString("draft.sequence-mode"), SequenceMode.ORDERED);
        draft.items.addAll(readTargets(y, "draft.items"));
        for (PrizeTier tier : PrizeTier.values()) draft.prizes.get(tier).addAll(readStacks(y, "draft.prizes." + tier.name().toLowerCase()));
        if (draft.prizes.get(PrizeTier.FIRST).isEmpty()) draft.prizes.get(PrizeTier.FIRST).addAll(readStacks(y, "draft.rewards"));

        state.active = y.getBoolean("hunt.active");
        state.startedAt = y.getLong("hunt.started-at");
        state.matchMode = enumValue(MatchMode.class, y.getString("hunt.match-mode"), MatchMode.MATERIAL);
        state.sequence = readTargets(y, "hunt.sequence");
        for (PrizeTier tier : PrizeTier.values()) state.prizes.get(tier).addAll(readStacks(y, "hunt.prizes." + tier.name().toLowerCase()));
        if (state.prizes.get(PrizeTier.FIRST).isEmpty()) state.prizes.get(PrizeTier.FIRST).addAll(readStacks(y, "hunt.rewards"));
        String winner = y.getString("hunt.winner");
        if (winner != null) state.winner = parseUuid(winner);
        for (String value : y.getStringList("hunt.finishers")) { UUID id = parseUuid(value); if (id != null) state.finishers.add(id); }
        if (state.finishers.isEmpty() && state.winner != null) state.finishers.add(state.winner);
        ConfigurationSection progress = y.getConfigurationSection("hunt.progress");
        if (progress != null) for (String key : progress.getKeys(false)) {
            UUID id = parseUuid(key);
            if (id != null) state.progress.put(id, progress.getInt(key));
        }
        ConfigurationSection claimSection = y.getConfigurationSection("claims");
        if (claimSection != null) for (String key : claimSection.getKeys(false)) {
            UUID id = parseUuid(key);
            if (id != null) claims.put(id, readStacks(y, "claims." + key));
        }
        return new Loaded(draft, state, claims);
    }

    public void save(DraftConfig draft, HuntState state, Map<UUID, List<ItemStack>> claims) {
        folder.mkdirs();
        YamlConfiguration y = new YamlConfiguration();
        y.set("draft.match-mode", draft.matchMode.name());
        y.set("draft.sequence-mode", draft.sequenceMode.name());
        writeTargets(y, "draft.items", draft.items);
        for (PrizeTier tier : PrizeTier.values()) y.set("draft.prizes." + tier.name().toLowerCase(), cloneStacks(draft.prizes.get(tier)));
        y.set("hunt.active", state.active);
        y.set("hunt.started-at", state.startedAt);
        y.set("hunt.match-mode", state.matchMode.name());
        writeTargets(y, "hunt.sequence", state.sequence);
        for (PrizeTier tier : PrizeTier.values()) y.set("hunt.prizes." + tier.name().toLowerCase(), cloneStacks(state.prizes.get(tier)));
        y.set("hunt.winner", state.winner == null ? null : state.winner.toString());
        y.set("hunt.finishers", state.finishers.stream().map(UUID::toString).toList());
        for (var entry : state.progress.entrySet()) y.set("hunt.progress." + entry.getKey(), entry.getValue());
        for (var entry : claims.entrySet()) y.set("claims." + entry.getKey(), cloneStacks(entry.getValue()));
        Path target = new File(folder, "data.yml").toPath();
        Path temp = new File(folder, "data.yml.tmp").toPath();
        try {
            y.save(temp.toFile());
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalStateException("Could not save Scavengers data", e); }
    }

    private static void writeTargets(YamlConfiguration y, String path, List<HuntItem> items) {
        for (int i = 0; i < items.size(); i++) {
            y.set(path + "." + i + ".item", items.get(i).item());
            y.set(path + "." + i + ".amount", items.get(i).amount());
        }
    }
    private static List<HuntItem> readTargets(YamlConfiguration y, String path) {
        List<HuntItem> result = new ArrayList<>();
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s == null) return result;
        s.getKeys(false).stream().sorted(Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
            ItemStack item = s.getItemStack(key + ".item");
            if (item != null && !item.getType().isAir()) result.add(new HuntItem(item, s.getInt(key + ".amount", 1)));
        });
        return result;
    }
    private static List<ItemStack> readStacks(YamlConfiguration y, String path) {
        List<ItemStack> result = new ArrayList<>();
        for (Object value : y.getList(path, List.of())) if (value instanceof ItemStack stack) result.add(stack.clone());
        return result;
    }
    private static List<ItemStack> cloneStacks(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::clone).toList(); }
    private static UUID parseUuid(String value) { try { return UUID.fromString(value); } catch (IllegalArgumentException e) { return null; } }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return value == null ? fallback : Enum.valueOf(type, value); } catch (IllegalArgumentException e) { return fallback; }
    }
}

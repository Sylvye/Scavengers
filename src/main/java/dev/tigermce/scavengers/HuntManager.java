package dev.tigermce.scavengers;

import dev.tigermce.scavengers.model.*;
import dev.tigermce.scavengers.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class HuntManager {
    private final ScavengersPlugin plugin;
    private final Persistence persistence;
    public final DraftConfig draft;
    public final HuntState state;
    public final Map<UUID, List<ItemStack>> claims;
    private final Map<UUID, Integer> pendingSubmissions = new HashMap<>();

    public HuntManager(ScavengersPlugin plugin, Persistence persistence, Persistence.Loaded loaded) {
        this.plugin = plugin; this.persistence = persistence;
        this.draft = loaded.draft(); this.state = loaded.state(); this.claims = loaded.claims();
    }

    public boolean start() {
        if (startError() != null) return false;
        state.active = true;
        state.startedAt = System.currentTimeMillis();
        state.winner = null;
        state.finishers.clear();
        state.progress.clear();
        state.matchMode = draft.matchMode;
        state.stagesPerHunt = draft.stagesPerHunt;
        state.announceMilestones = draft.announceMilestones;
        state.consumeRequiredItems = draft.consumeRequiredItems;
        state.sequence = randomSequence(draft.items, draft.stagesPerHunt, new Random());
        pendingSubmissions.clear();
        for (PrizeTier tier : PrizeTier.values()) state.prizes.put(tier, cloneStacks(draft.prizes.get(tier)));
        for (Player player : Bukkit.getOnlinePlayers()) state.progress.put(player.getUniqueId(), 0);
        save();
        Bukkit.broadcast(Items.text("A scavenger hunt has begun!", NamedTextColor.GOLD));
        for (Player player : Bukkit.getOnlinePlayers()) { announceCurrent(player); check(player); }
        return true;
    }

    public boolean stop() {
        if (!state.active) return false;
        state.active = false;
        pendingSubmissions.clear();
        save();
        Bukkit.broadcast(Items.text("The scavenger hunt was stopped by an administrator.", NamedTextColor.RED));
        return true;
    }

    public void join(Player player) {
        if (!state.active) return;
        state.progress.putIfAbsent(player.getUniqueId(), 0);
        save();
        announceCurrent(player);
        check(player);
    }

    public void check(Player player) {
        if (!state.active || !player.isOnline()) return;
        int index = state.progress(player.getUniqueId());
        boolean changed = false;
        while (state.active && index < state.sequence.size()) {
            HuntItem target = state.sequence.get(index);
            int found = Items.count(player.getInventory().getContents(), target, state.matchMode);
            if (found < target.amount()) {
                pendingSubmissions.remove(player.getUniqueId());
                break;
            }
            if (state.consumeRequiredItems) {
                if (!Objects.equals(pendingSubmissions.get(player.getUniqueId()), index)) {
                    pendingSubmissions.put(player.getUniqueId(), index);
                    Component prompt = Items.text("You have the items for stage " + (index + 1) + ". ", NamedTextColor.YELLOW)
                            .append(Items.text("[Submit Items]", NamedTextColor.GREEN)
                                    .clickEvent(ClickEvent.runCommand("/scav submit " + index)));
                    player.sendMessage(prompt);
                }
                break;
            }
            completeStage(player, index);
            changed = true;
            index++;
            if (!state.active || index == state.sequence.size()) return;
        }
        if (changed) save();
    }

    public boolean submit(Player player, Integer expectedStage) {
        if (!state.active || !state.consumeRequiredItems) {
            player.sendMessage(Items.text("There is no item submission waiting for you.", NamedTextColor.RED));
            return false;
        }
        int index = state.progress(player.getUniqueId());
        Integer pending = pendingSubmissions.get(player.getUniqueId());
        if (pending == null || pending != index || (expectedStage != null && expectedStage != index)) {
            player.sendMessage(Items.text("That item submission is no longer valid.", NamedTextColor.RED));
            return false;
        }
        HuntItem target = state.sequence.get(index);
        if (!Items.remove(player.getInventory(), target, state.matchMode)) {
            pendingSubmissions.remove(player.getUniqueId());
            player.sendMessage(Items.text("You no longer have enough items to submit for this stage.", NamedTextColor.RED));
            return false;
        }
        pendingSubmissions.remove(player.getUniqueId());
        completeStage(player, index);
        if (state.active) check(player);
        return true;
    }

    private void completeStage(Player player, int index) {
        int completed = index + 1;
        state.progress.put(player.getUniqueId(), completed);
        player.sendMessage(Items.text("✓ Objective complete!", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f);
        if (state.announceMilestones) Bukkit.broadcast(Component.empty().append(player.displayName())
                .append(Items.text(" has completed stage " + completed + "/" + state.sequence.size(), NamedTextColor.YELLOW)));
        if (completed == state.sequence.size()) finish(player);
        else announceCurrent(player);
        save();
    }

    public String startError() {
        if (state.active) return "A hunt is already active.";
        if (draft.items.isEmpty()) return "Add at least one hunt pool item first.";
        if (draft.stagesPerHunt < 1 || draft.stagesPerHunt > draft.items.size())
            return "Set stages per hunt between 1 and " + draft.items.size() + ".";
        return null;
    }

    public boolean containsEquivalent(HuntItem candidate, MatchMode mode) {
        return draft.items.stream().anyMatch(existing -> Items.sameTarget(existing, candidate, mode));
    }

    public boolean hasDuplicates(MatchMode mode) {
        for (int i = 0; i < draft.items.size(); i++) {
            for (int j = i + 1; j < draft.items.size(); j++) {
                if (Items.sameTarget(draft.items.get(i), draft.items.get(j), mode)) return true;
            }
        }
        return false;
    }

    static List<HuntItem> randomSequence(List<HuntItem> pool, int count, Random random) {
        List<HuntItem> result = new ArrayList<>(pool);
        Collections.shuffle(result, random);
        return new ArrayList<>(result.subList(0, count));
    }

    private void finish(Player player) {
        if (!state.active || state.finishers.contains(player.getUniqueId())) return;
        state.finishers.add(player.getUniqueId());
        int place = state.finishers.size();
        PrizeTier tier = PrizeTier.forPlace(place);
        if (place == 1) state.winner = player.getUniqueId();
        List<ItemStack> prize = prizesFor(state, place);
        if (!prize.isEmpty()) claims.put(player.getUniqueId(), prize);
        save();
        Component message = Component.text("★ ", NamedTextColor.GOLD)
                .append(player.displayName())
                .append(Items.text(place <= 3 ? " finished in " + ordinal(place) + " place!" : " completed the scavenger hunt!", NamedTextColor.YELLOW));
        Bukkit.broadcast(message);
        for (Player online : Bukkit.getOnlinePlayers()) online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (claims.containsKey(player.getUniqueId())) player.sendMessage(Items.text(
                place <= 3 && !state.prizes.get(tier).isEmpty()
                        ? "Your completion and " + tier.label() + " prizes are ready. Use /scav to claim them."
                        : "Your completion prize is ready. Use /scav to claim it.", NamedTextColor.AQUA));
    }

    public void announceCurrent(Player player) {
        if (!state.active) return;
        int index = state.progress(player.getUniqueId());
        if (index >= state.sequence.size()) return;
        HuntItem target = state.sequence.get(index);
        ItemStack stack = target.item();
        Component name = stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                ? stack.getItemMeta().displayName() : Component.translatable(stack.getType().translationKey());
        Component message = Items.text("Objective " + (index + 1) + "/" + state.sequence.size() + ": ", NamedTextColor.YELLOW)
                .append(name.colorIfAbsent(NamedTextColor.AQUA).hoverEvent(stack.asHoverEvent()))
                .append(Items.text(" ×" + target.amount() + " ", NamedTextColor.WHITE))
                .append(Items.text("[View]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/scav")));
        player.sendMessage(message);
    }

    public boolean claim(Player player) {
        List<ItemStack> rewards = claims.remove(player.getUniqueId());
        if (rewards == null) return false;
        for (ItemStack reward : rewards) {
            var overflow = player.getInventory().addItem(reward.clone());
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        save();
        player.sendMessage(Items.text("Reward claimed!", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    public void save() { persistence.save(draft, state, claims); }
    static List<ItemStack> prizesFor(HuntState state, int place) {
        List<ItemStack> result = new ArrayList<>(cloneStacks(state.prizes.get(PrizeTier.COMPLETION)));
        PrizeTier tier = PrizeTier.forPlace(place);
        if (tier != PrizeTier.COMPLETION) result.addAll(cloneStacks(state.prizes.get(tier)));
        return result;
    }
    private static List<ItemStack> cloneStacks(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::clone).toList(); }
    private static String ordinal(int place) { return switch (place) { case 1 -> "1st"; case 2 -> "2nd"; case 3 -> "3rd"; default -> place + "th"; }; }
}

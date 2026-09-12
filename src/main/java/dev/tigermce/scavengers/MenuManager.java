package dev.tigermce.scavengers;

import dev.tigermce.scavengers.model.*;
import dev.tigermce.scavengers.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.*;

public final class MenuManager implements Listener {
    private static final int PAGE_SIZE = 45;
    private final ScavengersPlugin plugin;
    private final HuntManager hunts;
    private final Map<UUID, CountRequest> countRequests = new HashMap<>();

    public MenuManager(ScavengersPlugin plugin, HuntManager hunts) { this.plugin = plugin; this.hunts = hunts; }

    private sealed interface Menu extends InventoryHolder permits Dashboard, Settings, Editor, Rewards, Progress, Confirm {
        @Override default Inventory getInventory() { return null; }
    }
    private record Dashboard() implements Menu {}
    private record Settings() implements Menu {}
    private record Editor(int page) implements Menu {}
    private record Rewards(PrizeTier tier, int page) implements Menu {}
    private record Progress(int page) implements Menu {}
    private record Confirm(Action action, int returnPage) implements Menu {}
    private record CountRequest(int index, int returnPage) {}
    private enum Action { START, STOP, CLEAR }

    public void openDashboard(Player player) {
        Inventory inv = Bukkit.createInventory(new Dashboard(), 27, title("Scavengers • Admin"));
        fill(inv);
        inv.setItem(10, Items.button(Material.COMPASS, "Edit Hunt Items", NamedTextColor.AQUA,
                hunts.draft.items.size() + " configured", "Click to edit the shared sequence"));
        int prizeCount = hunts.draft.prizes.values().stream().mapToInt(List::size).sum();
        inv.setItem(11, Items.button(Material.CHEST, "Edit Prizes", NamedTextColor.GOLD,
                prizeCount + " prize stacks", "1st, 2nd, 3rd, and completion"));
        inv.setItem(12, Items.button(Material.COMPARATOR, "Settings", NamedTextColor.LIGHT_PURPLE,
                "Match: " + hunts.draft.matchMode, "Sequence: " + hunts.draft.sequenceMode,
                "Left-click: match mode", "Right-click: sequence mode"));
        inv.setItem(14, Items.button(Material.BOOK, "View Progress", NamedTextColor.GREEN,
                hunts.state.active ? "Hunt is active" : "No active hunt"));
        inv.setItem(16, hunts.state.active
                ? Items.button(Material.BARRIER, "Stop Hunt", NamedTextColor.RED, "Requires confirmation")
                : Items.button(Material.LIME_DYE, "Start Hunt", NamedTextColor.GREEN,
                    hunts.draft.items.isEmpty() ? "Add at least one item first" : "Starts for all online players", "Requires confirmation"));
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new Settings(), 27, title("Scavengers • Settings"));
        fill(inv);
        inv.setItem(11, Items.button(hunts.draft.matchMode == MatchMode.MATERIAL ? Material.GRASS_BLOCK : Material.ENCHANTED_BOOK,
                "Match Mode: " + hunts.draft.matchMode, NamedTextColor.AQUA,
                hunts.draft.matchMode == MatchMode.MATERIAL ? "Matches item material only" : "Matches all item data/components",
                "Click to toggle"));
        inv.setItem(15, Items.button(hunts.draft.sequenceMode == SequenceMode.ORDERED ? Material.REPEATER : Material.ENDER_EYE,
                "Sequence: " + hunts.draft.sequenceMode, NamedTextColor.LIGHT_PURPLE,
                hunts.draft.sequenceMode == SequenceMode.ORDERED ? "Uses the configured GUI order" : "Shuffles once for everyone at start",
                "Click to toggle"));
        inv.setItem(22, Items.button(Material.ARROW, "Back", NamedTextColor.YELLOW));
        player.openInventory(inv);
    }

    public void openEditor(Player player, int requestedPage) {
        int page = boundedPage(requestedPage, hunts.draft.items.size());
        Inventory inv = Bukkit.createInventory(new Editor(page), 54, title("Hunt Items • " + (page + 1)));
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= hunts.draft.items.size()) break;
            HuntItem target = hunts.draft.items.get(index);
            inv.setItem(slot, Items.display(target, false, List.of(
                    Items.text("Required: " + target.amount(), NamedTextColor.YELLOW),
                    Items.text("Right-click: set amount", NamedTextColor.AQUA),
                    Items.text("Shift-left/right: move earlier/later", NamedTextColor.GRAY),
                    Items.text("Left-click: remove", NamedTextColor.RED))));
        }
        controls(inv, page, hunts.draft.items.size());
        inv.setItem(49, Items.button(Material.HOPPER, "Add From Your Inventory", NamedTextColor.GREEN,
                "Click any item in your inventory below", "A copy is appended to the sequence"));
        inv.setItem(50, Items.button(Material.TNT, "Clear All", NamedTextColor.RED, "Requires confirmation"));
        inv.setItem(53, Items.button(Material.ARROW, "Back", NamedTextColor.YELLOW));
        player.openInventory(inv);
    }

    public void openRewards(Player player, PrizeTier tier, int requestedPage) {
        List<ItemStack> prizes = hunts.draft.prizes.get(tier);
        int page = boundedPage(requestedPage, prizes.size());
        Inventory inv = Bukkit.createInventory(new Rewards(tier, page), 54, title(tier.label() + " Prizes • " + (page + 1)));
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= prizes.size()) break;
            ItemStack stack = prizes.get(index).clone();
            ItemMeta meta = stack.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Items.text("Click to remove", NamedTextColor.RED));
            meta.lore(lore); stack.setItemMeta(meta); inv.setItem(slot, stack);
        }
        controls(inv, page, prizes.size());
        inv.setItem(46, Items.button(Material.GOLD_INGOT, "1st Place", NamedTextColor.GOLD));
        inv.setItem(47, Items.button(Material.IRON_INGOT, "2nd Place", NamedTextColor.GRAY));
        inv.setItem(48, Items.button(Material.COPPER_INGOT, "3rd Place", NamedTextColor.YELLOW));
        inv.setItem(49, Items.button(Material.HOPPER, "Add Reward Items", NamedTextColor.GREEN,
                "Click any item in your inventory below", "A full copy is added"));
        inv.setItem(50, Items.button(Material.EMERALD, "Completion", NamedTextColor.GREEN));
        inv.setItem(53, Items.button(Material.ARROW, "Back", NamedTextColor.YELLOW));
        player.openInventory(inv);
    }

    public void openProgress(Player player, int requestedPage) {
        List<HuntItem> sequence = hunts.state.sequence;
        int page = boundedPage(requestedPage, sequence.size());
        Inventory inv = Bukkit.createInventory(new Progress(page), 54, title("Scavenger Hunt • " + (page + 1)));
        int progress = hunts.state.progress(player.getUniqueId());
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot;
            if (index >= sequence.size()) break;
            if (index < progress) inv.setItem(slot, Items.display(sequence.get(index), true,
                    List.of(Items.text("✓ Collected", NamedTextColor.GREEN))));
            else if (hunts.state.active && index == progress) {
                HuntItem target = sequence.get(index);
                int found = Items.count(player.getInventory().getContents(), target, hunts.state.matchMode);
                inv.setItem(slot, Items.display(target, false, List.of(
                        Items.text("Active objective", NamedTextColor.YELLOW),
                        Items.text("Carrying: " + found + "/" + target.amount(), NamedTextColor.AQUA))));
            } else inv.setItem(slot, Items.button(Material.BLACK_STAINED_GLASS_PANE, "???", NamedTextColor.DARK_GRAY,
                    "Complete earlier objectives to reveal"));
        }
        controls(inv, page, sequence.size());
        String elapsed = hunts.state.startedAt == 0 ? "—" : formatDuration(System.currentTimeMillis() - hunts.state.startedAt);
        inv.setItem(49, Items.button(Material.CLOCK, hunts.state.active ? "Hunt In Progress" : "Hunt Finished", hunts.state.active ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                "Progress: " + progress + "/" + sequence.size(), "Elapsed: " + elapsed,
                "Match mode: " + hunts.state.matchMode));
        if (hunts.claims.containsKey(player.getUniqueId())) inv.setItem(50,
                Items.button(Material.CHEST_MINECART, "Claim Winner Reward", NamedTextColor.GOLD, "Click to claim once"));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, Action action, int returnPage) {
        int size = action == Action.START ? 54 : 27;
        Inventory inv = Bukkit.createInventory(new Confirm(action, returnPage), size, title("Confirm • " + action.name()));
        fill(inv);
        int confirmSlot = action == Action.START ? 47 : 11;
        int cancelSlot = action == Action.START ? 51 : 15;
        inv.setItem(confirmSlot, Items.button(Material.LIME_CONCRETE, "Confirm", NamedTextColor.GREEN));
        inv.setItem(cancelSlot, Items.button(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED));
        if (action == Action.START) {
            for (int i = 0; i < Math.min(27, hunts.draft.items.size()); i++) {
                HuntItem target = hunts.draft.items.get(i);
                inv.setItem(9 + i, Items.display(target, false, List.of(Items.text("Required: " + target.amount(), NamedTextColor.YELLOW))));
            }
            inv.setItem(4, Items.button(Material.MAP, "Sequence Preview", NamedTextColor.GOLD,
                    hunts.draft.items.size() + " objectives", "Match: " + hunts.draft.matchMode,
                    "Sequence: " + hunts.draft.sequenceMode,
                    hunts.draft.items.size() > 27 ? "Showing the first 27" : "Review before starting"));
        }
        player.openInventory(inv);
    }

    private void openCount(Player player, int index, int returnPage) {
        countRequests.put(player.getUniqueId(), new CountRequest(index, returnPage));
        player.closeInventory();
        player.sendMessage(Items.text("Type the required amount in chat (1–1,000,000), or type 'cancel'.", NamedTextColor.YELLOW));
    }

    @EventHandler public void onChat(AsyncChatEvent event) {
        CountRequest request = countRequests.remove(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyCountInput(event.getPlayer(), request, input));
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (menu instanceof Dashboard) dashboardClick(player, raw, event.getClick());
        else if (menu instanceof Settings) settingsClick(player, raw);
        else if (menu instanceof Editor editor) editorClick(player, editor, raw, event);
        else if (menu instanceof Rewards rewards) rewardsClick(player, rewards, raw, event);
        else if (menu instanceof Progress progress) progressClick(player, progress, raw);
        else if (menu instanceof Confirm confirm) confirmClick(player, confirm, raw);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    private void dashboardClick(Player player, int slot, ClickType click) {
        switch (slot) {
            case 10 -> openEditor(player, 0);
            case 11 -> openRewards(player, PrizeTier.FIRST, 0);
            case 12 -> openSettings(player);
            case 14 -> openProgress(player, 0);
            case 16 -> openConfirm(player, hunts.state.active ? Action.STOP : Action.START, 0);
        }
    }

    private void settingsClick(Player player, int slot) {
        if (slot == 11) hunts.draft.matchMode = hunts.draft.matchMode == MatchMode.MATERIAL ? MatchMode.EXACT : MatchMode.MATERIAL;
        else if (slot == 15) hunts.draft.sequenceMode = hunts.draft.sequenceMode == SequenceMode.ORDERED ? SequenceMode.SHUFFLED : SequenceMode.ORDERED;
        else if (slot == 22) { openDashboard(player); return; }
        else return;
        hunts.save(); openSettings(player); tick(player);
    }

    private void editorClick(Player player, Editor menu, int raw, InventoryClickEvent event) {
        if (raw >= 0 && raw < PAGE_SIZE) {
            int index = menu.page * PAGE_SIZE + raw;
            if (index >= hunts.draft.items.size()) return;
            if (event.isRightClick() && !event.getClick().isShiftClick()) { openCount(player, index, menu.page); return; }
            if (event.getClick() == ClickType.SHIFT_LEFT && index > 0) Collections.swap(hunts.draft.items, index, index - 1);
            else if (event.getClick() == ClickType.SHIFT_RIGHT && index + 1 < hunts.draft.items.size()) Collections.swap(hunts.draft.items, index, index + 1);
            else hunts.draft.items.remove(index);
            hunts.save(); openEditor(player, menu.page); tick(player); return;
        }
        if (raw >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                hunts.draft.items.add(new HuntItem(clicked, clicked.getAmount())); hunts.save();
                openEditor(player, (hunts.draft.items.size() - 1) / PAGE_SIZE); tick(player);
            }
            return;
        }
        if (raw == 45) openEditor(player, menu.page - 1);
        else if (raw == 50) openConfirm(player, Action.CLEAR, menu.page);
        else if (raw == 52) openEditor(player, menu.page + 1);
        else if (raw == 53) openDashboard(player);
    }

    private void rewardsClick(Player player, Rewards menu, int raw, InventoryClickEvent event) {
        if (raw >= 0 && raw < PAGE_SIZE) {
            int index = menu.page * PAGE_SIZE + raw;
            List<ItemStack> prizes = hunts.draft.prizes.get(menu.tier);
            if (index < prizes.size()) { prizes.remove(index); hunts.save(); openRewards(player, menu.tier, menu.page); tick(player); }
            return;
        }
        if (raw >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            List<ItemStack> prizes = hunts.draft.prizes.get(menu.tier);
            if (clicked != null && !clicked.getType().isAir()) { prizes.add(clicked.clone()); hunts.save(); openRewards(player, menu.tier, (prizes.size() - 1) / PAGE_SIZE); tick(player); }
            return;
        }
        if (raw == 45) openRewards(player, menu.tier, menu.page - 1);
        else if (raw == 46) openRewards(player, PrizeTier.FIRST, 0);
        else if (raw == 47) openRewards(player, PrizeTier.SECOND, 0);
        else if (raw == 48) openRewards(player, PrizeTier.THIRD, 0);
        else if (raw == 50) openRewards(player, PrizeTier.COMPLETION, 0);
        else if (raw == 52) openRewards(player, menu.tier, menu.page + 1);
        else if (raw == 53) openDashboard(player);
    }

    private void progressClick(Player player, Progress menu, int slot) {
        if (slot == 45) openProgress(player, menu.page - 1);
        else if (slot == 52) openProgress(player, menu.page + 1);
        else if (slot == 50 && hunts.claim(player)) openProgress(player, menu.page);
    }

    private void confirmClick(Player player, Confirm menu, int slot) {
        int confirmSlot = menu.action == Action.START ? 47 : 11;
        int cancelSlot = menu.action == Action.START ? 51 : 15;
        if (slot == cancelSlot) { if (menu.action == Action.CLEAR) openEditor(player, menu.returnPage); else openDashboard(player); return; }
        if (slot != confirmSlot) return;
        switch (menu.action) {
            case START -> {
                if (!hunts.start()) player.sendMessage(Items.text(hunts.draft.items.isEmpty() ? "Add at least one hunt item first." : "A hunt is already active.", NamedTextColor.RED));
                openDashboard(player);
            }
            case STOP -> { hunts.stop(); openDashboard(player); }
            case CLEAR -> { hunts.draft.items.clear(); hunts.save(); openEditor(player, 0); }
        }
    }

    private void applyCountInput(Player player, CountRequest request, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(Items.text("Amount change cancelled.", NamedTextColor.GRAY));
            openEditor(player, request.returnPage);
            return;
        }
        try {
            int amount = Integer.parseInt(input);
            if (amount < 1 || amount > 1_000_000) throw new NumberFormatException();
            if (request.index >= hunts.draft.items.size()) { openEditor(player, request.returnPage); return; }
            HuntItem old = hunts.draft.items.get(request.index);
            hunts.draft.items.set(request.index, new HuntItem(old.item(), amount));
            hunts.save(); openEditor(player, request.returnPage); tick(player);
        } catch (NumberFormatException e) {
            player.sendMessage(Items.text("Enter a whole number from 1 to 1,000,000.", NamedTextColor.RED));
            countRequests.put(player.getUniqueId(), request);
        }
    }

    private void controls(Inventory inv, int page, int count) {
        for (int i = 45; i < 54; i++) inv.setItem(i, Items.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        if (page > 0) inv.setItem(45, Items.button(Material.ARROW, "Previous Page", NamedTextColor.YELLOW));
        if ((page + 1) * PAGE_SIZE < count) inv.setItem(52, Items.button(Material.ARROW, "Next Page", NamedTextColor.YELLOW));
    }
    private void fill(Inventory inv) { for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, Items.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY)); }
    private Component title(String value) { return Items.text(value, NamedTextColor.DARK_AQUA); }
    private int boundedPage(int page, int count) { return Math.max(0, Math.min(page, Math.max(0, (count - 1) / PAGE_SIZE))); }
    private String formatDuration(long millis) { Duration d = Duration.ofMillis(Math.max(0, millis)); return "%02d:%02d:%02d".formatted(d.toHours(), d.toMinutesPart(), d.toSecondsPart()); }
    private void tick(Player player) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f); }
}

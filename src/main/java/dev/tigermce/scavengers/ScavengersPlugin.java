package dev.tigermce.scavengers;

import dev.tigermce.scavengers.model.PrizeTier;
import dev.tigermce.scavengers.util.Items;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScavengersPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private HuntManager hunts;
    private MenuManager menus;

    @Override public void onEnable() {
        Persistence persistence = new Persistence(getDataFolder(), getLogger());
        hunts = new HuntManager(this, persistence, persistence.load());
        menus = new MenuManager(this, hunts);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        PluginCommand command = getCommand("scavengers");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(hunts::check), 20L, 20L);
        if (hunts.state.active) getLogger().info("Resumed an active scavenger hunt with " + hunts.state.sequence.size() + " objectives.");
    }

    @Override public void onDisable() { if (hunts != null) hunts.save(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("stop") && args[1].equalsIgnoreCase("--force")) {
            if (!sender.hasPermission("scavengers.admin")) {
                sender.sendMessage("You do not have permission to stop Scavengers.");
            } else if (!hunts.stop()) sender.sendMessage("There is no active scavenger hunt.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /" + label + " stop --force");
            return true;
        }
        if (!player.hasPermission("scavengers.use")) {
            player.sendMessage(Items.text("You do not have permission to use Scavengers.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            menus.openProgress(player, 0);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
            if (!player.hasPermission("scavengers.admin")) {
                player.sendMessage(Items.text("You do not have permission to configure Scavengers.", NamedTextColor.RED));
                return true;
            }
            menus.openDashboard(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("rewards")) {
            menus.openRewardPreview(player, PrizeTier.COMPLETION, 0);
            return true;
        }
        if ((args.length == 1 || args.length == 2) && args[0].equalsIgnoreCase("submit")) {
            Integer expectedStage = null;
            if (args.length == 2) {
                try { expectedStage = Integer.parseInt(args[1]); }
                catch (NumberFormatException ignored) {
                    player.sendMessage(Items.text("That item submission is invalid.", NamedTextColor.RED));
                    return true;
                }
            }
            hunts.submit(player, expectedStage);
            return true;
        }
        player.sendMessage(Items.text("Usage: /" + label + " [rewards|submit|settings|stop --force]", NamedTextColor.RED));
        return true;
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            java.util.List<String> options = new java.util.ArrayList<>(java.util.List.of("rewards", "submit"));
            if (sender.hasPermission("scavengers.admin")) options.addAll(java.util.List.of("settings", "stop"));
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop") && sender.hasPermission("scavengers.admin") && "--force".startsWith(args[1])) return java.util.List.of("--force");
        return java.util.List.of();
    }

    private void delayedCheck(Player player) {
        Bukkit.getScheduler().runTask(this, () -> { if (player.isOnline()) hunts.check(player); });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> hunts.join(event.getPlayer()));
    }
    @EventHandler public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) delayedCheck(player); }
    @EventHandler public void onClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player player) delayedCheck(player); }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getWhoClicked() instanceof Player player) delayedCheck(player); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { delayedCheck(event.getPlayer()); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) { delayedCheck(event.getPlayer()); }
    @EventHandler public void onConsume(PlayerItemConsumeEvent event) { delayedCheck(event.getPlayer()); }
    @EventHandler public void onBreak(PlayerItemBreakEvent event) { delayedCheck(event.getPlayer()); }
}

package ru.lostone.refontsearch.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.lostone.refontsearch.DemorganData;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.DemorganManager;

public class DemorganMovementListener implements Listener {

    private final RefontSearch plugin;

    public DemorganMovementListener(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!DemorganManager.isInDemorgan(player.getName())) {
            return;
        }

        if (player.hasPermission("refontsearch.demorgan.bypass")) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (from.distance(to) < 0.1) {
            return;
        }

        Location demorganLocation = getDemorganSpawnLocation();
        if (demorganLocation == null) {
            return;
        }

        double radius = plugin.getConfig().getDouble("demorgan.radius", 30.0);
        double distance = to.distance(demorganLocation);

        if (!to.getWorld().equals(demorganLocation.getWorld())) {
            event.setCancelled(true);
            returnToDemorgan(player, demorganLocation);
            return;
        }

        if (distance > radius) {
            event.setCancelled(true);
            returnToDemorgan(player, demorganLocation);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!DemorganManager.isInDemorgan(player.getName())) {
            return;
        }

        if (player.hasPermission("refontsearch.demorgan.bypass")) {
            return;
        }

        Location demorganLocation = getDemorganSpawnLocation();
        if (demorganLocation == null) {
            return;
        }

        Location targetLocation = event.getTo();
        if (targetLocation == null) {
            return;
        }

        double radius = plugin.getConfig().getDouble("demorgan.radius", 30.0);
        double distance = targetLocation.distance(demorganLocation);

        if (!targetLocation.getWorld().equals(demorganLocation.getWorld()) || distance > radius) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.demorgan.leave", "§c§l⚔ §7Вы не можете покинуть демогрант!")));

            showEscapeEffects(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!DemorganManager.isInDemorgan(player.getName())) {
            return;
        }

        DemorganData data = DemorganManager.getDemorganData(player.getName());
        if (data == null) {
            return;
        }

        if (data.isExpired()) {
            DemorganManager.releaseFromDemorgan(player.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.demorgan.expired", "§a§l⚔ §7Ваш срок в демогрант истек! Вы освобождены.")));
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location demorganLocation = getDemorganSpawnLocation();
            if (demorganLocation != null) {
                player.teleport(demorganLocation);
            }
        }, 20L);

        String message = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.demorgan.rejoin", "§c§l⚔ §7Вы все еще находитесь в демогрант!"))
                + "\n§7Причина: §f" + data.getReason()
                + "\n§7Оставшееся время: §e" + data.getFormattedRemainingTime()
                + "\n§7Администратор: §a" + data.getAdministrator();

        player.sendMessage(message);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DemorganManager.showDemorganEffects(player);
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (DemorganManager.isInDemorgan(player.getName())) {
            DemorganData data = DemorganManager.getDemorganData(player.getName());
            if (data != null) {
            }
        }
    }

    private void returnToDemorgan(Player player, Location demorganLocation) {
        player.teleport(demorganLocation);

        String message = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.demorgan.leave", "§c§l⚔ §7Вы не можете покинуть демогрант!"));
        player.sendMessage(message);

        showEscapeEffects(player);

        DemorganData data = DemorganManager.getDemorganData(player.getName());
        if (data != null) {
            double distance = player.getLocation().distance(demorganLocation);
        }
    }

    private Location getDemorganSpawnLocation() {
        String locStr = plugin.getConfig().getString("demorgan.location.spawn", "");
        if (locStr.isEmpty()) {
            return null;
        }
        return parseLocation(locStr);
    }

    private Location parseLocation(String locStr) {
        try {
            String[] parts = locStr.split(";");
            if (parts.length != 4) return null;

            return new Location(
                    plugin.getServer().getWorld(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3])
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void showEscapeEffects(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.escape.title", "§c§lПОБЕГ НЕВОЗМОЖЕН!"));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.escape.subtitle", "§7Охрана вернула вас в камеру"));

        int fadeIn = plugin.getConfig().getInt("demorgan.effects.escape.fadeIn", 10);
        int stay = plugin.getConfig().getInt("demorgan.effects.escape.stay", 40);
        int fadeOut = plugin.getConfig().getInt("demorgan.effects.escape.fadeOut", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        String sound = plugin.getConfig().getString("demorgan.effects.escape.sound", "entity.enderman.teleport");
        float volume = (float) plugin.getConfig().getDouble("demorgan.effects.escape.soundVolume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("demorgan.effects.escape.soundPitch", 0.5);

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            player.playSound(player.getLocation(), "entity.enderman.teleport", 1.0f, 0.5f);
        }
    }
}
package ru.lostone.refontsearch.listener;

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

        // Проверяем, находится ли игрок в демогрант
        if (!DemorganManager.isInDemorgan(player.getName())) {
            return;
        }

        // Проверяем права на обход демогрант
        if (player.hasPermission("refontsearch.demorgan.bypass")) {
            return;
        }

        Location demorganLocation = getDemorganSpawnLocation();
        if (demorganLocation == null) {
            return;
        }

        Location playerLocation = player.getLocation();

        // Проверяем, находится ли игрок в том же мире
        if (!playerLocation.getWorld().equals(demorganLocation.getWorld())) {
            returnToDemorgan(player, demorganLocation);
            return;
        }

        // Проверяем расстояние от центра демогрант
        double radius = plugin.getConfig().getDouble("demorgan.radius", 25.0);
        double distance = playerLocation.distance(demorganLocation);

        if (distance > radius) {
            returnToDemorgan(player, demorganLocation);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Проверяем, находится ли игрок в демогрант
        if (!DemorganManager.isInDemorgan(player.getName())) {
            return;
        }

        // Проверяем права на обход демогрант
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

        // Проверяем, пытается ли игрок телепортироваться за пределы демогрант
        if (!targetLocation.getWorld().equals(demorganLocation.getWorld())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.demorgan.leave", "§c§l⚔ §7Вы не можете покинуть демогрант!")));
            return;
        }

        double radius = plugin.getConfig().getDouble("demorgan.radius", 25.0);
        double distance = targetLocation.distance(demorganLocation);

        if (distance > radius) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.demorgan.leave", "§c§l⚔ §7Вы не можете покинуть демогрант!")));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Проверяем, находится ли игрок в демогрант
        if (!DemorganManager.isInDemorgan(player.getName())) {
            return;
        }

        // Получаем данные демогрант
        DemorganData data = DemorganManager.getDemorganData(player.getName());
        if (data == null) {
            return;
        }

        // Проверяем, не истекло ли время
        if (data.isExpired()) {
            DemorganManager.releaseFromDemorgan(player.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.demorgan.expired", "§a§l⚔ §7Ваш срок в демогрант истек! Вы освобождены.")));
            return;
        }

        // Телепортируем в демогрант
        Location demorganLocation = getDemorganSpawnLocation();
        if (demorganLocation != null) {
            player.teleport(demorganLocation);
        }

        // Уведомляем о возвращении в демогрант
        String message = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.demorgan.rejoin", "§c§l⚔ §7Вы все еще находитесь в демогрант!"))
                + "\n§7Причина: §f" + data.getReason()
                + "\n§7Оставшееся время: §e" + data.getFormattedRemainingTime()
                + "\n§7Администратор: §a" + data.getAdministrator();

        player.sendMessage(message);

        // Эффекты при возвращении
        showDemorganEffects(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Если игрок в демогрант, логируем выход
        if (DemorganManager.isInDemorgan(player.getName())) {
            DemorganData data = DemorganManager.getDemorganData(player.getName());
            if (data != null) {
                plugin.getLogger().info("Игрок " + player.getName() + " вышел из игры, находясь в демогрант. " +
                        "Оставшееся время: " + data.getFormattedRemainingTime() +
                        ". Причина: " + data.getReason());
            }
        }
    }

    /**
     * Возвращает игрока в демогрант
     */
    private void returnToDemorgan(Player player, Location demorganLocation) {
        player.teleport(demorganLocation);

        String message = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.demorgan.leave", "§c§l⚔ §7Вы не можете покинуть демогрант!"));
        player.sendMessage(message);

        // Показываем эффекты побега
        showEscapeEffects(player);

        // Логируем попытку побега
        DemorganData data = DemorganManager.getDemorganData(player.getName());
        if (data != null) {
            plugin.getLogger().info("Игрок " + player.getName() + " пытался сбежать из демогрант. " +
                    "Администратор: " + data.getAdministrator() +
                    ". Причина: " + data.getReason());
        }
    }

    /**
     * Получает локацию спавна демогрант
     */
    private Location getDemorganSpawnLocation() {
        String locStr = plugin.getConfig().getString("demorgan.location.spawn", "");
        if (locStr.isEmpty()) {
            return null;
        }
        return parseLocation(locStr);
    }

    /**
     * Парсит строку локации
     */
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

    /**
     * Показывает эффекты при входе в демогрант
     */
    private void showDemorganEffects(Player player) {
        // Title и Subtitle
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.rejoin.title", "§c§lДЕМОГРАН"));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.rejoin.subtitle", "§7Вы находитесь в административной тюрьме"));

        int fadeIn = plugin.getConfig().getInt("demorgan.effects.rejoin.fadeIn", 10);
        int stay = plugin.getConfig().getInt("demorgan.effects.rejoin.stay", 60);
        int fadeOut = plugin.getConfig().getInt("demorgan.effects.rejoin.fadeOut", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        // Звук
        String sound = plugin.getConfig().getString("demorgan.effects.rejoin.sound", "block.iron_door.close");
        float volume = (float) plugin.getConfig().getDouble("demorgan.effects.rejoin.soundVolume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("demorgan.effects.rejoin.soundPitch", 1.0);

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // Если звук не найден, используем стандартный
            player.playSound(player.getLocation(), "block.iron_door.close", 1.0f, 1.0f);
        }
    }

    /**
     * Показывает эффекты при попытке побега
     */
    private void showEscapeEffects(Player player) {
        // Title и Subtitle
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.escape.title", "§c§lПОБЕГ НЕВОЗМОЖЕН!"));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.escape.subtitle", "§7Охрана вернула вас в камеру"));

        int fadeIn = plugin.getConfig().getInt("demorgan.effects.escape.fadeIn", 10);
        int stay = plugin.getConfig().getInt("demorgan.effects.escape.stay", 40);
        int fadeOut = plugin.getConfig().getInt("demorgan.effects.escape.fadeOut", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        // Звук
        String sound = plugin.getConfig().getString("demorgan.effects.escape.sound", "entity.enderman.teleport");
        float volume = (float) plugin.getConfig().getDouble("demorgan.effects.escape.soundVolume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("demorgan.effects.escape.soundPitch", 0.5);

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // Если звук не найден, используем стандартный
            player.playSound(player.getLocation(), "entity.enderman.teleport", 1.0f, 0.5f);
        }
    }
}
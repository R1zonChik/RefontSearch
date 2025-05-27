package ru.lostone.refontsearch.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.JailManager;

public class PlayerMovementListener implements Listener {

    private RefontSearch plugin;

    public PlayerMovementListener(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Проверяем, находится ли игрок в тюрьме
        if (JailManager.isJailed(player.getUniqueId())) {
            Location jailLocation = plugin.getJailLocation();

            if (jailLocation == null) {
                return; // Если точки тюрьмы нет, то не ограничиваем перемещение
            }

            // Получаем радиус тюремной зоны из конфига или используем стандартный
            double jailRadius = plugin.getConfig().getDouble("jail.radius", 10.0);

            // Если игрок покинул зону тюрьмы, телепортируем его обратно
            if (event.getTo().distance(jailLocation) > jailRadius) {
                event.setCancelled(true);
                player.teleport(jailLocation);
                player.sendMessage("§c§l⚔ §7Вы не можете покинуть тюрьму!");
            }
        }
    }
}
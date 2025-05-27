package ru.lostone.refontsearch.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.JailManager;

public class PlayerJoinListener implements Listener {

    private final RefontSearch plugin;

    public PlayerJoinListener(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Проверяем, числится ли игрок в тюрьме
        if (JailManager.isJailed(player.getUniqueId())) {
            // Телепортируем с задержкой, чтобы игрок успел полностью войти
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.getJailLocation() != null) {
                        player.teleport(plugin.getJailLocation());
                        player.setGameMode(GameMode.ADVENTURE);
                        JailManager.startJailTimer(player);

                        String msg = plugin.getConfig().getString("messages.jail.rejoin",
                                "§c§l⚔ §7Вы все еще находитесь в тюрьме!");
                        player.sendMessage(msg);
                    }
                }
            }.runTaskLater(plugin, 20L); // Задержка в 1 секунду
        }
    }
}
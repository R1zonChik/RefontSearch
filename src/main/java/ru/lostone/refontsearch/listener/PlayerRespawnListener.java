package ru.lostone.refontsearch.listener;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.JailManager;

import java.util.UUID;

public class PlayerRespawnListener implements Listener {

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        // Если игрок числится в тюрьме согласно данным JailManager:
        if (JailManager.isJailed(playerId)) {
            // Устанавливаем точку респауна – точка тюрьмы (start)
            Location jailLoc = RefontSearch.getInstance().getJailLocation();
            if (jailLoc != null) {
                event.setRespawnLocation(jailLoc);
            }
            // Принудительно переводим игрока в режим ADVENTURE (gm2)
            player.setGameMode(GameMode.ADVENTURE);
            // Через 10 тактов (~500 мс) запускаем эффект возрождения (анимация + звук)
            new BukkitRunnable() {
                @Override
                public void run() {
                    String title = RefontSearch.getInstance().getConfig().getString("jail.respawn.title", "§a§lВы пробудились!");
                    String subtitle = RefontSearch.getInstance().getConfig().getString("jail.respawn.subtitle", "§7Вы попали в тюрьму...");
                    int fadeIn = RefontSearch.getInstance().getConfig().getInt("jail.respawn.fadeIn", 10);
                    int stay = RefontSearch.getInstance().getConfig().getInt("jail.respawn.stay", 40);
                    int fadeOut = RefontSearch.getInstance().getConfig().getInt("jail.respawn.fadeOut", 10);
                    player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                    String soundName = RefontSearch.getInstance().getConfig().getString("jail.respawn.sound", "block.anvil.land");
                    float volume = (float) RefontSearch.getInstance().getConfig().getDouble("jail.respawn.soundVolume", 1.0);
                    float pitch = (float) RefontSearch.getInstance().getConfig().getDouble("jail.respawn.soundPitch", 1.0);
                    player.playSound(player.getLocation(), soundName, volume, pitch);
                }
            }.runTaskLater(RefontSearch.getInstance(), 10L);

            // Определяем длительность анимации: fadeIn + stay + fadeOut
            int totalAnimTicks = RefontSearch.getInstance().getConfig().getInt("jail.respawn.fadeIn", 10)
                    + RefontSearch.getInstance().getConfig().getInt("jail.respawn.stay", 40)
                    + RefontSearch.getInstance().getConfig().getInt("jail.respawn.fadeOut", 10);
            // После завершения анимации запускаем таймер отсчёта оставшегося срока
            new BukkitRunnable() {
                @Override
                public void run() {
                    JailManager.startJailTimer(player);
                }
            }.runTaskLater(RefontSearch.getInstance(), totalAnimTicks);
        }
    }
}
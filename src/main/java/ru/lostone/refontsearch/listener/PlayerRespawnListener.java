package ru.lostone.refontsearch.listener;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.model.Jail;

import java.util.UUID;

public class PlayerRespawnListener implements Listener {

    private final RefontSearch plugin;

    public PlayerRespawnListener(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();

        // Если игрок числится в тюрьме согласно данным JailManager:
        if (JailManager.isJailed(playerId)) {
            // Пробуем получить конкретную тюрьму игрока
            Jail playerJail = plugin.getJailsManager().getPlayerJail(playerId);

            // Определяем точку телепортации
            Location jailLoc = null;

            if (playerJail != null && playerJail.getJailLocation() != null) {
                // Используем локацию из конкретной тюрьмы игрока
                jailLoc = playerJail.getJailLocation();
            } else {
                // Если нет конкретной тюрьмы - используем глобальную или случайную
                jailLoc = plugin.getJailLocation();

                // Если и глобальная не настроена, ищем случайную
                if (jailLoc == null) {
                    Jail randomJail = plugin.getJailsManager().getRandomJail();
                    if (randomJail != null && randomJail.getJailLocation() != null) {
                        jailLoc = randomJail.getJailLocation();
                        plugin.getJailsManager().setPlayerJail(playerId, randomJail.getName());
                    }
                }
            }

            // Если удалось определить точку перемещения
            if (jailLoc != null) {
                final Location finalJailLoc = jailLoc;

                // Устанавливаем точку респауна
                event.setRespawnLocation(finalJailLoc);

                // Принудительно переводим игрока в режим ADVENTURE (gm2)
                player.setGameMode(GameMode.ADVENTURE);

                // Добавляем эффект слепоты сразу после респавна
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Добавляем эффект темноты (как будто он потерял сознание)
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                    }
                }.runTaskLater(plugin, 1L);

                // Через 5 тиков повторно телепортируем игрока (на случай, если другие плагины перехватывают телепортацию)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.teleport(finalJailLoc);

                        String title = plugin.getConfig().getString("jail.respawn.title", "§a§lВы пробудились!");
                        String subtitle = plugin.getConfig().getString("jail.respawn.subtitle", "§7Вы находитесь в тюрьме...");
                        int fadeIn = plugin.getConfig().getInt("jail.respawn.fadeIn", 10);
                        int stay = plugin.getConfig().getInt("jail.respawn.stay", 60);
                        int fadeOut = plugin.getConfig().getInt("jail.respawn.fadeOut", 20);

                        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

                        String soundName = plugin.getConfig().getString("jail.respawn.sound", "block.anvil.land");
                        float volume = (float) plugin.getConfig().getDouble("jail.respawn.soundVolume", 1.0);
                        float pitch = (float) plugin.getConfig().getDouble("jail.respawn.soundPitch", 1.0);

                        player.playSound(player.getLocation(), soundName, volume, pitch);
                    }
                }.runTaskLater(plugin, 20L);  // Увеличиваем задержку до 1 секунды (20 тиков)

                // Запускаем таймер отсчёта оставшегося срока
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        JailManager.startJailTimer(player);
                    }
                }.runTaskLater(plugin, 95L); // примерно 4.75 секунды (учитывая fadeIn + stay)
            }
        }
    }
}
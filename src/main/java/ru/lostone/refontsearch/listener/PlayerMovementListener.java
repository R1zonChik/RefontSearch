package ru.lostone.refontsearch.listener;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.model.Jail;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerMovementListener implements Listener {

    private final RefontSearch plugin;
    // Карта для отслеживания последней телепортации игроков
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    // Задержка между телепортациями (в миллисекундах)
    private static final long TELEPORT_COOLDOWN = 3000;

    public PlayerMovementListener(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Если координаты блока не изменились - пропускаем проверку
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Проверяем, находится ли игрок в тюрьме
        if (JailManager.isJailed(playerId)) {
            // Получаем время последней телепортации
            long now = System.currentTimeMillis();
            Long lastTP = lastTeleport.get(playerId);

            // Если с момента последней телепортации прошло меньше задержки, пропускаем
            if (lastTP != null && now - lastTP < TELEPORT_COOLDOWN) {
                return;
            }

            // Пробуем получить конкретную тюрьму игрока
            Jail playerJail = plugin.getJailsManager().getPlayerJail(playerId);

            Location jailLocation;
            double jailRadius;

            if (playerJail != null) {
                // Используем данные конкретной тюрьмы
                jailLocation = playerJail.getJailLocation();
                jailRadius = playerJail.getRadius();
            } else {
                // Используем глобальные настройки тюрьмы
                jailLocation = plugin.getJailLocation();
                if (jailLocation == null) {
                    return; // Если точки тюрьмы нет, то не ограничиваем перемещение
                }
                jailRadius = plugin.getConfig().getDouble("jail.radius", 10.0);
            }

            // Если игрок покинул зону тюрьмы, телепортируем его обратно с эффектами
            if (event.getTo().distance(jailLocation) > jailRadius) {
                // Отменяем текущее движение
                event.setCancelled(true);

                // Обновляем время последней телепортации
                lastTeleport.put(playerId, now);

                // Добавляем эффект темноты (как будто он потерял сознание)
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));

                // Воспроизводим звук
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.5f);

                // Телепортируем игрока обратно с небольшой задержкой
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.teleport(jailLocation);

                        // Показываем титул "пробуждения"
                        String title = plugin.getConfig().getString("jail.escape.title", "§c§lВы пробудились!");
                        String subtitle = plugin.getConfig().getString("jail.escape.subtitle", "§7Охрана вернула вас в камеру...");
                        player.sendTitle(title, subtitle, 10, 40, 20);

                        // Сообщаем игроку
                        player.sendMessage("§c§l⚔ §7Вы не можете покинуть тюрьму!");

                        // Убираем слепоту через некоторое время
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.removePotionEffect(PotionEffectType.BLINDNESS);

                                // Воспроизводим звук пробуждения
                                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.5f, 1.2f);
                            }
                        }.runTaskLater(plugin, 30L); // через 1.5 секунды
                    }
                }.runTaskLater(plugin, 20L); // через 1 секунду
            }
        }
    }
}
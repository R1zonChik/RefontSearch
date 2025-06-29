package ru.lostone.refontsearch.listener;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.manager.WantedManager;
import ru.lostone.refontsearch.model.Jail;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BatonMechanicListener implements Listener {

    private final Map<UUID, Long> lastDamage = new HashMap<>();
    private static final long COOLDOWN = 3000; // 3 секунды между применениями дубинки

    private final RefontSearch plugin;

    public BatonMechanicListener(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player))
            return;

        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();

        // Проверяем, прошел ли кулдаун для удара дубинкой
        long now = System.currentTimeMillis();
        if (lastDamage.containsKey(damager.getUniqueId())) {
            long lastTime = lastDamage.get(damager.getUniqueId());
            if (now - lastTime < COOLDOWN) {
                event.setCancelled(true);
                return;
            }
        }

        // Проверяем предмет в руке
        ItemStack item = damager.getInventory().getItemInMainHand();
        if (item == null) return;

        // Вариант 1: Проверяем по имени (в случае если дубинка названа)
        boolean isBaton = false;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().equals("§6Полицейская Дубинка")) {
            isBaton = true;
        }
        // Вариант 2: Палка из конфига (если не задано кастомное имя)
        else if (item.getType() == Material.STICK && damager.hasPermission("refontsearch.police")) {
            isBaton = true;
        }

        if (isBaton) {
            // Обновляем время последнего использования
            lastDamage.put(damager.getUniqueId(), now);

            // Эффекты дубинки
            target.sendMessage(ChatColor.RED + "Вас ударила полицейская дубинка!");
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1));

            // Проверяем, находится ли жертва в розыске
            if (WantedManager.isWanted(target.getUniqueId()) && damager.hasPermission("refontsearch.police")) {
                // Получаем данные розыска
                WantedData data = WantedManager.getWanted(target.getUniqueId());
                int stars = data.getStars();

                // Определяем время ареста по звездам
                int jailTimeSeconds = plugin.getConfig().getInt("jailTimers." + stars, 900);
                long jailEndTime = System.currentTimeMillis() + (jailTimeSeconds * 1000L);

                // Сажаем игрока в тюрьму
                JailManager.jailPlayer(target.getUniqueId(), jailEndTime);

                // Снимаем розыск
                WantedManager.removeWanted(target.getUniqueId());

                // Сообщаем атакующему об успешном аресте
                String arrestMsg = plugin.getConfig()
                        .getString("messages.arrested.officer", "§a§l⚔ §7Вы арестовали игрока {player} на {time} секунд.")
                        .replace("{player}", target.getName())
                        .replace("{time}", String.valueOf(jailTimeSeconds));
                damager.sendMessage(arrestMsg);

                // Сообщаем жертве о аресте
                String reasonText = "розыска";
                String targetMsg = plugin.getConfig()
                        .getString("messages.arrested.target", "§c§l⚔ §7Вы были арестованы на {time} секунд из-за {reason}!")
                        .replace("{time}", String.valueOf(jailTimeSeconds))
                        .replace("{reason}", reasonText);
                target.sendMessage(targetMsg);

                // Публичное сообщение (если включено)
                if (plugin.getConfig().getBoolean("arrest.broadcast", true)) {
                    String broadcastMsg = plugin.getConfig()
                            .getString("messages.arrested.broadcast", "§c§l⚔ §7Игрок {player} был {type}арестован офицером {officer}!")
                            .replace("{player}", target.getName())
                            .replace("{officer}", damager.getName())
                            .replace("{type}", "");

                    plugin.getServer().broadcastMessage(broadcastMsg);
                }

                // Выбираем и телепортируем в тюрьму после короткой задержки
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Пробуем получить конкретную тюрьму или случайную
                        Location jailLoc = null;

                        // Используем случайную тюрьму
                        Jail randomJail = plugin.getJailsManager().getRandomJail();
                        if (randomJail != null && randomJail.getJailLocation() != null) {
                            jailLoc = randomJail.getJailLocation();
                            plugin.getJailsManager().setPlayerJail(target.getUniqueId(), randomJail.getName());
                        } else {
                            // Если нет случайной тюрьмы, используем глобальную
                            jailLoc = plugin.getJailLocation();
                        }

                        // Телепортируем игрока если нашли точку
                        if (jailLoc != null) {
                            target.teleport(jailLoc);
                            target.setGameMode(GameMode.ADVENTURE);

                            // Запускаем таймер отсчета
                            JailManager.startJailTimer(target);
                        }
                    }
                }.runTaskLater(plugin, 5L);
            }
        }
    }
}
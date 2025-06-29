package ru.lostone.refontsearch.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;
import ru.lostone.refontsearch.manager.WantedManager;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.model.Jail;

public class PlayerDeathListener implements Listener {

    // Добавить в класс PlayerDeathListener.java:
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        Player killer = killed.getKiller();

        // Проверяем, находится ли убитый в розыске
        if (WantedManager.isWanted(killed.getUniqueId())) {
            // Проверяем, является ли убийца полицейским
            if (killer != null && killer.hasPermission("refontsearch.police")) {
                // Определям время ареста по звездам розыска
                WantedData data = WantedManager.getWanted(killed.getUniqueId());
                int stars = data.getStars();
                int jailTimeSeconds = RefontSearch.getInstance().getConfig().getInt("jailTimers." + stars, 900);

                // Вычисляем время окончания ареста
                long jailEndTime = System.currentTimeMillis() + (jailTimeSeconds * 1000L);

                // Сажаем в тюрьму
                JailManager.jailPlayer(killed.getUniqueId(), jailEndTime);

                // Выбираем тюрьму
                Jail jail = RefontSearch.getInstance().getJailsManager().getRandomJail();
                if (jail != null && jail.getJailLocation() != null) {
                    RefontSearch.getInstance().getJailsManager().setPlayerJail(killed.getUniqueId(), jail.getName());
                }

                // Снимаем розыск
                WantedManager.removeWanted(killed.getUniqueId());

                // Сообщение для полицейского
                killer.sendMessage("§a§l⚔ §7Вы арестовали игрока " + killed.getName() + " на " + jailTimeSeconds + " секунд.");

                // Сообщение для арестованного (отправится после респавна)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        killed.sendMessage("§c§l⚔ §7Вы были арестованы на " + jailTimeSeconds + " секунд из-за розыска!");
                    }
                }.runTaskLater(RefontSearch.getInstance(), 40L);
            }
        }
    }
}
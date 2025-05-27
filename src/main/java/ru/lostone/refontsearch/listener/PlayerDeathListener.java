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

public class PlayerDeathListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Проверяем, активен ли арест при убийстве в конфиге
        if (!RefontSearch.getInstance().getConfig().getBoolean("arrest.onKill", true)) {
            return; // Если отключен - выходим из метода
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Если убийца отсутствует или не имеет право (permission "refontsearch.police") – не тюремить
        if (killer == null || !killer.hasPermission("refontsearch.police"))
            return;

        // Если жертва не числится в розыске – ничего не делаем
        if (!WantedManager.isWanted(victim.getUniqueId()))
            return;

        // Теперь не важно, чем убили — убираем проверку предмета
        WantedData data = WantedManager.getWanted(victim.getUniqueId());
        int stars = data.getStars();

        int jailTimeSeconds = RefontSearch.getInstance().getConfig().getInt("jailTimers." + stars, 900);
        long jailTimeTicks = jailTimeSeconds * 20L;
        long jailEndTime = System.currentTimeMillis() + (jailTimeSeconds * 1000L);

        // Сохраняем данные о заключении (если игрок уже числится – новый срок перезаписывается)
        JailManager.jailPlayer(victim.getUniqueId(), jailEndTime);

        WantedManager.removeWanted(victim.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (RefontSearch.getInstance().getJailLocation() != null) {
                    victim.teleport(RefontSearch.getInstance().getJailLocation());
                    victim.setGameMode(GameMode.ADVENTURE);
                    victim.sendMessage("§c§l⚔ §7Вы были посажены в тюрьму на " + jailTimeSeconds + " секунд(ы) за розыск!");
                } else {
                    victim.sendMessage("§c§l⚔ §7Точка тюрьмы не установлена!");
                }
            }
        }.runTaskLater(RefontSearch.getInstance(), 20L);

        // Больше не выводим broadcast сообщение.
    }
}
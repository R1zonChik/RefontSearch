package ru.lostone.refontsearch.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JailManager {
    private static File dataFile;
    private static FileConfiguration dataConfig;
    private static Map<UUID, BukkitRunnable> activeTimers = new HashMap<>();

    public static void init(RefontSearch plugin) {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (dataConfig.contains("jailed")) {
            for (String uuidStr : dataConfig.getConfigurationSection("jailed").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    long endTime = dataConfig.getLong("jailed." + uuidStr + ".endTime");
                    if (System.currentTimeMillis() < endTime) {
                        player.setGameMode(GameMode.ADVENTURE);
                        startJailTimer(player);
                    } else {
                        releasePlayer(uuid);
                    }
                }
            }
        }
    }

    // Метод для помещения игрока в тюрьму (при этом предыдущая запись перезаписывается)
    public static void jailPlayer(UUID uuid, long endTime) {
        dataConfig.set("jailed." + uuid.toString() + ".endTime", endTime);
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        dataConfig.set("jailed." + uuid.toString() + ".remaining", remaining);
        saveData();
        // Если активный таймер уже существует, отменяем его
        if (activeTimers.containsKey(uuid)) {
            activeTimers.get(uuid).cancel();
            activeTimers.remove(uuid);
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            startJailTimer(player);
        }
    }

    public static boolean isJailed(UUID uuid) {
        return dataConfig.contains("jailed." + uuid.toString()) &&
                dataConfig.get("jailed." + uuid.toString()) != null;
    }

    // Метод освобождения игрока. Теперь сразу отменяем таймер, удаляем запись из data.yml и не обновляем оставшееся время.
    public static void releasePlayer(UUID uuid) {
        if (activeTimers.containsKey(uuid)) {
            activeTimers.get(uuid).cancel();
            activeTimers.remove(uuid);
        }
        dataConfig.set("jailed." + uuid.toString(), null);
        saveData();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.setGameMode(GameMode.SURVIVAL);
            if (RefontSearch.getInstance().getUnjailLocation() != null) {
                player.teleport(RefontSearch.getInstance().getUnjailLocation());
            }
            player.sendTitle("§a§l⚔ Вы освобождены!", "", 10, 40, 10);
        }
    }

    public static void startJailTimer(Player player) {
        UUID uuid = player.getUniqueId();
        if (dataConfig.get("jailed." + uuid.toString()) == null) return;
        long endTime = dataConfig.getLong("jailed." + uuid.toString() + ".endTime");
        BukkitRunnable timer = new BukkitRunnable() {
            @Override
            public void run() {
                // Если запись уже удалена – отменяем таймер
                if (dataConfig.get("jailed." + uuid.toString()) == null) {
                    cancel();
                    activeTimers.remove(uuid);
                    return;
                }
                if (!player.isOnline()) {
                    cancel();
                    activeTimers.remove(uuid);
                    return;
                }
                long now = System.currentTimeMillis();
                if (now >= endTime) {
                    releasePlayer(uuid);
                    cancel();
                    return;
                }
                long secondsLeft = (endTime - now) / 1000;
                // Обновляем запись "remaining" каждую секунду
                dataConfig.set("jailed." + uuid.toString() + ".remaining", secondsLeft);
                saveData();
                String timeLeft;
                if (secondsLeft < 3600) {
                    long minutes = secondsLeft / 60;
                    long seconds = secondsLeft % 60;
                    timeLeft = String.format("§c§l⌚ Тюремный срок §f%02d:%02d", minutes, seconds);
                } else {
                    long hours = secondsLeft / 3600;
                    long minutes = (secondsLeft % 3600) / 60;
                    long seconds = secondsLeft % 60;
                    timeLeft = String.format("§c§l⌚ Тюремный срок §f%02d:%02d:%02d", hours, minutes, seconds);
                }
                player.sendTitle("", timeLeft, 0, 20, 0);
            }
        };
        timer.runTaskTimer(RefontSearch.getInstance(), 0L, 20L);
        activeTimers.put(uuid, timer);
    }

    private static void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void onDisable() {
        if (dataConfig.contains("jailed")) {
            for (String uuidStr : dataConfig.getConfigurationSection("jailed").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        for (BukkitRunnable timer : activeTimers.values()) {
            timer.cancel();
        }
        activeTimers.clear();
    }
}
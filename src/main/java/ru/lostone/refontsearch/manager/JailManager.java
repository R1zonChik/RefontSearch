package ru.lostone.refontsearch.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.model.Jail;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JailManager {
    private static File dataFile;
    private static FileConfiguration dataConfig;
    private static Map<UUID, BukkitRunnable> activeTimers = new HashMap<>();

    // Карта для хранения времени окончания паузы показа таймера
    private static Map<UUID, Long> pauseTimerDisplayUntil = new HashMap<>();

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

    // Добавляем в JailManager.java метод автоматического освобождения
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

            // Телепортируем в точку освобождения
            Location releaseLocation = null;

            // Сначала пробуем получить точку освобождения из конкретной тюрьмы
            Jail playerJail = RefontSearch.getInstance().getJailsManager().getPlayerJail(uuid);
            if (playerJail != null && playerJail.getReleaseLocation() != null) {
                releaseLocation = playerJail.getReleaseLocation();
            } else {
                // Используем глобальную точку освобождения
                releaseLocation = RefontSearch.getInstance().getUnjailLocation();
            }

            // Если точки освобождения нет, телепортируем на спавн
            if (releaseLocation == null) {
                releaseLocation = player.getWorld().getSpawnLocation();
            }

            player.teleport(releaseLocation);
            player.sendTitle("§a§l⚔ Вы освобождены!", "", 10, 40, 10);

            // Убираем информацию о тюрьме игрока
            RefontSearch.getInstance().getJailsManager().removePlayerJail(uuid);
        }
    }

    // Добавьте этот метод в JailManager.java
    public static long getRemainingTimeSeconds(UUID uuid) {
        if (!isJailed(uuid)) {
            return 0;
        }

        long endTime = dataConfig.getLong("jailed." + uuid.toString() + ".endTime");
        long now = System.currentTimeMillis();

        if (now >= endTime) {
            return 0;
        }

        return (endTime - now) / 1000;
    }

    public static String getRemainingTime(UUID uuid) {
        if (!isJailed(uuid)) {
            return "0";
        }

        long endTime = dataConfig.getLong("jailed." + uuid.toString() + ".endTime");
        long now = System.currentTimeMillis();

        if (now >= endTime) {
            return "0";
        }

        long secondsLeft = (endTime - now) / 1000;

        if (secondsLeft < 3600) {
            long minutes = secondsLeft / 60;
            long seconds = secondsLeft % 60;
            return String.format("%02d:%02d", minutes, seconds);
        } else {
            long hours = secondsLeft / 3600;
            long minutes = (secondsLeft % 3600) / 60;
            long seconds = secondsLeft % 60;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }

    // Метод для паузы показа таймера
    public static void pauseTimerDisplay(UUID uuid, long milliseconds) {
        pauseTimerDisplayUntil.put(uuid, System.currentTimeMillis() + milliseconds);
    }

    // Метод для проверки, можно ли показывать таймер
    public static boolean canDisplayTimer(UUID uuid) {
        Long pauseUntil = pauseTimerDisplayUntil.get(uuid);
        if (pauseUntil == null) {
            return true;
        }
        return System.currentTimeMillis() >= pauseUntil;
    }

    public static void startJailTimer(Player player) {
        UUID uuid = player.getUniqueId();
        if (dataConfig.get("jailed." + uuid.toString()) == null) return;

        // Проверяем валидность тюрьмы при запуске таймера
        checkJailValidity(uuid);

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

                // Показываем таймер в ActionBar вместо Title
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

                // Отправляем в ActionBar вместо Title
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(timeLeft));
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

    public static void checkJailValidity(UUID playerId) {
        if (isJailed(playerId)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Location jailLoc = null;

                // Проверяем, существует ли тюрьма игрока
                Jail playerJail = RefontSearch.getInstance().getJailsManager().getPlayerJail(playerId);
                if (playerJail != null && playerJail.getJailLocation() != null) {
                    jailLoc = playerJail.getJailLocation();
                } else {
                    // Если нет конкретной тюрьмы, ищем любую другую
                    jailLoc = RefontSearch.getInstance().getJailLocation();
                    if (jailLoc == null) {
                        Jail randomJail = RefontSearch.getInstance().getJailsManager().getRandomJail();
                        if (randomJail != null && randomJail.getJailLocation() != null) {
                            jailLoc = randomJail.getJailLocation();
                            RefontSearch.getInstance().getJailsManager().setPlayerJail(playerId, randomJail.getName());
                        } else {
                            // Если нет никаких тюрем, освобождаем игрока
                            releasePlayer(playerId);
                            return;
                        }
                    }
                }

                // Если нашли тюрьму - телепортируем
                if (jailLoc != null) {
                    player.teleport(jailLoc);
                }
            }
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
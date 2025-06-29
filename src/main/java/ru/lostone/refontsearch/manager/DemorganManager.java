package ru.lostone.refontsearch.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.lostone.refontsearch.DemorganData;
import ru.lostone.refontsearch.RefontSearch;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DemorganManager {

    private static RefontSearch plugin;
    private static final Map<String, DemorganData> demorganPlayers = new ConcurrentHashMap<>();
    private static final Map<String, BukkitTask> releaseTasks = new ConcurrentHashMap<>();

    public static void init(RefontSearch instance) {
        plugin = instance;

        // Запускаем периодическую проверку каждые 30 секунд
        Bukkit.getScheduler().runTaskTimer(plugin, DemorganManager::checkExpiredPunishments, 600L, 600L);
    }

    /**
     * Отправляет игрока в деморган
     */
    public static void sendToDemorgan(Player player, String reason, String administrator, long durationSeconds) {
        String playerName = player.getName();

        // Создаем данные демогрант
        DemorganData data = new DemorganData(
                playerName,
                player.getDisplayName(),
                reason,
                administrator,
                durationSeconds,
                "DEMORGAN"
        );

        demorganPlayers.put(playerName, data);

        // Телепортируем в деморган
        Location demorganLocation = getDemorganLocation();
        if (demorganLocation != null) {
            player.teleport(demorganLocation);
        }

        // Планируем автоматическое освобождение
        scheduleRelease(playerName, durationSeconds);

        // Сохраняем данные
        saveDemorganData();

        plugin.getLogger().info("Игрок " + playerName + " отправлен в деморган на " + durationSeconds + " секунд по причине: " + reason);
    }

    /**
     * Освобождает игрока из демогрант
     */
    public static void releaseFromDemorgan(String playerName) {
        DemorganData data = demorganPlayers.remove(playerName);
        if (data == null) return;

        // Отменяем задачу автоосвобождения
        BukkitTask task = releaseTasks.remove(playerName);
        if (task != null) {
            task.cancel();
        }

        // Телепортируем игрока обратно
        Player player = Bukkit.getPlayer(playerName);
        if (player != null && player.isOnline()) {
            Location releaseLocation = getReleaseLocation();
            if (releaseLocation != null) {
                player.teleport(releaseLocation);
            }
        }

        // Сохраняем данные
        saveDemorganData();

        plugin.getLogger().info("Игрок " + playerName + " освобожден из демогрант");
    }

    /**
     * Проверяет, находится ли игрок в демогрант
     */
    public static boolean isInDemorgan(String playerName) {
        return demorganPlayers.containsKey(playerName);
    }

    /**
     * Получает данные демогрант игрока
     */
    public static DemorganData getDemorganData(String playerName) {
        return demorganPlayers.get(playerName);
    }

    /**
     * Получает список всех игроков в демогрант
     */
    public static Set<String> getDemorganPlayers() {
        return new HashSet<>(demorganPlayers.keySet());
    }

    /**
     * Планирует автоматическое освобождение
     */
    private static void scheduleRelease(String playerName, long durationSeconds) {
        // Отменяем предыдущую задачу если есть
        BukkitTask existingTask = releaseTasks.get(playerName);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Создаем новую задачу
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            releaseFromDemorgan(playerName);

            Player player = Bukkit.getPlayer(playerName);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a§l⚔ §7Ваш срок в демогрант истек! Вы освобождены.");
            }
        }, durationSeconds * 20L); // 20 тиков = 1 секунда

        releaseTasks.put(playerName, task);
    }

    /**
     * Проверяет истекшие наказания
     */
    private static void checkExpiredPunishments() {
        List<String> toRelease = new ArrayList<>();

        for (Map.Entry<String, DemorganData> entry : demorganPlayers.entrySet()) {
            if (entry.getValue().isExpired()) {
                toRelease.add(entry.getKey());
            }
        }

        for (String playerName : toRelease) {
            releaseFromDemorgan(playerName);
        }
    }

    /**
     * Получает локацию демогрант
     */
    private static Location getDemorganLocation() {
        String locStr = plugin.getConfig().getString("demorgan.location.spawn", "");
        if (locStr.isEmpty()) {
            return plugin.getJailLocation(); // Fallback на обычную тюрьму
        }
        return parseLocation(locStr);
    }

    /**
     * Получает локацию освобождения
     */
    private static Location getReleaseLocation() {
        String locStr = plugin.getConfig().getString("demorgan.location.release", "");
        if (locStr.isEmpty()) {
            return plugin.getUnjailLocation(); // Fallback на обычную точку освобождения
        }
        return parseLocation(locStr);
    }

    /**
     * Парсит строку локации
     */
    private static Location parseLocation(String locStr) {
        try {
            String[] parts = locStr.split(";");
            if (parts.length != 4) return null;

            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Сохраняет данные демогрант (заглушка - можно расширить для сохранения в файл)
     */
    private static void saveDemorganData() {
        // Здесь можно добавить сохранение в файл или базу данных
        // Пока просто логируем
        plugin.getLogger().info("Данные демогрант обновлены. Всего в демогрант: " + demorganPlayers.size());
    }

    /**
     * Загружает данные демогрант (заглушка)
     */
    public static void loadDemorganData() {
        // Здесь можно добавить загрузку из файла или базы данных
        plugin.getLogger().info("Данные демогрант загружены");
    }

    /**
     * Очистка при отключении плагина
     */
    public static void onDisable() {
        // Отменяем все задачи
        for (BukkitTask task : releaseTasks.values()) {
            task.cancel();
        }
        releaseTasks.clear();

        // Сохраняем данные
        saveDemorganData();
    }
}
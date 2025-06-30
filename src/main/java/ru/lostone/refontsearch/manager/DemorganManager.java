package ru.lostone.refontsearch.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.lostone.refontsearch.DemorganData;
import ru.lostone.refontsearch.RefontSearch;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DemorganManager {

    private static RefontSearch plugin;
    private static final Map<String, DemorganData> demorganPlayers = new ConcurrentHashMap<>();
    private static final Map<String, BukkitTask> releaseTasks = new ConcurrentHashMap<>();

    // ========== НОВЫЙ ФУНКЦИОНАЛ: ТАЙМЕРЫ ==========
    private static final Map<UUID, BukkitTask> timerTasks = new ConcurrentHashMap<>();
    // ================================================

    public static void init(RefontSearch instance) {
        plugin = instance;

        // Запускаем периодическую проверку каждые 30 секунд
        Bukkit.getScheduler().runTaskTimer(plugin, DemorganManager::checkExpiredPunishments, 600L, 600L);

        // ПРИНУДИТЕЛЬНАЯ проверка местоположения каждые 5 секунд
        Bukkit.getScheduler().runTaskTimer(plugin, DemorganManager::checkPlayerLocations, 100L, 100L);

        plugin.getLogger().info("DemorganManager инициализирован с принудительной проверкой местоположения и таймерами");
    }

    /**
     * Отправляет игрока в деморган
     */
    public static void sendToDemorgan(Player player, String reason, String administrator, long durationSeconds) {
        String playerName = player.getName();

        // Проверяем, не находится ли уже в демогрант
        if (isInDemorgan(playerName)) {
            return;
        }

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

            // Показываем эффекты входа в демогрант
            showDemorganEffects(player);
        } else {
            plugin.getLogger().warning("Локация демогрант не установлена для игрока " + playerName);
        }

        // Планируем автоматическое освобождение
        scheduleRelease(playerName, durationSeconds);

        // ========== НОВЫЙ ФУНКЦИОНАЛ: ЗАПУСК ТАЙМЕРА ==========
        startDemorganTimer(player, durationSeconds);
        // =====================================================

        // Сохраняем данные
        saveDemorganData();

        plugin.getLogger().info("Игрок " + playerName + " отправлен в деморган на " + durationSeconds + " секунд по причине: " + reason + " (Администратор: " + administrator + ")");
    }

    /**
     * ========== НОВЫЙ МЕТОД: ЗАПУСК ТАЙМЕРА ДЕМОГРАНТ ==========
     */
    private static void startDemorganTimer(Player player, long durationSeconds) {
        UUID playerUUID = player.getUniqueId();

        // Останавливаем предыдущий таймер, если есть
        if (timerTasks.containsKey(playerUUID)) {
            timerTasks.get(playerUUID).cancel();
            timerTasks.remove(playerUUID);
        }

        // Создаем новый таймер
        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                DemorganData data = demorganPlayers.get(player.getName());
                if (data == null || System.currentTimeMillis() >= data.getReleaseTime()) {
                    // Время истекло или игрок освобожден
                    this.cancel();
                    timerTasks.remove(playerUUID);
                    return;
                }

                // Если игрок не онлайн, останавливаем таймер
                if (!player.isOnline()) {
                    this.cancel();
                    timerTasks.remove(playerUUID);
                    return;
                }

                long remainingSeconds = (data.getReleaseTime() - System.currentTimeMillis()) / 1000;
                if (remainingSeconds <= 0) {
                    this.cancel();
                    timerTasks.remove(playerUUID);
                    return;
                }

                String formattedTime = formatTime(remainingSeconds);
                String title = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("demorgan.effects.timer.title", "§c§lДЕМОГРАН"));
                String subtitle = ChatColor.translateAlternateColorCodes('&',
                                plugin.getConfig().getString("demorgan.effects.timer.subtitle", "§7Осталось: §e{time}"))
                        .replace("{time}", formattedTime);

                int fadeIn = plugin.getConfig().getInt("demorgan.effects.timer.fadeIn", 0);
                int stay = plugin.getConfig().getInt("demorgan.effects.timer.stay", 25);
                int fadeOut = plugin.getConfig().getInt("demorgan.effects.timer.fadeOut", 10);

                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
        }.runTaskTimer(plugin, 0L, 20L); // Запускаем сразу, повторяем каждую секунду

        timerTasks.put(playerUUID, timerTask);
    }

    /**
     * ========== НОВЫЙ МЕТОД: ФОРМАТИРОВАНИЕ ВРЕМЕНИ ==========
     */
    private static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d ч %d мин %d сек", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%d мин %d сек", minutes, secs);
        } else {
            return String.format("%d сек", secs);
        }
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

        // ========== НОВЫЙ ФУНКЦИОНАЛ: ОСТАНОВКА ТАЙМЕРА ==========
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            UUID playerUUID = player.getUniqueId();
            if (timerTasks.containsKey(playerUUID)) {
                timerTasks.get(playerUUID).cancel();
                timerTasks.remove(playerUUID);
            }
        }
        // =========================================================

        // Телепортируем игрока обратно
        if (player != null && player.isOnline()) {
            Location releaseLocation = getReleaseLocation();
            if (releaseLocation != null) {
                player.teleport(releaseLocation);
            }

            // Показываем эффекты освобождения
            showReleaseEffects(player);
        }

        // Сохраняем данные
        saveDemorganData();

        plugin.getLogger().info("Игрок " + playerName + " освобожден из демогрант");
    }

    /**
     * ========== НОВЫЙ МЕТОД: ОБРАБОТКА ВЫХОДА ИГРОКА ==========
     */
    public static void onPlayerQuit(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (timerTasks.containsKey(playerUUID)) {
            timerTasks.get(playerUUID).cancel();
            timerTasks.remove(playerUUID);
        }
    }

    /**
     * ========== НОВЫЙ МЕТОД: ВОССТАНОВЛЕНИЕ ТАЙМЕРА ПРИ ВХОДЕ ==========
     */
    public static void onPlayerJoin(Player player) {
        String playerName = player.getName();
        if (isInDemorgan(playerName)) {
            DemorganData data = getDemorganData(playerName);
            if (data != null) {
                long remainingSeconds = (data.getReleaseTime() - System.currentTimeMillis()) / 1000;
                if (remainingSeconds > 0) {
                    // Показываем эффекты возвращения в демогрант
                    showDemorganEffects(player);
                    // Запускаем таймер
                    startDemorganTimer(player, remainingSeconds);

                    // Телепортируем в демогрант
                    Location demorganLocation = getDemorganLocation();
                    if (demorganLocation != null) {
                        player.teleport(demorganLocation);
                    }
                } else {
                    // Время истекло, освобождаем
                    releaseFromDemorgan(playerName);
                }
            }
        }
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
     * Проверяет, находится ли игрок в зоне демогрант
     */
    public static boolean isPlayerInDemorganZone(Player player) {
        if (!isInDemorgan(player.getName())) {
            return true; // Если не в демогрант, то проверка не нужна
        }

        Location demorganLocation = getDemorganLocation();
        if (demorganLocation == null) {
            return true; // Если локация не установлена, не ограничиваем
        }

        Location playerLocation = player.getLocation();

        // Проверяем мир
        if (!playerLocation.getWorld().equals(demorganLocation.getWorld())) {
            return false;
        }

        // Проверяем расстояние
        double radius = plugin.getConfig().getDouble("demorgan.radius", 25.0);
        double distance = playerLocation.distance(demorganLocation);

        return distance <= radius;
    }

    /**
     * Принудительно возвращает игрока в демогрант
     */
    public static void forceReturnToDemorgan(Player player) {
        Location demorganLocation = getDemorganLocation();
        if (demorganLocation != null) {
            player.teleport(demorganLocation);
        }
    }

    /**
     * Получает информацию о демогрант для игрока
     */
    public static String getDemorganInfo(String playerName) {
        DemorganData data = getDemorganData(playerName);
        if (data == null) {
            return "Игрок не находится в демогрант";
        }

        return String.format("§7Игрок: §f%s\n§7Причина: §f%s\n§7Администратор: §a%s\n§7Оставшееся время: §e%s",
                playerName, data.getReason(), data.getAdministrator(), data.getFormattedRemainingTime());
    }

    /**
     * ПРИНУДИТЕЛЬНАЯ проверка местоположения всех игроков в демогрант
     */
    private static void checkPlayerLocations() {
        for (String playerName : demorganPlayers.keySet()) {
            Player player = Bukkit.getPlayer(playerName);
            if (player == null || !player.isOnline()) {
                continue;
            }

            // Проверяем права на обход
            if (player.hasPermission("refontsearch.demorgan.bypass")) {
                continue;
            }

            Location demorganLocation = getDemorganLocation();
            if (demorganLocation == null) {
                continue;
            }

            Location playerLocation = player.getLocation();
            double radius = plugin.getConfig().getDouble("demorgan.radius", 30.0);
            double distance = playerLocation.distance(demorganLocation);

            // Проверяем мир и расстояние
            if (!playerLocation.getWorld().equals(demorganLocation.getWorld()) || distance > radius) {
                // Принудительно возвращаем в демогрант
                player.teleport(demorganLocation);

                // Отправляем сообщение
                String message = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.demorgan.leave", "§c§l⚔ §7Вы не можете покинуть демогрант!"));
                player.sendMessage(message);

                // Показываем эффекты побега
                showEscapeEffects(player);

                // Логируем попытку побега
                plugin.getLogger().info("Принудительно возвращен в демогрант: " + playerName +
                        " (расстояние: " + String.format("%.1f", distance) + " блоков, лимит: " + radius + ")");
            }
        }
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
                String expiredMessage = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.demorgan.expired", "§a§l⚔ §7Ваш срок в демогрант истек! Вы освобождены."));
                player.sendMessage(expiredMessage);
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

            // Уведомляем игрока если он онлайн
            Player player = Bukkit.getPlayer(playerName);
            if (player != null && player.isOnline()) {
                String expiredMessage = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.demorgan.expired", "§a§l⚔ §7Ваш срок в демогрант истек! Вы освобождены."));
                player.sendMessage(expiredMessage);
            }
        }

        if (!toRelease.isEmpty()) {
            plugin.getLogger().info("Автоматически освобождено из демогрант: " + toRelease.size() + " игроков");
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
            plugin.getLogger().warning("Ошибка парсинга локации: " + locStr + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Показывает эффекты при входе в демогрант
     */
    public static void showDemorganEffects(Player player) {
        // Title и Subtitle
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.rejoin.title", "§c§lДЕМОГРАН"));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.rejoin.subtitle", "§7Вы находитесь в административной тюрьме"));

        int fadeIn = plugin.getConfig().getInt("demorgan.effects.rejoin.fadeIn", 10);
        int stay = plugin.getConfig().getInt("demorgan.effects.rejoin.stay", 60);
        int fadeOut = plugin.getConfig().getInt("demorgan.effects.rejoin.fadeOut", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        // Звук
        String sound = plugin.getConfig().getString("demorgan.effects.rejoin.sound", "block.iron_door.close");
        float volume = (float) plugin.getConfig().getDouble("demorgan.effects.rejoin.soundVolume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("demorgan.effects.rejoin.soundPitch", 1.0);

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // Если звук не найден, используем стандартный
            player.playSound(player.getLocation(), "block.iron_door.close", 1.0f, 1.0f);
        }
    }

    /**
     * Показывает эффекты при попытке побега
     */
    public static void showEscapeEffects(Player player) {
        // Title и Subtitle
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.escape.title", "§c§lПОБЕГ НЕВОЗМОЖЕН!"));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.escape.subtitle", "§7Охрана вернула вас в камеру"));

        int fadeIn = plugin.getConfig().getInt("demorgan.effects.escape.fadeIn", 10);
        int stay = plugin.getConfig().getInt("demorgan.effects.escape.stay", 40);
        int fadeOut = plugin.getConfig().getInt("demorgan.effects.escape.fadeOut", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        // Звук
        String sound = plugin.getConfig().getString("demorgan.effects.escape.sound", "entity.enderman.teleport");
        float volume = (float) plugin.getConfig().getDouble("demorgan.effects.escape.soundVolume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("demorgan.effects.escape.soundPitch", 0.5);

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // Если звук не найден, используем стандартный
            player.playSound(player.getLocation(), "entity.enderman.teleport", 1.0f, 0.5f);
        }
    }

    /**
     * Показывает эффекты при освобождении
     */
    private static void showReleaseEffects(Player player) {
        // Title и Subtitle
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.release.title", "§a§lОСВОБОЖДЕНИЕ"));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("demorgan.effects.release.subtitle", "§7Вы были освобождены из демогрант"));

        int fadeIn = plugin.getConfig().getInt("demorgan.effects.release.fadeIn", 10);
        int stay = plugin.getConfig().getInt("demorgan.effects.release.stay", 40);
        int fadeOut = plugin.getConfig().getInt("demorgan.effects.release.fadeOut", 20);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);

        // Звук
        String sound = plugin.getConfig().getString("demorgan.effects.release.sound", "entity.player.levelup");
        float volume = (float) plugin.getConfig().getDouble("demorgan.effects.release.soundVolume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("demorgan.effects.release.soundPitch", 1.0);

        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // Если звук не найден, используем стандартный
            player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
        }
    }

    /**
     * Сохраняет данные демогрант (заглушка - можно расширить для сохранения в файл)
     */
    private static void saveDemorganData() {
        // Здесь можно добавить сохранение в файл или базу данных
        // Пока просто логируем
        if (plugin.getConfig().getBoolean("debug.log_demorgan_changes", false)) {
            plugin.getLogger().info("Данные демогрант обновлены. Всего в демогрант: " + demorganPlayers.size());
        }
    }

    /**
     * Загружает данные демогрант (заглушка)
     */
    public static void loadDemorganData() {
        // Здесь можно добавить загрузку из файла или базы данных
        plugin.getLogger().info("Данные демогрант загружены. Инициализировано: " + demorganPlayers.size() + " записей");
    }

    /**
     * Получает статистику демогрант
     */
    public static Map<String, Object> getDemorganStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_players", demorganPlayers.size());
        stats.put("active_tasks", releaseTasks.size());
        stats.put("active_timers", timerTasks.size()); // Новая статистика

        // Подсчет по типам нарушений
        Map<String, Integer> reasonStats = new HashMap<>();
        for (DemorganData data : demorganPlayers.values()) {
            reasonStats.merge(data.getReason(), 1, Integer::sum);
        }
        stats.put("reason_stats", reasonStats);

        return stats;
    }

    /**
     * Очистка при отключении плагина
     */
    public static void onDisable() {
        // Отменяем все задачи освобождения
        for (BukkitTask task : releaseTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        releaseTasks.clear();

        // ========== НОВЫЙ ФУНКЦИОНАЛ: ОТМЕНА ВСЕХ ТАЙМЕРОВ ==========
        for (BukkitTask timerTask : timerTasks.values()) {
            if (timerTask != null) {
                timerTask.cancel();
            }
        }
        timerTasks.clear();
        // ============================================================

        // Сохраняем данные
        saveDemorganData();

        plugin.getLogger().info("DemorganManager отключен. Отменено задач автоосвобождения: " + releaseTasks.size() + ", таймеров: " + timerTasks.size());
    }

    /**
     * Перезагружает конфигурацию демогрант
     */
    public static void reloadConfig() {
        plugin.getLogger().info("Конфигурация DemorganManager перезагружена");
    }

    /**
     * Получает количество игроков в демогрант
     */
    public static int getDemorganCount() {
        return demorganPlayers.size();
    }

    /**
     * Проверяет, установлены ли локации демогрант
     */
    public static boolean isDemorganLocationSet() {
        String spawnLoc = plugin.getConfig().getString("demorgan.location.spawn", "");
        return !spawnLoc.isEmpty();
    }
}
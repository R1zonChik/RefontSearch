package ru.lostone.refontsearch.updater;

import com.google.gson.JsonArray;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class Updater {

    private final RefontSearch plugin;
    private final String currentVersion;
    private final String ENCRYPTED_DOMAIN = "aHR0cHM6Ly9yaXpvbmNoaWsucnU=";

    private String latestVersion = null;
    private String updateDate = null;
    private boolean updateAvailable = false;
    private boolean hasNotified = false;

    // 🆘 FALLBACK РЕЖИМ: когда сайт недоступен
    private boolean siteAccessible = true;
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES_BEFORE_FALLBACK = 3;
    private boolean fallbackModeActive = false;
    private long lastFallbackCheck = 0;
    private static final long FALLBACK_RECHECK_TIME = 1800000; // 30 минут

    private long lastUpdateCheck = 0;
    private static final long UPDATE_CACHE_TIME = 120000; // 2 минуты кеш
    private static final int MAX_UPDATE_RETRIES = 1;
    private static final long CHECK_INTERVAL = 6000L; // 5 минут

    // Добавляем новые поля для хранения информации о приоритете
    private boolean isHighPriorityUpdate = false;
    private String updateReason = "";
    private String lastUpdateResponse = null;

    public Updater(RefontSearch plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Запускает проверку обновлений с FALLBACK режимом
     */
    public void startUpdateChecker() {
        // Первая проверка через 30 секунд
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForUpdates();
            }
        }.runTaskLaterAsynchronously(plugin, 600L);

        // Регулярная проверка каждые 5 минут
        new BukkitRunnable() {
            @Override
            public void run() {
                checkForUpdates();
            }
        }.runTaskTimerAsynchronously(plugin, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    /**
     * БЫСТРАЯ проверка обновлений с передачей версии и названия файла
     */
    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            // Проверяем кеш (2 минуты)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateCheck < UPDATE_CACHE_TIME) {
                return updateAvailable;
            }

            // 🆘 FALLBACK: Если в fallback режиме, проверяем только раз в 30 минут
            if (fallbackModeActive && (currentTime - lastFallbackCheck) < FALLBACK_RECHECK_TIME) {
                return false;
            }

            try {
                String serverIP = plugin.getServerIP();

                if (serverIP == null) {
                    // Убираем лог
                    return false;
                }

                // 🌐 ПРОВЕРЯЕМ ДОСТУПНОСТЬ САЙТА
                boolean siteAvailable = checkSiteAccessibility();

                if (!siteAvailable) {
                    consecutiveFailures++;
                    // Оставляем только критичные логи
                    if (consecutiveFailures >= MAX_FAILURES_BEFORE_FALLBACK) {
                        plugin.getLogger().warning("⚠️ Сайт rizonchik.ru недоступен");
                    }

                    if (consecutiveFailures >= MAX_FAILURES_BEFORE_FALLBACK && !fallbackModeActive) {
                        activateFallbackMode();
                    }

                    lastUpdateCheck = currentTime;
                    lastFallbackCheck = currentTime;
                    return false;
                }

                // 🌐 САЙТ ДОСТУПЕН - сбрасываем счетчики
                if (consecutiveFailures > 0 || fallbackModeActive) {
                    consecutiveFailures = 0;
                    if (fallbackModeActive) {
                        deactivateFallbackMode();
                    }
                }

                String domain = new String(java.util.Base64.getDecoder().decode(ENCRYPTED_DOMAIN));
                String apiUrl = domain + "/api/check_license_by_ip.php";

                // 📁 ПОЛУЧАЕМ НАЗВАНИЕ ФАЙЛА ПЛАГИНА
                String pluginFileName = getPluginFileName();

                // 🔧 URL с версией и названием файла (убираем лог)
                String urlWithParams = apiUrl + "?" +
                        "ip=" + URLEncoder.encode(serverIP, "UTF-8") +
                        "&plugin=" + URLEncoder.encode("RefontSearch", "UTF-8") +
                        "&current_version=" + URLEncoder.encode(currentVersion, "UTF-8") +
                        "&file_name=" + URLEncoder.encode(pluginFileName, "UTF-8");

                URL url = new URL(urlWithParams);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "RefontSearch/" + currentVersion + " FastUpdateChecker");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Connection", "close");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    connection.disconnect();

                    // Убираем лог ответа API

                    boolean result = parseUpdateResponse(response.toString());
                    lastUpdateCheck = currentTime;
                    siteAccessible = true;
                    return result;
                } else {
                    // Убираем лог HTTP ошибок
                }

            } catch (Exception e) {
                consecutiveFailures++;
                // Убираем лог ошибок соединения

                if (consecutiveFailures >= MAX_FAILURES_BEFORE_FALLBACK && !fallbackModeActive) {
                    activateFallbackMode();
                }
            }

            lastUpdateCheck = currentTime;
            return false;

        }).thenApply(result -> {
            if (result && updateAvailable && !hasNotified) {
                Bukkit.getScheduler().runTask(plugin, this::notifyUpdate);
                hasNotified = true;
            }
            return result;
        }).exceptionally(throwable -> {
            return false;
        });
    }

    /**
     * 📁 ПОЛУЧАЕТ НАЗВАНИЕ ФАЙЛА ПЛАГИНА
     */
    private String getPluginFileName() {
        try {
            // Получаем файл плагина через reflection
            java.io.File pluginFile = null;

            // Способ 1: через getFile() если доступен
            try {
                java.lang.reflect.Method getFileMethod = plugin.getClass().getMethod("getFile");
                pluginFile = (java.io.File) getFileMethod.invoke(plugin);
            } catch (Exception e) {
                // Игнорируем
            }

            // Способ 2: через URLClassLoader
            if (pluginFile == null) {
                try {
                    ClassLoader classLoader = plugin.getClass().getClassLoader();
                    if (classLoader instanceof java.net.URLClassLoader) {
                        java.net.URLClassLoader urlClassLoader = (java.net.URLClassLoader) classLoader;
                        java.net.URL[] urls = urlClassLoader.getURLs();
                        for (java.net.URL url : urls) {
                            if (url.getPath().endsWith(".jar")) {
                                pluginFile = new java.io.File(url.toURI());
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }

            // Способ 3: поиск в папке plugins
            if (pluginFile == null) {
                java.io.File pluginsDir = plugin.getDataFolder().getParentFile();
                java.io.File[] files = pluginsDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file.getName().toLowerCase().contains("refontsearch") &&
                                file.getName().endsWith(".jar")) {
                            pluginFile = file;
                            break;
                        }
                    }
                }
            }

            if (pluginFile != null) {
                String fileName = pluginFile.getName();
                // Убираем лог найденного файла
                return fileName;
            } else {
                // Фолбэк - генерируем имя из версии
                String fallbackName = "RefontSearch-" + currentVersion + ".jar";
                // Убираем лог предупреждения
                return fallbackName;
            }

        } catch (Exception e) {
            // Убираем лог ошибки
            return "RefontSearch-" + currentVersion + ".jar";
        }
    }

    /**
     * Парсит ответ API и проверяет обновления
     */
    private boolean parseUpdateResponse(String jsonResponse) {
        try {
            this.lastUpdateResponse = jsonResponse; // Сохраняем для анализа

            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);

            if (jsonObject.has("success") && jsonObject.get("success").getAsBoolean()) {
                JsonObject data = jsonObject.getAsJsonObject("data");

                // 🆕 ПРОВЕРЯЕМ ИНФОРМАЦИЮ ОБ ОБНОВЛЕНИЯХ
                if (data.has("update_info")) {
                    JsonObject updateInfo = data.getAsJsonObject("update_info");

                    if (updateInfo.has("update_available") && updateInfo.get("update_available").getAsBoolean()) {

                        // Получаем информацию об обновлении
                        latestVersion = updateInfo.has("latest_version") ?
                                updateInfo.get("latest_version").getAsString() : currentVersion;

                        if (updateInfo.has("updated_at") && !updateInfo.get("updated_at").isJsonNull()) {
                            updateDate = updateInfo.get("updated_at").getAsString();
                        }

                        // 🔥 ПРОВЕРЯЕМ КРИТИЧНОСТЬ ОБНОВЛЕНИЯ
                        boolean isHighPriority = false;
                        String updateReason = "Доступно обновление";

                        if (updateInfo.has("file_change_detected") &&
                                updateInfo.get("file_change_detected").getAsBoolean()) {
                            isHighPriority = true;
                            updateReason = "ФАЙЛ ИЗМЕНЕН - КРИТИЧНОЕ ОБНОВЛЕНИЕ";
                        }

                        if (updateInfo.has("force_update") &&
                                updateInfo.get("force_update").getAsBoolean()) {
                            isHighPriority = true;
                            updateReason = "ПРИНУДИТЕЛЬНОЕ ОБНОВЛЕНИЕ";
                        }

                        // Сохраняем информацию о приоритете
                        this.isHighPriorityUpdate = isHighPriority;
                        this.updateReason = updateReason;

                        updateAvailable = true;

                        // Логируем только если обновление найдено
                        plugin.getLogger().info("🆕 Найдено обновление: " + currentVersion + " → " + latestVersion);
                        if (isHighPriority) {
                            plugin.getLogger().warning("🔥 " + updateReason);
                        }
                        if (updateDate != null) {
                            plugin.getLogger().info("📅 Дата обновления: " + updateDate);
                        }

                        // Логируем причины обновления только если есть
                        if (updateInfo.has("update_reasons")) {
                            try {
                                JsonArray reasons = updateInfo.getAsJsonArray("update_reasons");
                                plugin.getLogger().info("📋 Причины обновления:");
                                for (int i = 0; i < reasons.size(); i++) {
                                    plugin.getLogger().info("   • " + reasons.get(i).getAsString());
                                }
                            } catch (Exception e) {
                                // Игнорируем ошибки парсинга
                            }
                        }

                        return true;
                    } else {
                        // Сброс если обновления больше нет (убираем лог)
                        if (updateAvailable) {
                            updateAvailable = false;
                            hasNotified = false;
                            isHighPriorityUpdate = false;
                        }
                    }
                } else {
                    // Убираем лог "Информация об обновлениях недоступна"
                }
            } else {
                // Убираем лог API ответа
            }

            return false;

        } catch (Exception e) {
            // Убираем логи ошибок парсинга
            return false;
        }
    }

    /**
     * 🌐 ПРОВЕРЯЕТ ДОСТУПНОСТЬ САЙТА (простой ping)
     */
    private boolean checkSiteAccessibility() {
        try {
            String domain = new String(java.util.Base64.getDecoder().decode(ENCRYPTED_DOMAIN));
            URL url = new URL(domain);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent", "RefontSearch SiteCheck");

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode >= 200 && responseCode < 400;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 🆘 АКТИВИРУЕТ FALLBACK РЕЖИМ
     */
    private void activateFallbackMode() {
        fallbackModeActive = true;
        lastFallbackCheck = System.currentTimeMillis();

        plugin.getLogger().warning("╔══════════════════════════════════════╗");
        plugin.getLogger().warning("║      🆘 FALLBACK РЕЖИМ АКТИВЕН       ║");
        plugin.getLogger().warning("║                                      ║");
        plugin.getLogger().warning("║  Сайт rizonchik.ru недоступен        ║");
        plugin.getLogger().warning("║  Плагин работает БЕЗ проверок        ║");
        plugin.getLogger().warning("║  Проверка доступности каждые 30 мин  ║");
        plugin.getLogger().warning("║                                      ║");
        plugin.getLogger().warning("╚══════════════════════════════════════╝");

        // Уведомляем админов в игре
        notifyFallbackMode();
    }

    /**
     * ✅ ДЕАКТИВИРУЕТ FALLBACK РЕЖИМ
     */
    private void deactivateFallbackMode() {
        fallbackModeActive = false;
        consecutiveFailures = 0;

        plugin.getLogger().info("╔══════════════════════════════════════╗");
        plugin.getLogger().info("║      ✅ САЙТ СНОВА ДОСТУПЕН!         ║");
        plugin.getLogger().info("║                                      ║");
        plugin.getLogger().info("║  Fallback режим отключен             ║");
        plugin.getLogger().info("║  Возобновляется проверка лицензий    ║");
        plugin.getLogger().info("║                                      ║");
        plugin.getLogger().info("╚══════════════════════════════════════╝");

        // Уведомляем админов
        notifyFallbackDeactivated();
    }

    /**
     * 🔔 УВЕДОМЛЯЕТ О FALLBACK РЕЖИМЕ
     */
    private void notifyFallbackMode() {
        String message = ChatColor.YELLOW + "╔════════════════════════════════════════╗\n" +
                ChatColor.YELLOW + "║" + ChatColor.RED + ChatColor.BOLD + "        🆘 FALLBACK РЕЖИМ АКТИВЕН       " + ChatColor.RESET + ChatColor.YELLOW + "║\n" +
                ChatColor.YELLOW + "║                                        ║\n" +
                ChatColor.YELLOW + "║  " + ChatColor.WHITE + "⚠️ Сайт rizonchik.ru недоступен        " + ChatColor.YELLOW + "║\n" +
                ChatColor.YELLOW + "║  " + ChatColor.WHITE + "🔓 Плагин работает БЕЗ проверок        " + ChatColor.YELLOW + "║\n" +
                ChatColor.YELLOW + "║  " + ChatColor.WHITE + "⏰ Проверка восстановления каждые 30м  " + ChatColor.YELLOW + "║\n" +
                ChatColor.YELLOW + "║                                        ║\n" +
                ChatColor.YELLOW + "╚════════════════════════════════════════╝";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("refontsearch.admin") || player.isOp()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * 🔔 УВЕДОМЛЯЕТ О ВОССТАНОВЛЕНИИ САЙТА
     */
    private void notifyFallbackDeactivated() {
        String message = ChatColor.GREEN + "╔════════════════════════════════════════╗\n" +
                ChatColor.GREEN + "║" + ChatColor.GREEN + ChatColor.BOLD + "        ✅ САЙТ ВОССТАНОВЛЕН!           " + ChatColor.RESET + ChatColor.GREEN + "║\n" +
                ChatColor.GREEN + "║                                        ║\n" +
                ChatColor.GREEN + "║  " + ChatColor.WHITE + "🌐 Связь с rizonchik.ru восстановлена  " + ChatColor.GREEN + "║\n" +
                ChatColor.GREEN + "║  " + ChatColor.WHITE + "🔒 Проверка лицензий возобновлена      " + ChatColor.GREEN + "║\n" +
                ChatColor.GREEN + "║  " + ChatColor.WHITE + "📡 Проверка обновлений активна         " + ChatColor.GREEN + "║\n" +
                ChatColor.GREEN + "║                                        ║\n" +
                ChatColor.GREEN + "╚════════════════════════════════════════╝";

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("refontsearch.admin") || player.isOp()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Уведомляет о доступном обновлении с КРАСИВЫМ дизайном и звуком
     */
    private void notifyUpdate() {
        // 🔊 ЗВУКОВОЕ УВЕДОМЛЕНИЕ для всех игроков с правами
        playUpdateSound(isHighPriorityUpdate);

        // 🎨 КРАСИВОЕ сообщение в консоль
        String border = isHighPriorityUpdate ? "🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥" : "╔══════════════════════════════════════╗";

        plugin.getLogger().info(border);
        if (isHighPriorityUpdate) {
            plugin.getLogger().info("🔥🔥🔥    КРИТИЧНОЕ ОБНОВЛЕНИЕ!    🔥🔥🔥");
            plugin.getLogger().info("🔥                                    🔥");
            plugin.getLogger().info("🔥  📁 ФАЙЛ ПЛАГИНА ИЗМЕНЕН!         🔥");
        } else {
            plugin.getLogger().info("║        ДОСТУПНО ОБНОВЛЕНИЕ!          ║");
            plugin.getLogger().info("║                                      ║");
        }

        plugin.getLogger().info(isHighPriorityUpdate ? "🔥  📦 Версия: " + currentVersion + " → " + latestVersion + "     🔥" : "║  📦 Текущая версия: " + currentVersion + getSpaces(currentVersion) + "║");
        plugin.getLogger().info(isHighPriorityUpdate ? "🔥  🆕 Новая: " + latestVersion + "               🔥" : "║  🆕 Новая версия: " + latestVersion + getSpaces(latestVersion) + "║");

        if (updateDate != null) {
            String formattedDate = formatUpdateDate(updateDate);
            plugin.getLogger().info(isHighPriorityUpdate ? "🔥  📅 Дата: " + formattedDate + "          🔥" : "║  📅 Дата: " + formattedDate + getSpaces(formattedDate) + "║");
        }

        if (isHighPriorityUpdate) {
            plugin.getLogger().info("🔥                                    🔥");
            plugin.getLogger().info("🔥  ⚠️  РЕКОМЕНДУЕТСЯ СРОЧНО ОБНОВИТЬ  🔥");
            plugin.getLogger().info("🔥  🤖 @rizonchik_bot                🔥");
            plugin.getLogger().info("🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥");
        } else {
            plugin.getLogger().info("║                                      ║");
            plugin.getLogger().info("║  🤖 @rizonchik_bot                   ║");
            plugin.getLogger().info("╚══════════════════════════════════════╝");
        }

        // 🎊 КРАСИВОЕ уведомление игрокам
        notifyModeratorsWithStyle(isHighPriorityUpdate, updateReason);

        // ⏰ Повторное уведомление
        long repeatDelay = isHighPriorityUpdate ? 600L : 36000L; // 30 секунд для критичных, 30 минут для обычных
        new BukkitRunnable() {
            @Override
            public void run() {
                if (updateAvailable) {
                    if (isHighPriorityUpdate) {
                        plugin.getLogger().warning("🔥 КРИТИЧНОЕ НАПОМИНАНИЕ: Обновите плагин СРОЧНО!");
                        playUpdateSound(true);
                    } else {
                        plugin.getLogger().info("🔔 Напоминание: доступно обновление " + currentVersion + " → " + latestVersion);
                    }
                }
            }
        }.runTaskLater(plugin, repeatDelay);
    }

    /**
     * 🔊 ВОСПРОИЗВОДИТ ЗВУК ОБНОВЛЕНИЯ
     */
    private void playUpdateSound(boolean isHighPriority) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("refontsearch.admin") ||
                    player.hasPermission("refontsearch.police") ||
                    player.isOp()) {

                if (isHighPriority) {
                    // 🚨 КРИТИЧНОЕ ОБНОВЛЕНИЕ - громкий звук
                    player.playSound(player.getLocation(), "entity.wither.spawn", 1.0f, 1.0f);

                    // Дополнительные звуки через задержку
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.playSound(player.getLocation(), "block.note_block.pling", 1.0f, 2.0f);
                    }, 10L);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.playSound(player.getLocation(), "block.note_block.pling", 1.0f, 1.5f);
                    }, 20L);

                } else {
                    // 🔔 ОБЫЧНОЕ ОБНОВЛЕНИЕ - приятный звук
                    player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0f, 1.0f);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.playSound(player.getLocation(), "block.note_block.chime", 0.7f, 1.2f);
                    }, 5L);
                }
            }
        }
    }

    /**
     * 🎨 СТИЛЬНЫЕ уведомления модераторам
     */
    private void notifyModeratorsWithStyle(boolean isHighPriority, String updateReason) {
        String updateMessage;

        if (isHighPriority) {
            // 🔥 КРИТИЧНОЕ ОБНОВЛЕНИЕ
            updateMessage = ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥\n" +
                    ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥        " + ChatColor.YELLOW + "КРИТИЧНОЕ ОБНОВЛЕНИЕ!" + ChatColor.RED + "        🔥🔥🔥\n" +
                    ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥                                        🔥🔥🔥\n" +
                    ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥  " + ChatColor.WHITE + "📁 ФАЙЛ ПЛАГИНА ИЗМЕНЕН!         " + ChatColor.RED + "🔥🔥🔥\n" +
                    ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥  " + ChatColor.WHITE + "🔧 " + currentVersion + " → " + latestVersion + "                    " + ChatColor.RED + "🔥🔥🔥\n";

            if (updateDate != null) {
                String formattedDate = formatUpdateDate(updateDate);
                updateMessage += ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥  " + ChatColor.WHITE + "📅 " + formattedDate + "                  " + ChatColor.RED + "🔥🔥🔥\n";
            }

            updateMessage += ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥                                        🔥🔥🔥\n" +
                    ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥  " + ChatColor.YELLOW + "⚠️  ОБНОВИТЕ СРОЧНО!             " + ChatColor.RED + "🔥🔥🔥\n" +
                    ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥  " + ChatColor.AQUA + "🤖 @rizonchik_bot               " + ChatColor.RED + "🔥🔥🔥\n" +
                    ChatColor.RED + "" + ChatColor.BOLD + "🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥🔥";

        } else {
            // ✨ ОБЫЧНОЕ ОБНОВЛЕНИЕ
            updateMessage = ChatColor.GOLD + "╔════════════════════════════════════════╗\n" +
                    ChatColor.GOLD + "║" + ChatColor.YELLOW + ChatColor.BOLD + "          ✨ ОБНОВЛЕНИЕ ПЛАГИНА!         " + ChatColor.RESET + ChatColor.GOLD + "║\n" +
                    ChatColor.GOLD + "║                                        ║\n" +
                    ChatColor.GOLD + "║  " + ChatColor.WHITE + "🔧 RefontSearch " + currentVersion + " → " + latestVersion + "            " + ChatColor.GOLD + "║\n";

            if (updateDate != null) {
                String formattedDate = formatUpdateDate(updateDate);
                updateMessage += ChatColor.GOLD + "║  " + ChatColor.WHITE + "📅 " + formattedDate + "                    " + ChatColor.GOLD + "║\n";
            }

            updateMessage += ChatColor.GOLD + "║                                        ║\n" +
                    ChatColor.GOLD + "║  " + ChatColor.WHITE + "📥 Скачайте обновление:               " + ChatColor.GOLD + "║\n" +
                    ChatColor.GOLD + "║  " + ChatColor.AQUA + "🤖 @rizonchik_bot                     " + ChatColor.GOLD + "║\n" +
                    ChatColor.GOLD + "║                                        ║\n" +
                    ChatColor.GOLD + "╚════════════════════════════════════════╝";
        }

        // Отправляем всем игрокам с правами
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("refontsearch.admin") ||
                    player.hasPermission("refontsearch.police") ||
                    player.isOp()) {

                player.sendMessage(updateMessage);

                // 🎊 ДОПОЛНИТЕЛЬНЫЕ ЭФФЕКТЫ для критичного обновления
                if (isHighPriority) {
                    // Отправляем title
                    player.sendTitle(
                            ChatColor.RED + "" + ChatColor.BOLD + "🔥 КРИТИЧНОЕ ОБНОВЛЕНИЕ! 🔥",
                            ChatColor.YELLOW + "Файл плагина изменен - обновите срочно!",
                            10, 60, 20
                    );

                    // Эффект частиц (если возможно)
                    try {
                        player.spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, player.getLocation(), 3);
                    } catch (Exception e) {
                        // Игнорируем если частицы не поддерживаются
                    }
                } else {
                    // Обычный title
                    player.sendTitle(
                            ChatColor.GOLD + "" + ChatColor.BOLD + "✨ Обновление доступно!",
                            ChatColor.WHITE + "RefontSearch " + currentVersion + " → " + latestVersion,
                            10, 40, 10
                    );
                }
            }
        }
    }

    private String getSpaces(String text) {
        int spaces = 12 - text.length();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            result.append(" ");
        }
        return result.toString();
    }

    private String formatUpdateDate(String dateString) {
        try {
            if (dateString.contains(" ")) {
                DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                LocalDateTime date = LocalDateTime.parse(dateString, inputFormatter);
                return date.format(outputFormatter);
            }
            return dateString;
        } catch (Exception e) {
            return dateString;
        }
    }

    /**
     * Принудительная проверка
     */
    public void forceCheckUpdates() {
        hasNotified = false;
        lastUpdateCheck = 0;
        checkForUpdates();
    }

    /**
     * Информация игроку
     */
    public void sendUpdateInfo(Player player) {
        if (fallbackModeActive) {
            player.sendMessage(ChatColor.YELLOW + "⚠️ Плагин в FALLBACK режиме - сайт rizonchik.ru недоступен");
            player.sendMessage(ChatColor.YELLOW + "🔓 Проверка обновлений временно недоступна");
            return;
        }

        if (updateAvailable) {
            String message = ChatColor.GOLD + "╔════════════════════════════════════════╗\n" +
                    ChatColor.GOLD + "║" + ChatColor.YELLOW + ChatColor.BOLD + "       ИНФОРМАЦИЯ ОБ ОБНОВЛЕНИИ        " + ChatColor.RESET + ChatColor.GOLD + "║\n" +
                    ChatColor.GOLD + "║                                        ║\n" +
                    ChatColor.GOLD + "║  " + ChatColor.WHITE + "📦 Текущая: " + currentVersion + "                     " + ChatColor.GOLD + "║\n" +
                    ChatColor.GOLD + "║  " + ChatColor.WHITE + "🆕 Доступна: " + latestVersion + "                    " + ChatColor.GOLD + "║\n";

            if (updateDate != null) {
                String formattedDate = formatUpdateDate(updateDate);
                message += ChatColor.GOLD + "║  " + ChatColor.WHITE + "📅 Дата: " + formattedDate + "                  " + ChatColor.GOLD + "║\n";
            }

            message += ChatColor.GOLD + "║                                        ║\n" +
                    ChatColor.GOLD + "║  " + ChatColor.WHITE + "📥 @rizonchik_bot                      " + ChatColor.GOLD + "║\n" +
                    ChatColor.GOLD + "║                                        ║\n" +
                    ChatColor.GOLD + "╚════════════════════════════════════════╝";

            player.sendMessage(message);
        } else {
            player.sendMessage(ChatColor.GREEN + "✅ Актуальная версия: " + currentVersion);
        }
    }

    // ====== ГЕТТЕРЫ ======

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getUpdateDate() {
        return updateDate;
    }

    public boolean isFallbackModeActive() {
        return fallbackModeActive;
    }

    public boolean isSiteAccessible() {
        return siteAccessible;
    }

    /**
     * Сброс состояния
     */
    public void reset() {
        updateAvailable = false;
        hasNotified = false;
        latestVersion = null;
        updateDate = null;
        lastUpdateCheck = 0;
        consecutiveFailures = 0;
        fallbackModeActive = false;
        siteAccessible = true;
        isHighPriorityUpdate = false;
        updateReason = "";
        lastUpdateResponse = null;
    }
}
package ru.lostone.refontsearch.protection;

import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;

public class IPProtection {

    private final JavaPlugin plugin;

    // URL для проверки лицензии через вашу базу данных
    private final String LICENSE_CHECK_URL = "https://rizonchik.ru/api/check_license_by_ip.php";

    // 🆘 FALLBACK РЕЖИМ
    private boolean fallbackModeActive = false;
    private int consecutiveFailures = 0;
    private static final int MAX_FAILURES_BEFORE_FALLBACK = 3;
    private long lastFallbackCheck = 0;
    private static final long FALLBACK_RECHECK_TIME = 1800000; // 30 минут

    // ========== НОВОЕ: СИСТЕМА АНТИ-СПАМА ==========
    private boolean lastCheckSuccess = false;
    private long lastSuccessLogTime = 0;
    private static final long SUCCESS_LOG_INTERVAL = 3600000; // 1 час между успешными логами
    private String lastServerName = "";
    // ===============================================

    public IPProtection(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Основная проверка сервера с FALLBACK режимом
     */
    public boolean validateServer() {
        try {
            // Проверяем локальные IP (для разработки)
            if (isLocalDevelopment()) {
                // Логируем только раз в час
                logOnce("🏠 Локальный режим разработки", true);
                return true;
            }

            // 🆘 FALLBACK: Если в fallback режиме, разрешаем работу
            if (fallbackModeActive) {
                long currentTime = System.currentTimeMillis();
                if ((currentTime - lastFallbackCheck) < FALLBACK_RECHECK_TIME) {
                    // НЕ ЛОГИРУЕМ каждый раз - только при активации
                    return true;
                } else {
                    // Пробуем восстановить соединение
                    plugin.getLogger().info("🔄 Попытка восстановления соединения с сайтом...");
                }
            }

            // Получаем текущий IP сервера
            String currentIP = getCurrentServerIP();
            if (currentIP == null) {
                plugin.getLogger().warning("❌ Не удалось определить IP сервера");
                return handleConnectionFailure();
            }

            // 🌐 ПРОВЕРЯЕМ ДОСТУПНОСТЬ САЙТА
            if (!checkSiteAccessibility()) {
                consecutiveFailures++;
                plugin.getLogger().warning("⚠️ Сайт rizonchik.ru недоступен (попытка " + consecutiveFailures + "/" + MAX_FAILURES_BEFORE_FALLBACK + ")");

                if (consecutiveFailures >= MAX_FAILURES_BEFORE_FALLBACK) {
                    return activateFallbackMode();
                }
                return false;
            }

            // Проверяем через базу данных
            boolean result = checkIPInDatabase(currentIP);

            if (result) {
                // 🌐 УСПЕХ - сбрасываем счетчики
                if (consecutiveFailures > 0 || fallbackModeActive) {
                    consecutiveFailures = 0;
                    if (fallbackModeActive) {
                        deactivateFallbackMode();
                    }
                }
                lastCheckSuccess = true;
                return true;
            } else {
                lastCheckSuccess = false;
                // Это отказ в авторизации, а не проблема с сайтом
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка проверки IP: " + e.getMessage());
            lastCheckSuccess = false;
            return handleConnectionFailure();
        }
    }

    /**
     * ========== НОВЫЙ МЕТОД: ЛОГИРОВАНИЕ БЕЗ СПАМА ==========
     */
    private void logOnce(String message, boolean isSuccess) {
        long currentTime = System.currentTimeMillis();

        if (isSuccess) {
            // Логируем успех только раз в час
            if ((currentTime - lastSuccessLogTime) >= SUCCESS_LOG_INTERVAL) {
                plugin.getLogger().info(message);
                lastSuccessLogTime = currentTime;
            }
        } else {
            // Ошибки логируем всегда
            plugin.getLogger().warning(message);
        }
    }

    /**
     * 🌐 ПРОВЕРЯЕТ ДОСТУПНОСТЬ САЙТА
     */
    private boolean checkSiteAccessibility() {
        try {
            URL url = new URL("https://rizonchik.ru");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "RefontSearch SiteCheck");

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode >= 200 && responseCode < 500; // Даже 404 означает что сайт доступен

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 🆘 ОБРАБАТЫВАЕТ ОШИБКИ СОЕДИНЕНИЯ
     */
    private boolean handleConnectionFailure() {
        consecutiveFailures++;

        if (consecutiveFailures >= MAX_FAILURES_BEFORE_FALLBACK) {
            return activateFallbackMode();
        }

        return false;
    }

    /**
     * 🆘 АКТИВИРУЕТ FALLBACK РЕЖИМ
     */
    private boolean activateFallbackMode() {
        fallbackModeActive = true;
        lastFallbackCheck = System.currentTimeMillis();

        plugin.getLogger().warning("╔══════════════════════════════════════╗");
        plugin.getLogger().warning("║      🆘 FALLBACK РЕЖИМ АКТИВЕН       ║");
        plugin.getLogger().warning("║                                      ║");
        plugin.getLogger().warning("║  Сайт rizonchik.ru недоступен        ║");
        plugin.getLogger().warning("║  Плагин работает БЕЗ проверок        ║");
        plugin.getLogger().warning("║  Проверка восстановления каждые 30м  ║");
        plugin.getLogger().warning("║                                      ║");
        plugin.getLogger().warning("╚══════════════════════════════════════╝");

        return true; // Разрешаем работу плагина
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
        plugin.getLogger().info("║  Возобновляется проверка IP          ║");
        plugin.getLogger().info("║                                      ║");
        plugin.getLogger().info("╚══════════════════════════════════════╝");
    }

    /**
     * Проверяет, является ли сервер локальным (для разработки)
     */
    private boolean isLocalDevelopment() {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();

            // Проверка на локальные адреса
            if (localIP.equals("127.0.0.1") ||
                    localIP.startsWith("192.168.") ||
                    localIP.startsWith("10.") ||
                    localIP.startsWith("172.16.") ||
                    localIP.equals("localhost")) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает текущий IP сервера (внешний или локальный)
     */
    private String getCurrentServerIP() {
        try {
            // Сначала пробуем получить внешний IP
            String externalIP = getExternalIP();
            if (externalIP != null && !externalIP.isEmpty()) {
                return externalIP;
            }

            // Если не получилось, используем локальный
            return InetAddress.getLocalHost().getHostAddress();

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка получения IP: " + e.getMessage());
            return null;
        }
    }

    /**
     * ПРОВЕРКА IP В БАЗЕ ДАННЫХ с обработкой fallback (БЕЗ СПАМА)
     */
    private boolean checkIPInDatabase(String ip) {
        try {
            String urlWithParams = LICENSE_CHECK_URL + "?" +
                    "ip=" + URLEncoder.encode(ip, "UTF-8") +
                    "&plugin=" + URLEncoder.encode("RefontSearch", "UTF-8") +
                    "&current_version=" + URLEncoder.encode(plugin.getDescription().getVersion(), "UTF-8");

            URL url = new URL(urlWithParams);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "RefontSearch/" + plugin.getDescription().getVersion());
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                try {
                    Gson gson = new Gson();
                    JsonObject jsonResponse = gson.fromJson(response.toString(), JsonObject.class);

                    if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean()) {
                        if (jsonResponse.has("data")) {
                            JsonObject data = jsonResponse.getAsJsonObject("data");

                            // ========== ИСПРАВЛЕНИЕ: УБИРАЕМ СПАМ ==========
                            if (data.has("server_name")) {
                                String serverName = data.get("server_name").getAsString();

                                // Логируем только:
                                // 1. При первом успехе
                                // 2. При смене статуса с ошибки на успех
                                // 3. Раз в час
                                if (!lastCheckSuccess || !serverName.equals(lastServerName)) {
                                    plugin.getLogger().info("✅ IP подтвержден: " + serverName);
                                    lastServerName = serverName;
                                } else {
                                    // ТИХАЯ проверка - НЕ ЛОГИРУЕМ
                                    logOnce("✅ IP подтвержден: " + serverName, true);
                                }
                            }
                            // ===============================================

                            return true;
                        }
                    } else {
                        plugin.getLogger().warning("❌ IP не авторизован");
                        return false; // Это отказ в авторизации, не проблема с сайтом
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("❌ Ошибка обработки ответа");
                    return false;
                }
            } else if (responseCode == 403) {
                plugin.getLogger().warning("❌ Доступ запрещен - IP не в базе данных");
                return false; // Это отказ в авторизации, не проблема с сайтом
            } else {
                plugin.getLogger().warning("❌ Ошибка сервера: " + responseCode);
                // Это может быть проблема с сайтом
                throw new Exception("Server error: " + responseCode);
            }

        } catch (java.net.ConnectException e) {
            plugin.getLogger().warning("❌ Нет соединения с сервером");
            throw new RuntimeException("Connection failed", e);
        } catch (java.net.SocketTimeoutException e) {
            plugin.getLogger().warning("❌ Таймаут соединения");
            throw new RuntimeException("Timeout", e);
        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка соединения с API");
            throw new RuntimeException("API error", e);
        }

        return false;
    }

    /**
     * Получение внешнего IP адреса
     */
    private String getExternalIP() {
        String[] ipServices = {
                "http://checkip.amazonaws.com/",
                "https://api.ipify.org",
                "http://icanhazip.com/"
        };

        for (String serviceUrl : ipServices) {
            try {
                URL url = new URL(serviceUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "RefontSearch/" + plugin.getDescription().getVersion());

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String ip = reader.readLine();
                reader.close();

                if (ip != null && !ip.trim().isEmpty()) {
                    return ip.trim();
                }
            } catch (Exception e) {
                // Пробуем следующий сервис
            }
        }
        return null;
    }

    /**
     * Проверка целостности плагина (БЕЗ СПАМА)
     */
    public boolean checkIntegrity() {
        try {
            String pluginName = plugin.getDescription().getName();
            String pluginVersion = plugin.getDescription().getVersion();

            // Проверяем имя плагина
            if (!"RefontSearch".equals(pluginName)) {
                plugin.getLogger().warning("❌ Неверное имя плагина: " + pluginName);
                return false;
            }

            // Проверяем, что версия существует и не пустая
            if (pluginVersion == null || pluginVersion.trim().isEmpty()) {
                plugin.getLogger().warning("❌ Версия плагина не найдена в plugin.yml");
                return false;
            }

            // ========== УБИРАЕМ СПАМ: НЕ ЛОГИРУЕМ КАЖДЫЙ РАЗ ==========
            // Логируем только при первом запуске или ошибках
            // plugin.getLogger().info("✅ Проверка целостности пройдена: " + pluginName + " v" + pluginVersion);
            // ==========================================================

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("❌ Ошибка проверки целостности: " + e.getMessage());
            return false;
        }
    }

    // ====== ГЕТТЕРЫ ======

    public boolean isFallbackModeActive() {
        return fallbackModeActive;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Принудительная проверка восстановления (для команд админа)
     */
    public void forceReconnectionCheck() {
        plugin.getLogger().info("🔄 Принудительная проверка восстановления соединения...");
        lastFallbackCheck = 0; // Сбрасываем таймер
        lastCheckSuccess = false; // Сбрасываем статус для принудительного лога
        validateServer(); // Проверяем заново
    }

    /**
     * Сброс fallback режима (для отладки)
     */
    public void resetFallbackMode() {
        fallbackModeActive = false;
        consecutiveFailures = 0;
        lastFallbackCheck = 0;
        lastCheckSuccess = false;
        lastSuccessLogTime = 0;
        plugin.getLogger().info("🔄 Fallback режим принудительно сброшен");
    }

    /**
     * ========== НОВЫЙ МЕТОД: ПОЛУЧЕНИЕ СТАТУСА ==========
     */
    public String getProtectionStatus() {
        if (fallbackModeActive) {
            return "§e⚠ FALLBACK режим";
        } else if (lastCheckSuccess) {
            return "§a✅ Авторизован";
        } else {
            return "§c❌ Не авторизован";
        }
    }
}
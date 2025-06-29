package ru.lostone.refontsearch;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.lostone.refontsearch.listener.DemorganMovementListener;
import ru.lostone.refontsearch.command.*;
import ru.lostone.refontsearch.listener.*;
import ru.lostone.refontsearch.manager.DemorganManager;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.manager.WantedManager;
import ru.lostone.refontsearch.manager.JailsManager;
import ru.lostone.refontsearch.integration.RefontSearchExpansion;
import ru.lostone.refontsearch.updater.Updater;
import ru.lostone.refontsearch.protection.IPProtection;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public final class RefontSearch extends JavaPlugin {

    private static RefontSearch instance;
    private Location jailLocation;
    private Location unjailLocation;
    private JailsManager jailsManager;
    private Updater updater;
    private IPProtection ipProtection;

    // Защищенные данные
    private static final String ENCRYPTED_DOMAIN = "aHR0cHM6Ly9yaXpvbmNoaWsucnU=";
    private String serverIP = null;
    private String licenseKey = null;
    private boolean isLocalServer = false;
    private boolean isValidated = false;

    // 🆘 FALLBACK РЕЖИМ для лицензий
    private boolean licenseFallbackActive = false;
    private int licenseFallureCount = 0;
    private static final int MAX_LICENSE_FAILURES = 3;

    // Кеширование лицензии
    private long lastLicenseCheck = 0;
    private boolean lastLicenseResult = false;
    private static final long LICENSE_CACHE_TIME = 300000; // 5 минут кеш
    private static final int MAX_RETRIES = 2; // Уменьшили для быстроты
    private static final long RETRY_DELAY = 3000; // 3 секунды

    @Override
    public void onEnable() {
        instance = this;

        // Инициализируем защиту IP
        ipProtection = new IPProtection(this);

        // Получаем IP сервера
        serverIP = getServerIP();

        // Проверяем, является ли IP локальным
        isLocalServer = isLocalIP(serverIP);

        // Красивое сообщение о начале проверки
        if (isLocalServer) {
            getLogger().info("🏠 Локальный режим разработки");
            continueLoading();
        } else {
            // Для реального сервера проверяем лицензию
            getLogger().info("🔐 Проверка лицензии...");

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                boolean licenseValid = validateLicenseWithRetries();
                boolean ipValid = ipProtection.validateServer();
                boolean integrityValid = ipProtection.checkIntegrity();

                // 🆘 FALLBACK: Если IP protection в fallback режиме - разрешаем запуск
                if (ipProtection.isFallbackModeActive()) {
                    getLogger().warning("🆘 IP Protection в FALLBACK режиме - плагин запускается без полной проверки");
                    Bukkit.getScheduler().runTask(this, this::continueLoading);
                    return;
                }

                // 🆘 FALLBACK: Если лицензия в fallback режиме - разрешаем запуск
                if (licenseFallbackActive) {
                    getLogger().warning("🆘 License в FALLBACK режиме - плагин запускается без полной проверки");
                    Bukkit.getScheduler().runTask(this, this::continueLoading);
                    return;
                }

                if (licenseValid && ipValid && integrityValid) {
                    Bukkit.getScheduler().runTask(this, this::continueLoading);
                } else {
                    getLogger().severe("❌ Проверки безопасности не пройдены");
                    if (!licenseValid) getLogger().severe("  • Лицензия: ❌");
                    if (!ipValid) getLogger().severe("  • IP защита: ❌");
                    if (!integrityValid) getLogger().severe("  • Целостность: ❌");
                    Bukkit.getScheduler().runTask(this, this::disablePlugin);
                }
            });
        }
    }

    /**
     * Проверка лицензии с FALLBACK режимом
     */
    private boolean validateLicenseWithRetries() {
        // Проверяем кеш
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLicenseCheck < LICENSE_CACHE_TIME && lastLicenseResult) {
            getLogger().info("✅ Лицензия подтверждена (кеш)");
            return true;
        }

        // 🆘 FALLBACK: Если уже в fallback режиме
        if (licenseFallbackActive) {
            getLogger().info("🆘 Лицензия в FALLBACK режиме");
            return true;
        }

        // Пробуем несколько раз
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                boolean result = validateLicense();

                if (result) {
                    lastLicenseCheck = currentTime;
                    lastLicenseResult = true;
                    // Сбрасываем счетчик неудач
                    licenseFallureCount = 0;
                    if (licenseFallbackActive) {
                        deactivateLicenseFallback();
                    }
                    return true;
                }
            } catch (Exception e) {
                getLogger().warning("❌ Попытка " + attempt + " проверки лицензии неудачна: " + e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 🆘 ВСЕ ПОПЫТКИ НЕУДАЧНЫ - проверяем fallback
        licenseFallureCount++;

        if (licenseFallureCount >= MAX_LICENSE_FAILURES) {
            return activateLicenseFallback();
        }

        // Проверяем устаревший кеш как последний шанс
        if (lastLicenseResult && (currentTime - lastLicenseCheck) < LICENSE_CACHE_TIME * 3) {
            getLogger().warning("⚠️ Используется резервный кеш лицензии");
            return true;
        }

        return false;
    }

    /**
     * 🆘 АКТИВИРУЕТ FALLBACK РЕЖИМ ДЛЯ ЛИЦЕНЗИИ
     */
    private boolean activateLicenseFallback() {
        licenseFallbackActive = true;

        getLogger().warning("╔══════════════════════════════════════╗");
        getLogger().warning("║    🆘 LICENSE FALLBACK АКТИВЕН       ║");
        getLogger().warning("║                                      ║");
        getLogger().warning("║  Сервер лицензий недоступен          ║");
        getLogger().warning("║  Плагин работает БЕЗ проверки        ║");
        getLogger().warning("║  лицензии до восстановления связи    ║");
        getLogger().warning("║                                      ║");
        getLogger().warning("╚══════════════════════════════════════╝");

        return true;
    }

    /**
     * ✅ ДЕАКТИВИРУЕТ LICENSE FALLBACK РЕЖИМ
     */
    private void deactivateLicenseFallback() {
        licenseFallbackActive = false;
        licenseFallureCount = 0;

        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║    ✅ ЛИЦЕНЗИЯ ВОССТАНОВЛЕНА!        ║");
        getLogger().info("║                                      ║");
        getLogger().info("║  Связь с сервером лицензий           ║");
        getLogger().info("║  восстановлена                       ║");
        getLogger().info("║                                      ║");
        getLogger().info("╚══════════════════════════════════════╝");
    }

    /**
     * 🌐 ПРОВЕРЯЕТ ДОСТУПНОСТЬ САЙТА ДЛЯ ЛИЦЕНЗИИ
     */
    private boolean checkLicenseSiteAccessibility() {
        try {
            String domain = new String(java.util.Base64.getDecoder().decode(ENCRYPTED_DOMAIN));
            URL url = new URL(domain);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "RefontSearch LicenseCheck");

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode >= 200 && responseCode < 500;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Проверяет, является ли IP локальным
     */
    private boolean isLocalIP(String ip) {
        if (ip == null) return true;

        // Проверка на localhost
        if (ip.equals("localhost") || ip.equals("127.0.0.1") || ip.equals("::1")) {
            return true;
        }

        // Проверка на локальную сеть
        if (ip.startsWith("192.168.") ||
                ip.startsWith("10.") ||
                ip.startsWith("172.16.") ||
                ip.startsWith("172.17.") ||
                ip.startsWith("172.18.") ||
                ip.startsWith("172.19.") ||
                ip.startsWith("172.20.") ||
                ip.startsWith("172.21.") ||
                ip.startsWith("172.22.") ||
                ip.startsWith("172.23.") ||
                ip.startsWith("172.24.") ||
                ip.startsWith("172.25.") ||
                ip.startsWith("172.26.") ||
                ip.startsWith("172.27.") ||
                ip.startsWith("172.28.") ||
                ip.startsWith("172.29.") ||
                ip.startsWith("172.30.") ||
                ip.startsWith("172.31.")) {
            return true;
        }

        return false;
    }

    /**
     * ПРОВЕРКА ЛИЦЕНЗИИ с обработкой fallback
     */
    private boolean validateLicense() {
        try {
            if (serverIP == null) {
                throw new Exception("IP сервера недоступен");
            }

            // 🌐 СНАЧАЛА проверяем доступность сайта
            if (!checkLicenseSiteAccessibility()) {
                throw new Exception("Сайт лицензий недоступен");
            }

            String domain = new String(java.util.Base64.getDecoder().decode(ENCRYPTED_DOMAIN));
            String apiUrl = domain + "/api/check_license_by_ip.php";

            String urlWithParams = apiUrl + "?" +
                    "ip=" + URLEncoder.encode(serverIP, "UTF-8") +
                    "&plugin=" + URLEncoder.encode("RefontSearch", "UTF-8") +
                    "&current_version=" + URLEncoder.encode(getDescription().getVersion(), "UTF-8");

            URL url = new URL(urlWithParams);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "RefontSearch/" + getDescription().getVersion());
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

                            // Проверяем статус IP
                            if (data.has("ip_found") && data.get("ip_found").getAsBoolean()) {
                                // Проверяем активность лицензии
                                if (data.has("license_active") && data.get("license_active").getAsBoolean()) {

                                    // Краткий лог успеха
                                    String serverName = data.has("server_name") ?
                                            data.get("server_name").getAsString() : "Unknown";
                                    getLogger().info("✅ Сервер авторизован: " + serverName);

                                    return true;
                                } else {
                                    getLogger().warning("❌ Лицензия неактивна");
                                    return false; // Это отказ в авторизации, не проблема с сайтом
                                }
                            } else {
                                getLogger().warning("❌ IP не найден в базе данных");
                                return false; // Это отказ в авторизации, не проблема с сайтом
                            }
                        } else {
                            throw new Exception("Неверный формат ответа API");
                        }
                    } else {
                        String message = jsonResponse.has("message") ?
                                jsonResponse.get("message").getAsString() : "Неизвестная ошибка";
                        getLogger().warning("❌ " + message);
                        return false; // Это отказ в авторизации, не проблема с сайтом
                    }
                } catch (Exception e) {
                    throw new Exception("Ошибка обработки ответа сервера");
                }
            } else if (responseCode == 403) {
                getLogger().warning("❌ IP не авторизован. Добавьте через @rizonchik_bot");
                return false; // Это отказ в авторизации, не проблема с сайтом
            } else if (responseCode == 404) {
                throw new Exception("API недоступен");
            } else {
                throw new Exception("Ошибка сервера: " + responseCode);
            }

        } catch (java.net.ConnectException e) {
            throw new RuntimeException("Нет соединения с сервером лицензий", e);
        } catch (java.net.SocketTimeoutException e) {
            throw new RuntimeException("Таймаут соединения", e);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка проверки лицензии: " + e.getMessage(), e);
        }
    }

    /**
     * Получает IP сервера (внешний или локальный) с кешированием
     */
    private String cachedServerIP = null;
    private long lastIPCheck = 0;
    private static final long IP_CACHE_TIME = 600000; // 10 минут кеш для IP

    public String getServerIP() {
        long currentTime = System.currentTimeMillis();

        // Используем кешированный IP если он свежий
        if (cachedServerIP != null && (currentTime - lastIPCheck) < IP_CACHE_TIME) {
            return cachedServerIP;
        }

        try {
            String externalIP = getExternalIP();
            if (externalIP != null) {
                cachedServerIP = externalIP;
                lastIPCheck = currentTime;
                return externalIP;
            }

            String localIP = InetAddress.getLocalHost().getHostAddress();
            cachedServerIP = localIP;
            lastIPCheck = currentTime;
            return localIP;
        } catch (Exception e) {
            getLogger().warning("⚠️ Ошибка получения IP: " + e.getMessage());
            // Возвращаем кешированный IP если есть
            return cachedServerIP;
        }
    }

    /**
     * Получает внешний IP через сервисы
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
                connection.setRequestProperty("User-Agent", "RefontSearch/" + getDescription().getVersion());

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String ip = reader.readLine();
                reader.close();

                if (ip != null && !ip.trim().isEmpty()) {
                    return ip.trim();
                }
            } catch (Exception e) {
                // Тихо пробуем следующий сервис
            }
        }
        return null;
    }

    /**
     * Продолжает загрузку плагина после проверки лицензии
     */
    private void continueLoading() {
        isValidated = true;

        DemorganManager.init(this);
        DemorganManager.loadDemorganData();

        // Создаем/загружаем конфиг
        saveDefaultConfig();

        // Красивое сообщение о загрузке
        if (isLocalServer) {
            getLogger().info("╔══════════════════════════════════════╗");
            getLogger().info("║          РЕЖИМ РАЗРАБОТКИ            ║");
            getLogger().info("║  🏠 Локальный сервер                ║");
            getLogger().info("╚══════════════════════════════════════╝");
        } else if (ipProtection.isFallbackModeActive() || licenseFallbackActive) {
            getLogger().warning("╔══════════════════════════════════════╗");
            getLogger().warning("║          🆘 FALLBACK РЕЖИМ           ║");
            getLogger().warning("║  Плагин работает БЕЗ полной проверки ║");
            if (ipProtection.isFallbackModeActive()) {
                getLogger().warning("║  • IP защита: FALLBACK               ║");
            }
            if (licenseFallbackActive) {
                getLogger().warning("║  • Лицензия: FALLBACK                ║");
            }
            getLogger().warning("╚══════════════════════════════════════╝");
        } else {
            getLogger().info("╔══════════════════════════════════════╗");
            getLogger().info("║            RefontSearch              ║");
            getLogger().info("║  ✅ Система защиты активна           ║");
            getLogger().info("╚══════════════════════════════════════╝");
        }

        // Обновляем конфиг
        updateConfig();

        // Инициализация менеджеров
        WantedManager.init(this);
        JailManager.init(this);
        jailsManager = new JailsManager(this);

        // Загружаем координаты
        loadJailLocations();

        // Регистрируем PlaceholderAPI если доступен
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RefontSearchExpansion(this).register();
            getLogger().info("🔗 PlaceholderAPI интегрирован");
        }

        // Регистрация команд
        registerCommands();

        // Регистрация слушателей
        registerListeners();

        // Инициализация и запуск проверки обновлений (если не локальный сервер)
        if (!isLocalServer) {
            updater = new Updater(this);
            updater.startUpdateChecker();

            // Периодическая проверка каждые 30 минут
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (!validateLicenseWithRetries()) {
                    getLogger().warning("⚠️ Проблема с лицензией при периодической проверке");
                }
                // Также проверяем IP protection
                ipProtection.validateServer();
            }, 36000L, 36000L);
        }

        // Финальное сообщение
        getLogger().info("╔══════════════════════════════════════╗");
        getLogger().info("║  🎉 RefontSearch загружен успешно!   ║");
        getLogger().info("║  👮 Полицейская система готова       ║");
        getLogger().info("╚══════════════════════════════════════╝");
    }

    /**
     * Загружает координаты тюрем
     */
    private void loadJailLocations() {
        String startStr = getConfig().getString("jail.location.start", "");
        if (!startStr.isEmpty()) {
            jailLocation = parseLocation(startStr);
        }
        String endStr = getConfig().getString("jail.location.end", "");
        if (!endStr.isEmpty()) {
            unjailLocation = parseLocation(endStr);
        }
    }

    /**
     * Регистрирует команды плагина
     */
    private void registerCommands() {
        getCommand("unwanted").setExecutor(new UnwantedCommand());
        getCommand("unwanted").setTabCompleter(new UnwantedCommand());
        getCommand("policefind").setExecutor(new PoliceFindCommand());
        getCommand("policefind").setTabCompleter(new PoliceFindCommand());
        getCommand("policecall").setExecutor(new PoliceCallCommand());
        getCommand("policeaccept").setExecutor(new PoliceAcceptCommand());
        getCommand("wanted").setExecutor(new WantedCommand());
        getCommand("arrest").setExecutor(new ArrestCommand());
        getCommand("arrest").setTabCompleter(new ArrestCommand());

        if (getConfig().getBoolean("baton.enabled", true)) {
            getCommand("wanteditems").setExecutor(new WantedItemsCommand());
            Bukkit.getPluginManager().registerEvents(new BatonMechanicListener(this), this);
        }

        if (getConfig().getBoolean("demorgan.enabled", true)) {
            getCommand("demorgan").setExecutor(new DemorganCommand(this));
            getCommand("demorgan").setTabCompleter(new DemorganCommand(this));
            getCommand("undemorgan").setExecutor(new UndemorganCommand(this));
            getCommand("undemorgan").setTabCompleter(new UndemorganCommand(this));
            getCommand("demorganlist").setExecutor(new DemorganListCommand(this));
            getCommand("setdemorgan").setExecutor(new SetDemorganCommand(this));
        }

        getCommand("setjail").setExecutor(new SetJailCommand());
        getCommand("unjail").setExecutor(new UnjailCommand());
        getCommand("unjail").setTabCompleter(new UnjailCommand());
        getCommand("updatestars").setExecutor(new UpdateStarsCommand());
        getCommand("updatestars").setTabCompleter(new UpdateStarsCommand());
        getCommand("jails").setExecutor(new JailsCommand(this));
        getCommand("jails").setTabCompleter(new JailsCommand(this));
    }

    /**
     * Регистрирует слушатели событий
     */
    private void registerListeners() {

        if (getConfig().getBoolean("demorgan.enabled", true)) {
            Bukkit.getPluginManager().registerEvents(new DemorganMovementListener(this), this);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WantedInventoryListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerMovementListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
    }

    /**
     * Отключает плагин при ошибке лицензии (только если НЕ fallback режим)
     */
    private void disablePlugin() {
        getLogger().severe("╔══════════════════════════════════════╗");
        getLogger().severe("║          ОШИБКА АКТИВАЦИИ            ║");
        getLogger().severe("║                                      ║");
        getLogger().severe("║  ❌ Недействительная лицензия        ║");
        getLogger().severe("║  🌐 IP сервера: " + formatIP(serverIP) + "║");
        getLogger().severe("║                                      ║");
        getLogger().severe("║  📞 Поддержка:                      ║");
        getLogger().severe("║  Discord: rizonchik                 ║");
        getLogger().severe("║  Telegram: @orythix                 ║");
        getLogger().severe("║  Bot: @rizonchik_bot                ║");
        getLogger().severe("║                                      ║");
        getLogger().severe("╚══════════════════════════════════════╝");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.getPluginManager().disablePlugin(this);
        }, 100L);
    }

    /**
     * Форматирует IP для красивого отображения
     */
    private String formatIP(String ip) {
        if (ip == null) return "Неизвестно        ";

        // Добавляем пробелы для форматирования
        int spaces = 15 - ip.length();
        StringBuilder result = new StringBuilder(ip);
        for (int i = 0; i < spaces; i++) {
            result.append(" ");
        }
        return result.toString();
    }

    /**
     * Обновляет конфигурацию плагина
     */
    private void updateConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);

            Reader defaultConfigStream = new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8);
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigStream);

            boolean changed = false;

            for (String key : defaultConfig.getKeys(true)) {
                if (!currentConfig.contains(key)) {
                    currentConfig.set(key, defaultConfig.get(key));
                    changed = true;
                }
            }

            if (changed) {
                currentConfig.save(configFile);
                reloadConfig();
                getLogger().info("⚙️ Конфигурация обновлена");
            }
        } catch (Exception e) {
            getLogger().warning("⚠️ Ошибка обновления конфигурации");
        }
    }

    @Override
    public void onDisable() {
        DemorganManager.onDisable();
        if (isValidated) {
            JailManager.onDisable();
            getLogger().info("╔══════════════════════════════════════╗");
            getLogger().info("║            RefontSearch              ║");
            getLogger().info("║  🔴 Плагин корректно отключен        ║");
            getLogger().info("║  👋 До свидания!                    ║");
            getLogger().info("╚══════════════════════════════════════╝");
        }
    }

    // ====== ГЕТТЕРЫ И СЕТТЕРЫ ======

    public static RefontSearch getInstance() {
        return instance;
    }

    public boolean isValidated() {
        return isValidated;
    }

    public String getLicenseKey() {
        return licenseKey;
    }

    public boolean isLocalServer() {
        return isLocalServer;
    }

    public boolean isLicenseFallbackActive() {
        return licenseFallbackActive;
    }

    public Updater getUpdater() {
        return updater;
    }

    public IPProtection getIpProtection() {
        return ipProtection;
    }

    public Location getJailLocation() {
        return jailLocation;
    }

    public void setJailLocation(Location loc) {
        this.jailLocation = loc;
    }

    public Location getUnjailLocation() {
        return unjailLocation;
    }

    public void setUnjailLocation(Location loc) {
        this.unjailLocation = loc;
    }

    public JailsManager getJailsManager() {
        return jailsManager;
    }

    /**
     * Парсит строку локации в объект Location
     */
    private Location parseLocation(String locStr) {
        String[] parts = locStr.split(";");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Сохраняет координаты тюрем в конфиг
     */
    public void saveJailLocations() {
        if (jailLocation != null) {
            String locStr = jailLocation.getWorld().getName() + ";" + jailLocation.getX() + ";" + jailLocation.getY() + ";" + jailLocation.getZ();
            getConfig().set("jail.location.start", locStr);
        }
        if (unjailLocation != null) {
            String locStr = unjailLocation.getWorld().getName() + ";" + unjailLocation.getX() + ";" + unjailLocation.getY() + ";" + unjailLocation.getZ();
            getConfig().set("jail.location.end", locStr);
        }
        saveConfig();
    }

    /**
     * Принудительная очистка кеша лицензии (для отладки)
     */
    public void clearLicenseCache() {
        lastLicenseCheck = 0;
        lastLicenseResult = false;
        cachedServerIP = null;
        lastIPCheck = 0;
        licenseFallbackActive = false;
        licenseFallureCount = 0;
        getLogger().info("🔄 Кеш лицензии и IP очищен, fallback режимы сброшены");
    }

    /**
     * Принудительная проверка восстановления всех систем
     */
    public void forceReconnectionCheck() {
        getLogger().info("🔄 Принудительная проверка восстановления всех систем...");

        // Сбрасываем кеши
        clearLicenseCache();

        // Проверяем IP protection
        ipProtection.forceReconnectionCheck();

        // Проверяем лицензию
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean licenseValid = validateLicenseWithRetries();
            if (licenseValid) {
                getLogger().info("✅ Проверка лицензии успешна");
            } else {
                getLogger().warning("❌ Проверка лицензии неудачна");
            }
        });
    }
}
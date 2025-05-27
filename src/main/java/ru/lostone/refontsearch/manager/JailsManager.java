package ru.lostone.refontsearch.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.model.Jail;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class JailsManager {
    private final RefontSearch plugin;
    private final Map<String, Jail> jails = new HashMap<>();
    private final Map<UUID, String> playerJails = new HashMap<>();  // Хранит информацию о том, в какой тюрьме сидит игрок

    public JailsManager(RefontSearch plugin) {
        this.plugin = plugin;
        loadJails();
    }

    public void loadJails() {
        jails.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection jailsSection = config.getConfigurationSection("jails");

        if (jailsSection == null) {
            return;
        }

        for (String jailName : jailsSection.getKeys(false)) {
            ConfigurationSection jailSection = jailsSection.getConfigurationSection(jailName);
            if (jailSection == null) continue;

            String startLocStr = jailSection.getString("location.start");
            String endLocStr = jailSection.getString("location.end");
            double radius = jailSection.getDouble("radius", 10.0);

            Location startLoc = parseLocation(startLocStr);
            Location endLoc = parseLocation(endLocStr);

            if (startLoc != null) {
                Jail jail = new Jail(jailName, startLoc, endLoc, radius);
                jails.put(jailName, jail);
            }
        }
    }

    private Location parseLocation(String locStr) {
        if (locStr == null || locStr.isEmpty()) {
            return null;
        }

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

    // Сохранение тюрем в конфиг
    public void saveJails() {
        FileConfiguration config = plugin.getConfig();

        // Очищаем существующие тюрьмы в конфиге
        config.set("jails", null);

        for (Map.Entry<String, Jail> entry : jails.entrySet()) {
            String jailName = entry.getKey();
            Jail jail = entry.getValue();

            String startPath = "jails." + jailName + ".location.start";
            String endPath = "jails." + jailName + ".location.end";
            String radiusPath = "jails." + jailName + ".radius";

            if (jail.getJailLocation() != null) {
                Location loc = jail.getJailLocation();
                String locStr = loc.getWorld().getName() + ";" +
                        loc.getX() + ";" +
                        loc.getY() + ";" +
                        loc.getZ();
                config.set(startPath, locStr);
            }

            if (jail.getReleaseLocation() != null) {
                Location loc = jail.getReleaseLocation();
                String locStr = loc.getWorld().getName() + ";" +
                        loc.getX() + ";" +
                        loc.getY() + ";" +
                        loc.getZ();
                config.set(endPath, locStr);
            }

            config.set(radiusPath, jail.getRadius());
        }

        plugin.saveConfig();
    }

    // Добавление новой тюрьмы
    public boolean addJail(String name, Location jailLocation, Location releaseLocation, double radius) {
        if (jails.containsKey(name)) {
            return false; // Тюрьма с таким названием уже существует
        }

        Jail jail = new Jail(name, jailLocation, releaseLocation, radius);
        jails.put(name, jail);
        saveJails();
        return true;
    }

    // Удаление тюрьмы
    public boolean removeJail(String name) {
        if (!jails.containsKey(name)) {
            return false; // Тюрьма не найдена
        }

        jails.remove(name);
        saveJails();
        return true;
    }

    // Получение тюрьмы по имени
    public Jail getJail(String name) {
        return jails.get(name);
    }

    // Получение случайной тюрьмы
    public Jail getRandomJail() {
        if (jails.isEmpty()) {
            return null;
        }

        int size = jails.size();
        int index = new Random().nextInt(size);

        int i = 0;
        for (Jail jail : jails.values()) {
            if (i == index) {
                return jail;
            }
            i++;
        }

        return null;
    }

    // Получение всех тюрем
    public Map<String, Jail> getAllJails() {
        return jails;
    }

    // Запоминаем, в какой тюрьме сидит игрок
    public void setPlayerJail(UUID playerId, String jailName) {
        playerJails.put(playerId, jailName);
    }

    // Получаем тюрьму, в которой сидит игрок
    public Jail getPlayerJail(UUID playerId) {
        String jailName = playerJails.get(playerId);
        if (jailName == null) {
            return null;
        }
        return getJail(jailName);
    }

    // Удаляем информацию о тюрьме игрока
    public void removePlayerJail(UUID playerId) {
        playerJails.remove(playerId);
    }
}
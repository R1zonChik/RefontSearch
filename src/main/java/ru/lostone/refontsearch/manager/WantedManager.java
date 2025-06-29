package ru.lostone.refontsearch.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class WantedManager {
    private static File dataFile;
    private static FileConfiguration dataConfig;
    private static Map<UUID, Long> lastWantedTime = new HashMap<>();

    public static void init(RefontSearch plugin) {
        dataFile = new File(plugin.getDataFolder(), "wanted.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public static void setWanted(UUID uuid, int stars, String reason, String issuedBy) {
        // Старый метод для обратной совместимости
        WantedData data = new WantedData(stars, reason, issuedBy);
        data.setPlayerName(uuid.toString());
        data.setDisplayName(uuid.toString());
        setWanted(uuid, data);
    }

    public static void setWanted(UUID uuid, WantedData data) {
        // Новый метод
        String path = "wanted." + uuid.toString();
        dataConfig.set(path + ".stars", data.getStars());
        dataConfig.set(path + ".reason", data.getReason());
        dataConfig.set(path + ".article", data.getArticle());
        dataConfig.set(path + ".officer", data.getOfficer());
        dataConfig.set(path + ".playerName", data.getPlayerName());
        dataConfig.set(path + ".displayName", data.getDisplayName());
        dataConfig.set(path + ".timestamp", data.getTimestamp());
        dataConfig.set(path + ".date", data.getDate());

        lastWantedTime.put(uuid, System.currentTimeMillis());
        saveData();
    }

    public static boolean isWanted(UUID uuid) {
        return dataConfig.contains("wanted." + uuid.toString());
    }

    public static WantedData getWanted(UUID uuid) {
        if (!isWanted(uuid)) return null;

        String path = "wanted." + uuid.toString();
        int stars = dataConfig.getInt(path + ".stars");
        String reason = dataConfig.getString(path + ".reason");
        String article = dataConfig.getString(path + ".article", "Не указана");
        String officer = dataConfig.getString(path + ".officer");
        String playerName = dataConfig.getString(path + ".playerName", uuid.toString());
        String displayName = dataConfig.getString(path + ".displayName", playerName);
        long timestamp = dataConfig.getLong(path + ".timestamp", System.currentTimeMillis());

        WantedData data = new WantedData(playerName, displayName, stars, reason, article, officer);
        data.setTimestamp(timestamp);

        // Устанавливаем дату из конфига если есть
        String savedDate = dataConfig.getString(path + ".date");
        if (savedDate != null) {
            data.setDate(savedDate);
        }

        return data;
    }

    public static void removeWanted(UUID uuid) {
        dataConfig.set("wanted." + uuid.toString(), null);
        lastWantedTime.remove(uuid);
        saveData();
    }

    public static boolean canSetWanted(UUID issuer) {
        if (!lastWantedTime.containsKey(issuer)) return true;

        long cooldown = RefontSearch.getInstance().getConfig().getLong("wantedCooldown", 30) * 1000;
        return System.currentTimeMillis() - lastWantedTime.get(issuer) >= cooldown;
    }

    public static Map<UUID, WantedData> getAllWanted() {
        Map<UUID, WantedData> wantedPlayers = new HashMap<>();
        if (!dataConfig.contains("wanted")) return wantedPlayers;

        for (String uuidStr : dataConfig.getConfigurationSection("wanted").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                WantedData data = getWanted(uuid);
                if (data != null) {
                    wantedPlayers.put(uuid, data);
                }
            } catch (IllegalArgumentException ignored) {}
        }

        return wantedPlayers;
    }

    private static void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
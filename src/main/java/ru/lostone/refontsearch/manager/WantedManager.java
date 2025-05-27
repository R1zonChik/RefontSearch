package ru.lostone.refontsearch.manager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;

public class WantedManager {
    private static Map<UUID, WantedData> wantedMap = new HashMap<>();
    private static File dataFile;
    private static FileConfiguration dataConfig;

    public static void init(RefontSearch plugin) {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    public static void loadData() {
        wantedMap.clear();
        if (dataConfig.contains("wanted")) {
            for (String uuidStr : dataConfig.getConfigurationSection("wanted").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "wanted." + uuidStr;
                int stars = dataConfig.getInt(path + ".stars");
                String reason = dataConfig.getString(path + ".reason");
                String date = dataConfig.getString(path + ".date");
                String issuer = dataConfig.getString(path + ".issuer");
                WantedData data = new WantedData(stars, reason, issuer);
                data.setDate(date);
                wantedMap.put(uuid, data);
            }
        }
    }

    public static void saveData() {
        dataConfig.set("wanted", null);
        for (Map.Entry<UUID, WantedData> entry : wantedMap.entrySet()) {
            String path = "wanted." + entry.getKey().toString();
            WantedData data = entry.getValue();
            dataConfig.set(path + ".stars", data.getStars());
            dataConfig.set(path + ".reason", data.getReason());
            dataConfig.set(path + ".date", data.getDate());
            dataConfig.set(path + ".issuer", data.getIssuedBy());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addWanted(UUID target, WantedData data) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        data.setDate(sdf.format(new Date()));
        wantedMap.put(target, data);
        saveData();
    }

    public static WantedData getWanted(UUID target) {
        return wantedMap.get(target);
    }

    public static boolean isWanted(UUID target) {
        return wantedMap.containsKey(target);
    }

    public static void removeWanted(UUID target) {
        wantedMap.remove(target);
        saveData();
    }

    public static Map<UUID, WantedData> getAllWanted() {
        return wantedMap;
    }
}
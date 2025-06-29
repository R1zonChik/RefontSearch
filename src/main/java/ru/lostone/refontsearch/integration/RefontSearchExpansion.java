package ru.lostone.refontsearch.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.manager.WantedManager;

public class RefontSearchExpansion extends PlaceholderExpansion {

    private final RefontSearch plugin;

    public RefontSearchExpansion(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "refontsearch";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }

        // %refontsearch_wanted%
        if (params.equals("wanted")) {
            return WantedManager.isWanted(player.getUniqueId()) ? "true" : "false";
        }

        // %refontsearch_stars%
        if (params.equals("stars")) {
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                return String.valueOf(data.getStars());
            }
            return "0";
        }

        // %refontsearch_stars_display%
        if (params.equals("stars_display")) {
            int stars = 0;
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                stars = data.getStars();
            }
            return getStarsDisplay(stars);
        }

        // %refontsearch_stars_filled%
        if (params.equals("stars_filled")) {
            int stars = 0;
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                stars = data.getStars();
            }
            return getFilledStars(stars);
        }

        // %refontsearch_stars_empty%
        if (params.equals("stars_empty")) {
            int stars = 0;
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                stars = data.getStars();
            }
            return getEmptyStars(stars);
        }

        // %refontsearch_reason%
        if (params.equals("reason")) {
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                return data.getReason();
            }
            return plugin.getConfig().getString("placeholders.default_values.reason", "Нет");
        }

        // %refontsearch_article%
        if (params.equals("article")) {
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                return data.getArticle() != null ? data.getArticle() : plugin.getConfig().getString("placeholders.default_values.article", "Не указана");
            }
            return plugin.getConfig().getString("placeholders.default_values.article", "Нет");
        }

        // %refontsearch_officer%
        if (params.equals("officer")) {
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                return data.getOfficer() != null ? data.getOfficer() : plugin.getConfig().getString("placeholders.default_values.officer", "Неизвестно");
            }
            return plugin.getConfig().getString("placeholders.default_values.officer", "Нет");
        }

        // %refontsearch_display_name%
        if (params.equals("display_name")) {
            String placeholder = plugin.getConfig().getString("display.placeholder", "%player_name%");
            if (placeholder.equals("%player_name%")) {
                return player.getName();
            }
            // Если есть другой плейсхолдер, обрабатываем его
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);
        }

        // %refontsearch_date%
        if (params.equals("date")) {
            if (WantedManager.isWanted(player.getUniqueId())) {
                WantedData data = WantedManager.getWanted(player.getUniqueId());
                return data.getDate();
            }
            return plugin.getConfig().getString("placeholders.default_values.date", "Нет");
        }

        // %refontsearch_jailed%
        if (params.equals("jailed")) {
            return JailManager.isJailed(player.getUniqueId()) ? "true" : "false";
        }

        // %refontsearch_jail_time%
        if (params.equals("jail_time")) {
            if (JailManager.isJailed(player.getUniqueId())) {
                return JailManager.getRemainingTime(player.getUniqueId());
            }
            return plugin.getConfig().getString("placeholders.default_values.jail_time", "0");
        }

        // %refontsearch_jail_time_seconds%
        if (params.equals("jail_time_seconds")) {
            if (JailManager.isJailed(player.getUniqueId())) {
                return String.valueOf(JailManager.getRemainingTimeSeconds(player.getUniqueId()));
            }
            return "0";
        }

        // %refontsearch_status%
        if (params.equals("status")) {
            if (JailManager.isJailed(player.getUniqueId())) {
                return plugin.getConfig().getString("placeholders.status.jailed", "В заключении");
            } else if (WantedManager.isWanted(player.getUniqueId())) {
                return plugin.getConfig().getString("placeholders.status.wanted", "В розыске");
            } else {
                return plugin.getConfig().getString("placeholders.status.clean", "Чистый");
            }
        }

        return null;
    }

    private String getStarsDisplay(int stars) {
        int maxStars = plugin.getConfig().getInt("wanted.maxStars", 5);
        String filledStar = plugin.getConfig().getString("placeholders.stars.filled", "§6★");
        String emptyStar = plugin.getConfig().getString("placeholders.stars.empty", "§7★");

        StringBuilder starsDisplay = new StringBuilder();

        for (int i = 1; i <= maxStars; i++) {
            if (i <= stars) {
                starsDisplay.append(filledStar);
            } else {
                starsDisplay.append(emptyStar);
            }
        }

        return starsDisplay.toString();
    }

    private String getFilledStars(int stars) {
        String filledStar = plugin.getConfig().getString("placeholders.stars.filled", "§6★");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < stars; i++) {
            result.append(filledStar);
        }

        return result.toString();
    }

    private String getEmptyStars(int stars) {
        int maxStars = plugin.getConfig().getInt("wanted.maxStars", 5);
        String emptyStar = plugin.getConfig().getString("placeholders.stars.empty", "§7★");
        StringBuilder result = new StringBuilder();

        int emptyCount = maxStars - stars;
        for (int i = 0; i < emptyCount; i++) {
            result.append(emptyStar);
        }

        return result.toString();
    }
}
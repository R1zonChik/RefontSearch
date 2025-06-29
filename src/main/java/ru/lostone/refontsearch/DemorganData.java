package ru.lostone.refontsearch;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DemorganData {
    private String playerName;
    private String displayName;
    private String reason;
    private String administrator;
    private long timestamp;
    private String date;
    private long duration; // в секундах
    private String type; // "DEMORGAN" или "JAIL"

    public DemorganData(String playerName, String displayName, String reason, String administrator, long duration, String type) {
        this.playerName = playerName;
        this.displayName = displayName;
        this.reason = reason;
        this.administrator = administrator;
        this.duration = duration;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }

    // Геттеры и сеттеры
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getAdministrator() { return administrator; }
    public void setAdministrator(String administrator) { this.administrator = administrator; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        this.date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    // Проверка, истекло ли время заключения
    public boolean isExpired() {
        return System.currentTimeMillis() > (timestamp + duration * 1000);
    }

    // Получить оставшееся время в секундах
    public long getRemainingTime() {
        long remaining = (timestamp + duration * 1000 - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    // Форматированное оставшееся время
    public String getFormattedRemainingTime() {
        long remaining = getRemainingTime();
        if (remaining <= 0) return "00:00:00";

        long hours = remaining / 3600;
        long minutes = (remaining % 3600) / 60;
        long seconds = remaining % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
package ru.lostone.refontsearch;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WantedData {
    private String playerName;
    private String displayName;
    private int stars;
    private String reason;
    private String article;
    private long timestamp;
    private String officer;
    private String date;

    // Старый конструктор для обратной совместимости
    public WantedData(int stars, String reason, String issuedBy) {
        this.stars = stars;
        this.reason = reason;
        this.officer = issuedBy;
        this.timestamp = System.currentTimeMillis();
        this.date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
        this.article = "Не указана";
        this.playerName = "";
        this.displayName = "";
    }

    // Новый конструктор
    public WantedData(String playerName, String displayName, int stars, String reason, String article, String officer) {
        this.playerName = playerName;
        this.displayName = displayName;
        this.stars = stars;
        this.reason = reason;
        this.article = article;
        this.officer = officer;
        this.timestamp = System.currentTimeMillis();
        this.date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }

    // Геттеры и сеттеры
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getArticle() { return article; }
    public void setArticle(String article) { this.article = article; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        this.date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(timestamp));
    }

    public String getOfficer() { return officer; }
    public void setOfficer(String officer) { this.officer = officer; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    // Для обратной совместимости
    public String getIssuedBy() { return officer; }
}
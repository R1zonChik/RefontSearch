package ru.lostone.refontsearch;

public class WantedData {
    private int stars;
    private String reason;
    private String issuedBy;
    private String date;

    public WantedData(int stars, String reason, String issuedBy) {
        this.stars = stars;
        this.reason = reason;
        this.issuedBy = issuedBy;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
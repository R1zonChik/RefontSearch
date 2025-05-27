package ru.lostone.refontsearch.model;

import org.bukkit.Location;

public class Jail {
    private String name;
    private Location jailLocation;
    private Location releaseLocation;
    private double radius;

    public Jail(String name, Location jailLocation, Location releaseLocation, double radius) {
        this.name = name;
        this.jailLocation = jailLocation;
        this.releaseLocation = releaseLocation;
        this.radius = radius;
    }

    // Геттеры и сеттеры
    public String getName() {
        return name;
    }

    public Location getJailLocation() {
        return jailLocation;
    }

    public void setJailLocation(Location jailLocation) {
        this.jailLocation = jailLocation;
    }

    public Location getReleaseLocation() {
        return releaseLocation;
    }

    public void setReleaseLocation(Location releaseLocation) {
        this.releaseLocation = releaseLocation;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }
}
package ru.lostone.refontsearch;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.lostone.refontsearch.command.*;
import ru.lostone.refontsearch.listener.*;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.manager.WantedManager;
import ru.lostone.refontsearch.manager.JailsManager;

public final class RefontSearch extends JavaPlugin {

    private static RefontSearch instance;
    private Location jailLocation;
    private Location unjailLocation;
    private JailsManager jailsManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Инициализация менеджеров
        WantedManager.init(this);
        JailManager.init(this);
        jailsManager = new JailsManager(this);

        // Загружаем координаты из config.yml (если заданы) для обратной совместимости
        String startStr = getConfig().getString("jail.location.start", "");
        if (!startStr.isEmpty()) {
            jailLocation = parseLocation(startStr);
        }
        String endStr = getConfig().getString("jail.location.end", "");
        if (!endStr.isEmpty()) {
            unjailLocation = parseLocation(endStr);
        }

        // Регистрация команд
        getCommand("unwanted").setExecutor(new UnwantedCommand());
        getCommand("unwanted").setTabCompleter(new UnwantedCommand());
        getCommand("policefind").setExecutor(new PoliceFindCommand());
        getCommand("policefind").setTabCompleter(new PoliceFindCommand());
        getCommand("policecall").setExecutor(new PoliceCallCommand());
        getCommand("policeaccept").setExecutor(new PoliceAcceptCommand());
        getCommand("wanted").setExecutor(new WantedCommand());
        // Регистрация команды ареста
        getCommand("arrest").setExecutor(new ArrestCommand());
        getCommand("arrest").setTabCompleter(new ArrestCommand());
        // Если батон включен – регистрируем выдачу предмета и событие
        if (getConfig().getBoolean("baton.enabled", true)) {
            getCommand("wanteditems").setExecutor(new WantedItemsCommand());
            Bukkit.getPluginManager().registerEvents(new BatonMechanicListener(), this);
        }
        getCommand("setjail").setExecutor(new SetJailCommand());
        getCommand("unjail").setExecutor(new UnjailCommand());
        getCommand("unjail").setTabCompleter(new UnjailCommand());

        // Регистрация команды управления тюрьмами
        getCommand("jails").setExecutor(new JailsCommand(this));
        getCommand("jails").setTabCompleter(new JailsCommand(this));

        // Регистрация слушателей
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerRespawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new WantedInventoryListener(), this);
        // Новые слушатели для тюремной системы
        Bukkit.getPluginManager().registerEvents(new PlayerMovementListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        getLogger().info("RefontSearch plugin enabled!");
    }

    @Override
    public void onDisable() {
        JailManager.onDisable();
        getLogger().info("RefontSearch plugin disabled!");
    }

    public static RefontSearch getInstance() {
        return instance;
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

    private Location parseLocation(String locStr) {
        // Формат: world;x;y;z
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
}
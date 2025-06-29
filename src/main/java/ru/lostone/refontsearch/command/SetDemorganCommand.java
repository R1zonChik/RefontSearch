package ru.lostone.refontsearch.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;

public class SetDemorganCommand implements CommandExecutor {

    private final RefontSearch plugin;

    public SetDemorganCommand(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда доступна только игрокам!");
            return true;
        }

        if (!sender.hasPermission("refontsearch.setdemorgan")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.noPermission", "§cУ вас нет прав для использования этой команды.")));
            return true;
        }

        Player player = (Player) sender;
        Location loc = player.getLocation();

        String type = args.length > 0 ? args[0].toLowerCase() : "spawn";

        if (type.equals("spawn")) {
            String locStr = loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ();
            plugin.getConfig().set("demorgan.location.spawn", locStr);
            plugin.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Точка спавна демогрант установлена!");
        } else if (type.equals("release")) {
            String locStr = loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ();
            plugin.getConfig().set("demorgan.location.release", locStr);
            plugin.saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Точка освобождения из демогрант установлена!");
        } else {
            sender.sendMessage(ChatColor.RED + "Использование: /setdemorgan [spawn|release]");
        }

        return true;
    }
}
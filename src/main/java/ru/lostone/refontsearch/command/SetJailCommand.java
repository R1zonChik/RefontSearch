package ru.lostone.refontsearch.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;

public class SetJailCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("refontsearch.admin")) {
            player.sendMessage("§c§l⚔ §7У вас нет прав для данной команды.");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage("§c§l⚔ §7Использование: /setjail <start|end> <x> <y> <z>");
            return true;
        }
        String subCommand = args[0].toLowerCase();
        try {
            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);
            Location loc = new Location(player.getWorld(), x, y, z);
            if (subCommand.equals("start")) {
                RefontSearch.getInstance().setJailLocation(loc);
                player.sendMessage("§a§l⚔ §7Точка тюрьмы (start) установлена: "
                        + loc.getWorld().getName() + " x:" + String.format("%.1f", x)
                        + " y:" + String.format("%.1f", y) + " z:" + String.format("%.1f", z));
            } else if (subCommand.equals("end")) {
                RefontSearch.getInstance().setUnjailLocation(loc);
                player.sendMessage("§a§l⚔ §7Точка освобождения (end) установлена: "
                        + loc.getWorld().getName() + " x:" + String.format("%.1f", x)
                        + " y:" + String.format("%.1f", y) + " z:" + String.format("%.1f", z));
            } else {
                player.sendMessage("§c§l⚔ §7Использование: /setjail <start|end> <x> <y> <z>");
            }
            RefontSearch.getInstance().saveJailLocations();
        } catch (NumberFormatException e) {
            player.sendMessage("§c§l⚔ §7Неверные координаты.");
        }
        return true;
    }
}
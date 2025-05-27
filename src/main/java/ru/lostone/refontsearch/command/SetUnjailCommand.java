package ru.lostone.refontsearch.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;

public class SetUnjailCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("refontsearch.admin")) {
            player.sendMessage("§cУ вас нет прав для данной команды.");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("Использование: /setunjail <x> <y> <z>");
            return true;
        }
        try {
            double x = Double.parseDouble(args[0]);
            double y = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);
            Location loc = new Location(player.getWorld(), x, y, z);
            RefontSearch.getInstance().setUnjailLocation(loc);
            player.sendMessage("§7Точка освобождения установлена.");
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверные координаты.");
        }
        return true;
    }
}
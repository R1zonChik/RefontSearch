package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.manager.WantedManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UnwantedCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player admin = (Player) sender;
        if (!admin.hasPermission("refontsearch.police")) {
            admin.sendMessage("§cУ вас нет прав для этой команды.");
            return true;
        }
        if (args.length < 1) {
            admin.sendMessage("Использование: /unwanted <ник>");
            return true;
        }
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            admin.sendMessage("§cИгрок " + targetName + " не найден или не в сети.");
            return true;
        }
        UUID targetId = target.getUniqueId();
        if (!WantedManager.isWanted(targetId)) {
            admin.sendMessage("§cИгрок " + targetName + " не находится в розыске.");
            return true;
        }
        WantedManager.removeWanted(targetId);
        admin.sendMessage("§aРозыск с игрока " + targetName + " снят.");
        target.sendMessage("§aС вашего розыска сняты звёзды.");
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (WantedManager.isWanted(p.getUniqueId()) && p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
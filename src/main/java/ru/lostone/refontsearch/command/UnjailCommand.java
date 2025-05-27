package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.manager.JailManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UnjailCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("refontsearch.police")) {
            player.sendMessage("§cУ вас нет прав для этой команды.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("Использование: /unjail <ник>");
            return true;
        }
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cИгрок " + targetName + " не найден или не в сети.");
            return true;
        }
        UUID targetId = target.getUniqueId();
        if (!JailManager.isJailed(targetId)) {
            player.sendMessage("§cИгрок " + targetName + " не находится в тюрьме.");
            return true;
        }
        // Чтобы гарантировать немедленное освобождение, можно установить срок равный 1 секунде и сразу вызвать releasePlayer
        JailManager.releasePlayer(targetId);
        player.sendMessage("§aИгрок " + targetName + " освобождён из тюрьмы.");
        target.sendMessage("§aВы освобождены из тюрьмы!");
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (JailManager.isJailed(p.getUniqueId()) && p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
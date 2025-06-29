package ru.lostone.refontsearch.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.lostone.refontsearch.DemorganData;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.DemorganManager;

import java.util.Set;

public class DemorganListCommand implements CommandExecutor {

    private final RefontSearch plugin;

    public DemorganListCommand(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("refontsearch.demorgan.view")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.noPermission", "§cУ вас нет прав для использования этой команды.")));
            return true;
        }

        Set<String> demorganPlayers = DemorganManager.getDemorganPlayers();

        if (demorganPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.demorgan.list_empty", "§7Демогрант пуст.")));
            return true;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.demorgan.list_header", "§6§l=== СПИСОК ДЕМОГРАНТ ===")));

        for (String playerName : demorganPlayers) {
            DemorganData data = DemorganManager.getDemorganData(playerName);
            if (data != null) {
                String message = ChatColor.translateAlternateColorCodes('&',
                                plugin.getConfig().getString("messages.demorgan.list_entry",
                                        "§7{player} §8- §e{time} §8- §f{reason} §8(§a{admin}§8)"))
                        .replace("{player}", playerName)
                        .replace("{time}", data.getFormattedRemainingTime())
                        .replace("{reason}", data.getReason())
                        .replace("{admin}", data.getAdministrator());

                sender.sendMessage(message);
            }
        }

        return true;
    }
}
package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.DemorganManager;

import java.util.ArrayList;
import java.util.List;

public class UndemorganCommand implements CommandExecutor, TabCompleter {

    private final RefontSearch plugin;

    public UndemorganCommand(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("refontsearch.undemorgan")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.noPermission", "§cУ вас нет прав для использования этой команды.")));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Использование: /undemorgan <игрок>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            // Попробуем освободить оффлайн игрока
            if (DemorganManager.isInDemorgan(targetName)) {
                DemorganManager.releaseFromDemorgan(targetName);
                sender.sendMessage(ChatColor.GREEN + "Игрок " + targetName + " освобожден из демогрант (оффлайн).");
            } else {
                sender.sendMessage(ChatColor.RED + "Игрок не найден или не находится в демогрант.");
            }
            return true;
        }

        if (!DemorganManager.isInDemorgan(target.getName())) {
            sender.sendMessage(ChatColor.RED + "Игрок не находится в демогрант.");
            return true;
        }

        String adminName = sender instanceof Player ? sender.getName() : "Консоль";

        DemorganManager.releaseFromDemorgan(target.getName());

        String targetMessage = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.demorgan.released",
                                "§a§l⚔ §7Вы были освобождены из демогрант администратором {admin}!"))
                .replace("{admin}", adminName);

        String adminMessage = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.demorgan.admin_released",
                                "§a§l⚔ §7Вы освободили игрока {player} из демогрант."))
                .replace("{player}", target.getName());

        target.sendMessage(targetMessage);
        sender.sendMessage(adminMessage);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            // Добавляем игроков в демогрант
            for (String playerName : DemorganManager.getDemorganPlayers()) {
                if (playerName.toLowerCase().startsWith(partial)) {
                    completions.add(playerName);
                }
            }
        }

        return completions;
    }
}
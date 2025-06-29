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
import java.util.Arrays;
import java.util.List;

public class DemorganCommand implements CommandExecutor, TabCompleter {

    private final RefontSearch plugin;

    public DemorganCommand(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("refontsearch.demorgan")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.noPermission", "§cУ вас нет прав для использования этой команды.")));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /demorgan <игрок> <время_в_минутах> <причина>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.errors.playerNotFound", "§cИгрок не найден или не в сети.")));
            return true;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(args[1]);
            if (minutes <= 0) {
                sender.sendMessage(ChatColor.RED + "Время должно быть положительным числом!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Неверный формат времени! Укажите время в минутах.");
            return true;
        }

        // Собираем причину из оставшихся аргументов
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            reasonBuilder.append(args[i]);
            if (i < args.length - 1) reasonBuilder.append(" ");
        }
        String reason = reasonBuilder.toString();

        if (reason.trim().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Укажите причину для демогрант!");
            return true;
        }

        String adminName = sender instanceof Player ? sender.getName() : "Консоль";
        long durationSeconds = minutes * 60L;

        // Отправляем в деморган
        DemorganManager.sendToDemorgan(target, reason, adminName, durationSeconds);

        // Уведомления
        String targetMessage = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.demorgan.target",
                                "§c§l⚔ §7Вы были отправлены в деморган на {time} по причине: {reason}"))
                .replace("{time}", formatTime(durationSeconds))
                .replace("{reason}", reason)
                .replace("{admin}", adminName);

        String adminMessage = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.demorgan.admin",
                                "§a§l⚔ §7Вы отправили игрока {player} в деморган на {time} по причине: {reason}"))
                .replace("{player}", target.getName())
                .replace("{time}", formatTime(durationSeconds))
                .replace("{reason}", reason);

        target.sendMessage(targetMessage);
        sender.sendMessage(adminMessage);

        // Публичное уведомление (если включено)
        if (plugin.getConfig().getBoolean("demorgan.broadcast", true)) {
            String broadcastMessage = ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.demorgan.broadcast",
                                    "§c§l⚔ §7Игрок {player} был отправлен в деморган администратором {admin}"))
                    .replace("{player}", target.getName())
                    .replace("{admin}", adminName);

            Bukkit.broadcastMessage(broadcastMessage);
        }

        return true;
    }

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d ч %d мин", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d мин", minutes);
        } else {
            return String.format("%d сек", secs);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Автодополнение имен игроков
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // Предустановленное время
            completions.addAll(Arrays.asList("5", "10", "15", "30", "60", "120", "180"));
        } else if (args.length == 3) {
            // Предустановленные причины
            completions.addAll(plugin.getConfig().getStringList("demorgan.predefined_reasons"));
        }

        return completions;
    }
}
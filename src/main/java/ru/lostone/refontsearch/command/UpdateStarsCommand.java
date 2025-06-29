package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;
import ru.lostone.refontsearch.manager.WantedManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpdateStarsCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("refontsearch.updatestars")) {
            player.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.noPermission", "§cУ вас нет прав для данной команды."));
            return true;
        }

        if (args.length != 2) {
            String usage = RefontSearch.getInstance().getConfig().getString("commands.updatestars.usage", "/updatestars <ник> <новые_звезды>");
            player.sendMessage("§c§l⚔ §7Использование: " + usage);
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            player.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.playerNotFound", "§cИгрок не найден."));
            return true;
        }

        UUID targetId = target.getUniqueId();

        // Проверяем, находится ли игрок в розыске
        if (!WantedManager.isWanted(targetId)) {
            player.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.notWanted", "§cИгрок не находится в розыске."));
            return true;
        }

        // Проверяем новое количество звезд
        int newStars;
        try {
            newStars = Integer.parseInt(args[1]);
            int maxStars = RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 5);
            if (newStars < 1 || newStars > maxStars) {
                String errorMsg = RefontSearch.getInstance().getConfig().getString("messages.errors.invalidStars", "§cНеверное количество звезд. Допустимо от 1 до {max}.")
                        .replace("{max}", String.valueOf(maxStars));
                player.sendMessage(errorMsg);
                return true;
            }
        } catch (NumberFormatException e) {
            String errorMsg = RefontSearch.getInstance().getConfig().getString("messages.errors.invalidStars", "§cНеверное количество звезд.")
                    .replace("{max}", String.valueOf(RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 5)));
            player.sendMessage(errorMsg);
            return true;
        }

        // Получаем текущие данные розыска
        WantedData currentData = WantedManager.getWanted(targetId);

        // Создаем новые данные с обновленным количеством звезд
        WantedData updatedData = new WantedData(
                currentData.getPlayerName(),
                currentData.getDisplayName(),
                newStars,
                currentData.getReason(),
                currentData.getArticle(),
                currentData.getOfficer()
        );

        // Сохраняем оригинальную дату
        updatedData.setTimestamp(currentData.getTimestamp());

        // Обновляем данные
        WantedManager.setWanted(targetId, updatedData);

        // Получаем отображаемое имя
        String displayName = getDisplayName(target);

        // Сообщение для офицера
        String message = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.updated", "§7Уровень розыска игрока {player} изменен на {stars}")
                .replace("{player}", displayName)
                .replace("{stars}", String.valueOf(newStars));
        player.sendMessage(message);

        // Публичное сообщение
        String broadcastMsg = "§7Уровень розыска игрока §f" + displayName + " §7изменен на §c" + newStars + " §6★§7 офицером §f" + player.getName();
        Bukkit.broadcastMessage(broadcastMsg);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Предлагаем игроков в розыске
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (WantedManager.isWanted(p.getUniqueId()) && p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            // Предлагаем количество звезд ИЗ КОНФИГА
            String partial = args[1];
            int maxStars = RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 5); // БЕРЕМ ИЗ КОНФИГА
            for (int i = 1; i <= maxStars; i++) {
                if (String.valueOf(i).startsWith(partial)) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }

    private String getDisplayName(Player player) {
        String placeholder = RefontSearch.getInstance().getConfig().getString("display.placeholder", "%player_name%");

        if (placeholder.equals("%player_name%")) {
            return player.getName();
        }

        // Если доступен PlaceholderAPI, используем его
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);
            } catch (Exception e) {
                return player.getName();
            }
        }

        return player.getName();
    }
}
package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;
import ru.lostone.refontsearch.manager.JailManager;
import ru.lostone.refontsearch.manager.WantedManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArrestCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Проверяем, что команда включена в конфиге
        if (!RefontSearch.getInstance().getConfig().getBoolean("arrest.enabled", true)) {
            sender.sendMessage("§cКоманда ареста отключена в настройках.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        Player officer = (Player) sender;
        if (!officer.hasPermission("refontsearch.police")) {
            officer.sendMessage("§c§l⚔ §7У вас нет прав для данной команды.");
            return true;
        }

        if (args.length < 1) {
            officer.sendMessage("§c§l⚔ §7Использование: /arrest <ник> [force|время]");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            officer.sendMessage("§c§l⚔ §7Игрок " + targetName + " не найден или не в сети.");
            return true;
        }

        // Проверяем радиус действия ареста
        double radius = RefontSearch.getInstance().getConfig().getDouble("arrest.radius", 5.0);
        if (officer.getLocation().distance(target.getLocation()) > radius) {
            officer.sendMessage("§c§l⚔ §7Игрок " + targetName + " слишком далеко для ареста.");
            return true;
        }

        // Проверка дополнительных аргументов
        final boolean forceArrest;
        int customTime = 0;

        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("force")) {
                // Только администраторы могут использовать принудительный арест
                if (officer.hasPermission("refontsearch.admin")) {
                    forceArrest = true;
                } else {
                    officer.sendMessage("§c§l⚔ §7У вас нет прав для принудительного ареста.");
                    return true;
                }
            } else {
                forceArrest = false;
                // Проверяем, задано ли время в аргументе
                try {
                    customTime = Integer.parseInt(args[1]);
                    if (customTime <= 0) {
                        officer.sendMessage("§c§l⚔ §7Время ареста должно быть положительным числом.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    officer.sendMessage("§c§l⚔ §7Неверный формат времени или команды. Используйте 'force' или число секунд.");
                    return true;
                }
            }
        } else {
            forceArrest = false;
        }

        // Пропускаем проверку на розыск если админ использует принудительный арест
        if (!forceArrest && !WantedManager.isWanted(target.getUniqueId())) {
            officer.sendMessage("§c§l⚔ §7Игрок " + targetName + " не находится в розыске.");
            return true;
        }

        // Определяем время ареста
        final int jailTimeSeconds;
        if (customTime > 0) {
            // Используем указанное время
            jailTimeSeconds = customTime;
        } else if (forceArrest) {
            // Для принудительного ареста используем время из конфига
            jailTimeSeconds = RefontSearch.getInstance().getConfig().getInt("arrest.forceTime", 1800); // 30 минут по умолчанию
        } else {
            // Определяем время по звездам розыска
            WantedData data = WantedManager.getWanted(target.getUniqueId());
            int stars = data.getStars();
            jailTimeSeconds = RefontSearch.getInstance().getConfig().getInt("jailTimers." + stars, 900);
        }

        // Вычисляем время окончания ареста
        long jailEndTime = System.currentTimeMillis() + (jailTimeSeconds * 1000L);

        // Сажаем в тюрьму
        JailManager.jailPlayer(target.getUniqueId(), jailEndTime);

        // Снимаем розыск (только если игрок был в розыске)
        if (WantedManager.isWanted(target.getUniqueId())) {
            WantedManager.removeWanted(target.getUniqueId());
        }

        // Телепортируем в тюрьму с небольшой задержкой
        new BukkitRunnable() {
            @Override
            public void run() {
                Location jailLoc = RefontSearch.getInstance().getJailLocation();
                if (jailLoc != null) {
                    target.teleport(jailLoc);
                    target.setGameMode(GameMode.ADVENTURE);

                    // Сообщение для арестованного
                    String reasonText = forceArrest ? "административного решения" : "розыска";
                    String arrestedMsg = RefontSearch.getInstance().getConfig()
                            .getString("messages.arrested.target", "§c§l⚔ §7Вы были арестованы на {time} секунд из-за {reason}!")
                            .replace("{time}", String.valueOf(jailTimeSeconds))
                            .replace("{reason}", reasonText);
                    target.sendMessage(arrestedMsg);
                } else {
                    target.sendMessage("§c§l⚔ §7Точка тюрьмы не установлена!");
                }
            }
        }.runTaskLater(RefontSearch.getInstance(), 20L);

        // Сообщение для офицера
        String typeText = forceArrest ? "принудительно " : "";
        String arrestMsg = RefontSearch.getInstance().getConfig()
                .getString("messages.arrested.officer", "§a§l⚔ §7Вы {type}арестовали игрока {player} на {time} секунд.")
                .replace("{player}", target.getName())
                .replace("{time}", String.valueOf(jailTimeSeconds))
                .replace("{type}", typeText);
        officer.sendMessage(arrestMsg);

        // Публичное сообщение (если включено)
        if (RefontSearch.getInstance().getConfig().getBoolean("arrest.broadcast", true)) {
            String broadcastMsg = RefontSearch.getInstance().getConfig()
                    .getString("messages.arrested.broadcast", "§c§l⚔ §7Игрок {player} был {type}арестован офицером {officer}!")
                    .replace("{player}", target.getName())
                    .replace("{officer}", officer.getName())
                    .replace("{type}", typeText);

            Bukkit.broadcastMessage(broadcastMsg);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Показываем всех игроков в розыске + если есть право админа
                boolean hasAdminPerm = sender.hasPermission("refontsearch.admin");
                if ((WantedManager.isWanted(p.getUniqueId()) || hasAdminPerm) &&
                        p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            // Если у отправителя есть права админа, предлагаем force
            if (sender.hasPermission("refontsearch.admin")) {
                if ("force".startsWith(partial)) {
                    completions.add("force");
                }
            }
            // Предлагаем стандартные значения времени
            String[] times = {"300", "600", "900", "1800", "3600", "7200"};
            for (String time : times) {
                if (time.startsWith(partial)) {
                    completions.add(time);
                }
            }
        }

        return completions;
    }
}
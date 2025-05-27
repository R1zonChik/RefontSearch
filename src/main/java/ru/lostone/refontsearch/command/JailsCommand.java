package ru.lostone.refontsearch.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.model.Jail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JailsCommand implements CommandExecutor, TabCompleter {

    private final RefontSearch plugin;

    public JailsCommand(RefontSearch plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("refontsearch.admin")) {
            player.sendMessage("§c§l⚔ §7У вас нет прав для управления тюрьмами.");
            return true;
        }

        if (args.length < 1) {
            sendHelpMessage(player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "create":
                if (args.length < 3) {
                    player.sendMessage("§c§l⚔ §7Использование: /jails create <название> <радиус>");
                    return true;
                }

                String jailName = args[1];
                double radius;

                try {
                    radius = Double.parseDouble(args[2]);
                    if (radius <= 0) {
                        player.sendMessage("§c§l⚔ §7Радиус должен быть положительным числом.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c§l⚔ §7Неверный формат радиуса.");
                    return true;
                }

                boolean created = plugin.getJailsManager().addJail(
                        jailName,
                        player.getLocation(),
                        player.getLocation(),
                        radius
                );

                if (created) {
                    player.sendMessage("§a§l⚔ §7Тюрьма '" + jailName + "' успешно создана. Используйте /jails setpoint для настройки точек.");
                } else {
                    player.sendMessage("§c§l⚔ §7Тюрьма с названием '" + jailName + "' уже существует.");
                }
                break;

            case "remove":
                if (args.length < 2) {
                    player.sendMessage("§c§l⚔ §7Использование: /jails remove <название>");
                    return true;
                }

                jailName = args[1];
                boolean removed = plugin.getJailsManager().removeJail(jailName);

                if (removed) {
                    player.sendMessage("§a§l⚔ §7Тюрьма '" + jailName + "' успешно удалена.");
                } else {
                    player.sendMessage("§c§l⚔ §7Тюрьма с названием '" + jailName + "' не найдена.");
                }
                break;

            case "setpoint":
                if (args.length < 3) {
                    player.sendMessage("§c§l⚔ §7Использование: /jails setpoint <название> <start|end>");
                    return true;
                }

                jailName = args[1];
                String pointType = args[2].toLowerCase();

                Jail jail = plugin.getJailsManager().getJail(jailName);
                if (jail == null) {
                    player.sendMessage("§c§l⚔ §7Тюрьма с названием '" + jailName + "' не найдена.");
                    return true;
                }

                if (pointType.equals("start")) {
                    jail.setJailLocation(player.getLocation());
                    player.sendMessage("§a§l⚔ §7Точка начала тюрьмы '" + jailName + "' установлена.");
                } else if (pointType.equals("end")) {
                    jail.setReleaseLocation(player.getLocation());
                    player.sendMessage("§a§l⚔ §7Точка освобождения для тюрьмы '" + jailName + "' установлена.");
                } else {
                    player.sendMessage("§c§l⚔ §7Неверный тип точки. Используйте 'start' или 'end'.");
                    return true;
                }

                plugin.getJailsManager().saveJails();
                break;

            case "list":
                Map<String, Jail> jails = plugin.getJailsManager().getAllJails();

                if (jails.isEmpty()) {
                    player.sendMessage("§c§l⚔ §7Тюрьмы не настроены.");
                    return true;
                }

                player.sendMessage("§6§l⚔ §7Список тюрем:");
                for (Map.Entry<String, Jail> entry : jails.entrySet()) {
                    Jail j = entry.getValue();
                    String status = (j.getJailLocation() != null && j.getReleaseLocation() != null)
                            ? "§aНастроена"
                            : "§cТребует настройки";
                    player.sendMessage("§7- " + entry.getKey() + " (" + status + "§7, радиус: " + j.getRadius() + ")");
                }
                break;

            case "setradius":
                if (args.length < 3) {
                    player.sendMessage("§c§l⚔ §7Использование: /jails setradius <название> <радиус>");
                    return true;
                }

                jailName = args[1];
                jail = plugin.getJailsManager().getJail(jailName);

                if (jail == null) {
                    player.sendMessage("§c§l⚔ §7Тюрьма с названием '" + jailName + "' не найдена.");
                    return true;
                }

                try {
                    radius = Double.parseDouble(args[2]);
                    if (radius <= 0) {
                        player.sendMessage("§c§l⚔ §7Радиус должен быть положительным числом.");
                        return true;
                    }

                    jail.setRadius(radius);
                    plugin.getJailsManager().saveJails();
                    player.sendMessage("§a§l⚔ §7Радиус тюрьмы '" + jailName + "' успешно изменен на " + radius);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c§l⚔ §7Неверный формат радиуса.");
                }
                break;

            default:
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6§l⚔ §7Команды управления тюрьмами:");
        player.sendMessage("§7- /jails create <название> <радиус> - Создать новую тюрьму");
        player.sendMessage("§7- /jails remove <название> - Удалить тюрьму");
        player.sendMessage("§7- /jails setpoint <название> <start|end> - Установить точку тюрьмы");
        player.sendMessage("§7- /jails list - Показать список тюрем");
        player.sendMessage("§7- /jails setradius <название> <радиус> - Изменить радиус тюрьмы");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("create");
            completions.add("remove");
            completions.add("setpoint");
            completions.add("list");
            completions.add("setradius");
            return filterCompletions(completions, args[0]);
        } else if (args.length == 2) {
            // Подсказки для команд, требующих названия тюрьмы
            if (args[0].equalsIgnoreCase("remove") ||
                    args[0].equalsIgnoreCase("setpoint") ||
                    args[0].equalsIgnoreCase("setradius")) {

                Map<String, Jail> jails = plugin.getJailsManager().getAllJails();
                completions.addAll(jails.keySet());
                return filterCompletions(completions, args[1]);
            }
        } else if (args.length == 3) {
            // Подсказки для команды setpoint
            if (args[0].equalsIgnoreCase("setpoint")) {
                completions.add("start");
                completions.add("end");
                return filterCompletions(completions, args[2]);
            }
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> completions, String arg) {
        if (arg.isEmpty()) {
            return completions;
        }

        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(arg.toLowerCase())) {
                filtered.add(completion);
            }
        }

        return filtered;
    }
}
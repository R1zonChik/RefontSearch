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
import ru.lostone.refontsearch.model.Jail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            officer.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.noPermission", "§cУ вас нет прав для данной команды."));
            return true;
        }

        if (args.length < 1) {
            String usage = RefontSearch.getInstance().getConfig().getString("commands.arrest.usage", "/arrest <ник> [время] [статья] [тюрьма]");
            officer.sendMessage("§c§l⚔ §7Использование: " + usage);
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            officer.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.playerNotFound", "§cИгрок не найден."));
            return true;
        }

        // Проверяем радиус действия ареста
        double radius = RefontSearch.getInstance().getConfig().getDouble("arrest.radius", 5.0);
        if (officer.getLocation().distance(target.getLocation()) > radius) {
            officer.sendMessage("§c§l⚔ §7Игрок " + targetName + " слишком далеко для ареста.");
            return true;
        }

        // Парсим аргументы
        final boolean forceArrest;
        int customTime = 0;
        String article = null;
        final String jailName;

        // Парсинг аргументов: /arrest <ник> [время|force] [статья] [тюрьма]
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("force")) {
                if (officer.hasPermission("refontsearch.admin")) {
                    forceArrest = true;
                } else {
                    officer.sendMessage("§c§l⚔ §7У вас нет прав для принудительного ареста.");
                    return true;
                }
            } else {
                forceArrest = false;
                try {
                    customTime = Integer.parseInt(args[1]);
                    if (customTime <= 0) {
                        officer.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.invalidTime", "§cНеверное время."));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    officer.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.invalidTime", "§cНеверное время."));
                    return true;
                }
            }
        } else {
            forceArrest = false;
        }

        // Получаем статью (3-й аргумент)
        if (args.length >= 3) {
            article = args[2];
        }

        // Получаем тюрьму (4-й аргумент)
        if (args.length >= 4) {
            jailName = args[3];
            Jail jail = RefontSearch.getInstance().getJailsManager().getJail(jailName);
            if (jail == null) {
                officer.sendMessage("§c§l⚔ §7Тюрьма с названием '" + jailName + "' не найдена.");
                return true;
            }
            if (jail.getJailLocation() == null) {
                officer.sendMessage("§c§l⚔ §7У тюрьмы '" + jailName + "' не настроена точка начала.");
                return true;
            }
        } else {
            jailName = null;
        }

        // Получаем статью из розыска если не указана
        if (!forceArrest) {
            if (!WantedManager.isWanted(target.getUniqueId())) {
                officer.sendMessage("§c§l⚔ §7Игрок " + targetName + " не находится в розыске.");
                return true;
            }

            // Если статья не указана, берем из розыска
            if (article == null) {
                WantedData wantedData = WantedManager.getWanted(target.getUniqueId());
                article = wantedData.getArticle();
            }
        }

        // Проверяем требование статьи
        if (article == null || article.trim().isEmpty()) {
            if (RefontSearch.getInstance().getConfig().getBoolean("articles.enabled", true)) {
                officer.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.errors.articleRequired", "§cУкажите статью УК."));
                return true;
            }
            article = "Не указана";
        }

        // Проверяем наличие тюрьмы
        if (RefontSearch.getInstance().getJailLocation() == null && jailName == null) {
            boolean hasAnyJail = false;
            Map<String, Jail> jails = RefontSearch.getInstance().getJailsManager().getAllJails();
            for (Jail jail : jails.values()) {
                if (jail.getJailLocation() != null) {
                    hasAnyJail = true;
                    break;
                }
            }

            if (!hasAnyJail) {
                officer.sendMessage("§c§l⚔ §7Не удалось арестовать игрока: тюрьма не настроена.");
                return true;
            }
        }

        // Определяем время ареста
        final int jailTimeSeconds;
        if (customTime > 0) {
            jailTimeSeconds = customTime;
        } else if (forceArrest) {
            jailTimeSeconds = RefontSearch.getInstance().getConfig().getInt("arrest.forceTime", 1800);
        } else {
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

        final String finalArticle = article;

        // Телепортируем в тюрьму
        new BukkitRunnable() {
            @Override
            public void run() {
                Location jailLoc;
                if (jailName != null) {
                    Jail jail = RefontSearch.getInstance().getJailsManager().getJail(jailName);
                    jailLoc = jail.getJailLocation();
                    RefontSearch.getInstance().getJailsManager().setPlayerJail(target.getUniqueId(), jailName);
                } else {
                    jailLoc = RefontSearch.getInstance().getJailLocation();
                    if (jailLoc == null) {
                        Jail randomJail = RefontSearch.getInstance().getJailsManager().getRandomJail();
                        if (randomJail != null && randomJail.getJailLocation() != null) {
                            jailLoc = randomJail.getJailLocation();
                            RefontSearch.getInstance().getJailsManager().setPlayerJail(target.getUniqueId(), randomJail.getName());
                        }
                    }
                }

                if (jailLoc != null) {
                    target.teleport(jailLoc);
                    target.setGameMode(GameMode.ADVENTURE);

                    String arrestedMsg = RefontSearch.getInstance().getConfig()
                            .getString("messages.arrested.target", "§c§l⚔ §7Вы были арестованы на {time} секунд по статье {article}!")
                            .replace("{time}", String.valueOf(jailTimeSeconds))
                            .replace("{article}", finalArticle)
                            .replace("{reason}", "розыска");  // ДОБАВЬТЕ ЭТУ СТРОКУ ЕСЛИ НУЖНО
                    target.sendMessage(arrestedMsg);

                } else {
                    Jail randomJail = RefontSearch.getInstance().getJailsManager().getRandomJail();
                    if (randomJail != null && randomJail.getJailLocation() != null) {
                        target.teleport(randomJail.getJailLocation());
                        target.setGameMode(GameMode.ADVENTURE);
                        RefontSearch.getInstance().getJailsManager().setPlayerJail(target.getUniqueId(), randomJail.getName());

                        String arrestedMsg = RefontSearch.getInstance().getConfig()
                                .getString("messages.arrested.target", "§c§l⚔ §7Вы были арестованы на {time} секунд по статье {article}!")
                                .replace("{time}", String.valueOf(jailTimeSeconds))
                                .replace("{article}", finalArticle)
                                .replace("{reason}", "розыска");  // ДОБАВЬТЕ ЭТУ СТРОКУ ЕСЛИ НУЖНО
                        target.sendMessage(arrestedMsg);
                    } else {
                        officer.sendMessage("§c§l⚔ §7Не удалось арестовать игрока: тюрьма не настроена.");
                        target.sendMessage("§c§l⚔ §7Точка тюрьмы не установлена!");
                        JailManager.releasePlayer(target.getUniqueId());
                    }
                }
            }
        }.runTaskLater(RefontSearch.getInstance(), 20L);

        // Сообщения
        String typeText = forceArrest ? "принудительно " : "";
        String arrestMsg = RefontSearch.getInstance().getConfig()
                .getString("messages.arrested.officer", "§a§l⚔ §7Вы {type}арестовали игрока {player} на {time} секунд по статье {article}.")
                .replace("{player}", target.getName())
                .replace("{time}", String.valueOf(jailTimeSeconds))
                .replace("{type}", typeText)
                .replace("{article}", finalArticle);
        officer.sendMessage(arrestMsg);

        // Публичное сообщение
        if (RefontSearch.getInstance().getConfig().getBoolean("arrest.broadcast", true)) {
            String broadcastMsg = RefontSearch.getInstance().getConfig()
                    .getString("messages.arrested.broadcast", "§c§l⚔ §7Игрок {player} был {type}арестован офицером {officer} по статье {article}!")
                    .replace("{player}", target.getName())
                    .replace("{officer}", officer.getName())
                    .replace("{type}", typeText)
                    .replace("{article}", finalArticle);

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
                boolean hasAdminPerm = sender.hasPermission("refontsearch.admin");
                if ((WantedManager.isWanted(p.getUniqueId()) || hasAdminPerm) &&
                        p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            if (sender.hasPermission("refontsearch.admin")) {
                if ("force".startsWith(partial)) {
                    completions.add("force");
                }
            }
            String[] times = {"300", "600", "900", "1800", "3600", "7200"};
            for (String time : times) {
                if (time.startsWith(partial)) {
                    completions.add(time);
                }
            }
        } else if (args.length == 3) {
            // Предлагаем статьи УК
            String partial = args[2].toLowerCase();
            List<String> articles = RefontSearch.getInstance().getConfig().getStringList("articles.predefined");
            for (String article : articles) {
                if (article.toLowerCase().startsWith(partial)) {
                    completions.add(article);
                }
            }
        } else if (args.length == 4) {
            // Предлагаем названия тюрем
            String partial = args[3].toLowerCase();
            Map<String, Jail> jails = RefontSearch.getInstance().getJailsManager().getAllJails();
            for (String jailName : jails.keySet()) {
                if (jailName.toLowerCase().startsWith(partial)) {
                    completions.add(jailName);
                }
            }
        }

        return completions;
    }
}
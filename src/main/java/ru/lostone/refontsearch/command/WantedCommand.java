package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.WantedData;
import ru.lostone.refontsearch.manager.WantedManager;
import java.util.*;

public class WantedCommand implements CommandExecutor, TabCompleter {

    public static final int ITEMS_PER_PAGE = 45;
    private static final int MAX_REASON_LINE_LENGTH = 30;
    // Кулдаун на выдачу розыска (в миллисекундах)
    private static Map<UUID, Long> wantedCooldowns = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cКоманда доступна только игрокам.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("refontsearch.wanted")) {
            player.sendMessage("§cУ вас нет прав для использования этой команды.");
            return true;
        }

        // ========== ФИКС: ОТКРЫТИЕ МЕНЮ РОЗЫСКА ==========
        // Если команда без аргументов - открыть список розыска
        if (args.length == 0) {
            openWantedList(player, 1);
            return true;
        }
        // ================================================

        if (args.length < 4) {
            player.sendMessage("§cИспользование: /wanted <ник> <звезды> <статья> <причина>");
            player.sendMessage("§7Для просмотра списка розыска: /wanted");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            player.sendMessage("§cИгрок не найден.");
            return true;
        }

        // Проверяем кулдаун
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        int cooldownSeconds = RefontSearch.getInstance().getConfig().getInt("wantedCooldown", 30);
        long cooldownTime = cooldownSeconds * 1000L;

        if (wantedCooldowns.containsKey(playerUUID)) {
            long lastUsed = wantedCooldowns.get(playerUUID);
            long timeDiff = currentTime - lastUsed;
            if (timeDiff < cooldownTime) {
                long remainingTime = (cooldownTime - timeDiff) / 1000;
                player.sendMessage("§cПодождите " + remainingTime + " секунд перед следующей выдачей розыска.");
                return true;
            }
        }

        // Проверяем звезды
        int stars;
        try {
            stars = Integer.parseInt(args[1]);
            int maxStars = RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 7);
            if (stars < 1 || stars > maxStars) {
                player.sendMessage("§cЗвезды должны быть от 1 до " + maxStars);
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверное количество звезд.");
            return true;
        }

        String article = args[2];

        // Собираем причину из оставшихся аргументов
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            reasonBuilder.append(args[i]);
            if (i < args.length - 1) {
                reasonBuilder.append(" ");
            }
        }
        String reason = reasonBuilder.toString();

        // Проверяем, не находится ли уже в розыске
        if (WantedManager.isWanted(target.getUniqueId())) {
            player.sendMessage("§cИгрок уже находится в розыске. Используйте /updatestars для изменения уровня.");
            return true;
        }

        // Получаем отображаемое имя
        String displayName = getDisplayName(target);

        // Создаем данные розыска
        WantedData wantedData = new WantedData(target.getName(), displayName, stars, reason, article, player.getName());
        WantedManager.setWanted(target.getUniqueId(), wantedData);

        // Устанавливаем кулдаун
        wantedCooldowns.put(playerUUID, currentTime);

        // Уведомления
        String message = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.set", "§7Розыск установлен для игрока {player} с уровнем {stars} по статье {article}")
                .replace("{player}", displayName)
                .replace("{stars}", String.valueOf(stars))
                .replace("{article}", article);
        player.sendMessage(message);

        String reasonMsg = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.reason", "§7Причина: {reason}")
                .replace("{reason}", reason);
        player.sendMessage(reasonMsg);

        String broadcastMsg = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.notify", "§7Игрок {player} объявлен в розыск по статье {article}: §a{reason}")
                .replace("{player}", displayName)
                .replace("{article}", article)
                .replace("{reason}", reason)
                .replace("{stars}", String.valueOf(stars));

        // Уведомляем игрокам с правами полиции
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("refontsearch.police") || onlinePlayer.hasPermission("refontsearch.wanted")) {
                onlinePlayer.sendMessage(broadcastMsg);
            }
        }

        return true;
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
                // Если PlaceholderAPI недоступен, возвращаем ник
                return player.getName();
            }
        }

        return player.getName();
    }

    public void openWantedList(Player player, int page) {
        Map<UUID, WantedData> wantedList = WantedManager.getAllWanted();

        if (wantedList.isEmpty()) {
            player.sendMessage("§7Список розыска пуст.");
            return;
        }

        List<Map.Entry<UUID, WantedData>> entries = new ArrayList<>(wantedList.entrySet());
        int totalPages = Math.max(1, (entries.size() - 1) / ITEMS_PER_PAGE + 1);
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        String title = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.interface.title", "§0Розыск Страница {page}")
                .replace("{page}", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, entries.size());

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<UUID, WantedData> entry = entries.get(i);
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null) playerName = entry.getKey().toString();
            WantedData data = entry.getValue();

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();

            // Собираем описание в требуемом виде:
            // Верхняя линия, строка уровня, статья, дата, причина, выдавший, нижняя линия.
            // Заполненные звёзды: §f★, незаполненные: §8★.
            java.util.List<String> lore = new ArrayList<>();
            lore.add("§f------------------------");

            int filled = data.getStars();
            int maxStars = RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 7);
            int unfilled = maxStars - filled;
            StringBuilder starsLine = new StringBuilder("§9Уровень: ");
            for (int j = 0; j < filled; j++) {
                starsLine.append("§f★");
            }
            for (int j = 0; j < unfilled; j++) {
                starsLine.append("§8★");
            }
            lore.add(starsLine.toString());

            // Добавляем статью
            lore.add("§9Статья: §f" + data.getArticle());

            lore.add("§9В розыске с:");
            lore.add("§f" + data.getDate());

            lore.add("§9Причина:");
            List<String> wrapped = wrapText(data.getReason(), MAX_REASON_LINE_LENGTH);
            for (String line : wrapped) {
                lore.add("§f" + line);
            }

            lore.add("§9Выдал: §a" + data.getOfficer());
            lore.add("§f------------------------");

            // Добавляем информацию о статусе игрока
            Player targetPlayer = Bukkit.getPlayer(entry.getKey());
            if (targetPlayer != null && targetPlayer.isOnline()) {
                lore.add("§aСтатус: В сети");
            } else {
                lore.add("§cСтатус: Не в сети");
            }

            meta.setDisplayName("§9" + playerName);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.addItem(item);
        }

        // Навигация по страницам
        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            String prevDisplay = RefontSearch.getInstance().getConfig()
                    .getString("messages.wanted.interface.prev_page", "§7← Предыдущая страница");
            prevMeta.setDisplayName(prevDisplay);
            List<String> prevLore = new ArrayList<>();
            prevLore.add("§7Нажмите для перехода");
            prevLore.add("§7на страницу " + (page - 1));
            prevMeta.setLore(prevLore);
            prevPage.setItemMeta(prevMeta);
            inv.setItem(45, prevPage);
        }

        if (page < totalPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            String nextDisplay = RefontSearch.getInstance().getConfig()
                    .getString("messages.wanted.interface.next_page", "§7Следующая страница →");
            nextMeta.setDisplayName(nextDisplay);
            List<String> nextLore = new ArrayList<>();
            nextLore.add("§7Нажмите для перехода");
            nextLore.add("§7на страницу " + (page + 1));
            nextMeta.setLore(nextLore);
            nextPage.setItemMeta(nextMeta);
            inv.setItem(53, nextPage);
        }

        // Информационный предмет
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lИнформация");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Всего в розыске: §e" + entries.size());
        infoLore.add("§7Страница: §e" + page + "§7/§e" + totalPages);
        infoLore.add("§7");
        infoLore.add("§7Команды:");
        infoLore.add("§7• /unwanted <ник> - снять розыск");
        infoLore.add("§7• /updatestars <ник> <звезды> - изменить уровень");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        player.openInventory(inv);
    }

    private List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxLength) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        } else if (args.length == 2) {
            // Предлагаем количество звезд
            String partial = args[1];
            int maxStars = RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 7);
            for (int i = 1; i <= maxStars; i++) {
                if (String.valueOf(i).startsWith(partial)) {
                    completions.add(String.valueOf(i));
                }
            }
        } else if (args.length == 3) {
            // Предлагаем статьи УК
            List<String> articles = RefontSearch.getInstance().getConfig().getStringList("articles.predefined");
            String partial = args[2].toLowerCase();
            for (String article : articles) {
                if (article.toLowerCase().startsWith(partial)) {
                    completions.add(article);
                }
            }
        }
        return completions;
    }
}
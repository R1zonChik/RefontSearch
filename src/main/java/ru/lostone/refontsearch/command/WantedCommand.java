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

    // WantedCommand.java - обновленная версия
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

        if (args.length < 4) {
            player.sendMessage("§cИспользование: /wanted <ник> <звезды> <статья> <причина>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            player.sendMessage("§cИгрок не найден.");
            return true;
        }

        // Проверяем звезды
        int stars;
        try {
            stars = Integer.parseInt(args[1]);
            int maxStars = RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 5);
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

        // Получаем отображаемое имя
        String displayName = getDisplayName(target);

        // Создаем данные розыска
        WantedData wantedData = new WantedData(target.getName(), displayName, stars, reason, article, player.getName());
        WantedManager.setWanted(target.getUniqueId(), wantedData);

        // Уведомления
        String message = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.set", "§7Розыск установлен для игрока {player} с уровнем {stars}")
                .replace("{player}", displayName)
                .replace("{stars}", String.valueOf(stars))
                .replace("{article}", article);  // ДОБАВЬТЕ ЭТУ СТРОКУ
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

        Bukkit.broadcastMessage(broadcastMsg);

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
        List<Map.Entry<UUID, WantedData>> entries = new ArrayList<>(wantedList.entrySet());
        int totalPages = (entries.size() - 1) / ITEMS_PER_PAGE + 1;
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        String title = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.interface.title", "Розыск Страница {page}")
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
            // Верхняя линия, строка уровня, дата, причина, нижняя линия.
            // Заполненные звёзды: §f★, незаполненные: §8★.
            java.util.List<String> lore = new ArrayList<>();
            lore.add("§f------------------------");
            int filled = data.getStars();
            int unfilled = 7 - filled;
            StringBuilder starsLine = new StringBuilder("§9Уровень: ");
            for (int j = 0; j < filled; j++) {
                starsLine.append("§f★");
            }
            for (int j = 0; j < unfilled; j++) {
                starsLine.append("§8★");
            }
            lore.add(starsLine.toString());
            lore.add("§9В розыске с:");
            lore.add("§f" + data.getDate());
            lore.add("§9Причина:");
            List<String> wrapped = wrapText(data.getReason(), MAX_REASON_LINE_LENGTH);
            for (String line : wrapped) {
                lore.add("§f" + line);
            }
            lore.add("§f------------------------");

            meta.setDisplayName("§9" + playerName);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.addItem(item);
        }

        if (page > 1) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            String prevDisplay = RefontSearch.getInstance().getConfig()
                    .getString("messages.wanted.interface.prev_page", "← Предыдущая страница");
            prevMeta.setDisplayName(prevDisplay);
            prevPage.setItemMeta(prevMeta);
            inv.setItem(45, prevPage);
        }
        if (page < totalPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            String nextDisplay = RefontSearch.getInstance().getConfig()
                    .getString("messages.wanted.interface.next_page", "Следующая страница →");
            nextMeta.setDisplayName(nextDisplay);
            nextPage.setItemMeta(nextMeta);
            inv.setItem(53, nextPage);
        }
        player.openInventory(inv);
    }

    private List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxLength) {
                lines.add(current.toString());
                current = new StringBuilder(word);
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
            int maxStars = RefontSearch.getInstance().getConfig().getInt("wanted.maxStars", 5); // БЕРЕМ ИЗ КОНФИГА
            for (int i = 1; i <= maxStars; i++) {
                if (String.valueOf(i).startsWith(partial)) {
                    completions.add(String.valueOf(i));
                }
            }
        }
        return completions;
    }
}
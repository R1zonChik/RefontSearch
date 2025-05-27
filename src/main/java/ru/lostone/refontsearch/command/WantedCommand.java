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
        // Если без аргументов, открыть GUI
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            openWantedList(player, 1);
            return true;
        }

        // Если выдаем розыск, проверяем кулдаун
        long current = System.currentTimeMillis();
        UUID senderId = player.getUniqueId();
        int cooldownSeconds = RefontSearch.getInstance().getConfig().getInt("wantedCooldown", 30);
        long cooldownMillis = cooldownSeconds * 1000L;
        if (wantedCooldowns.containsKey(senderId)) {
            long last = wantedCooldowns.get(senderId);
            if (current - last < cooldownMillis) {
                long wait = (cooldownMillis - (current - last)) / 1000;
                player.sendMessage("§cПодождите ещё " + wait + " секунд перед выдачей нового розыска.");
                return true;
            }
        }
        wantedCooldowns.put(senderId, current);

        // Если выдаются аргументы, то выдаем розыск
        if (args.length < 3) {
            player.sendMessage("Использование: /wanted <ник> <звезды> [причина]");
            return true;
        }
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cИгрок " + targetName + " не найден или не в сети.");
            return true;
        }
        int stars;
        try {
            stars = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверное количество звёзд.");
            return true;
        }
        if (stars < 1 || stars > 7) {
            player.sendMessage("§cКоличество звёзд должно быть от 1 до 7.");
            return true;
        }
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            reasonBuilder.append(args[i]).append(" ");
        }
        String reason = reasonBuilder.toString().trim();
        WantedData data = new WantedData(stars, reason, player.getName());
        WantedManager.addWanted(target.getUniqueId(), data);

        String setTemplate = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.set", "Розыск установлен для {player} с уровнем {stars}");
        String setMessage = setTemplate.replace("{player}", target.getName())
                .replace("{stars}", String.valueOf(stars));
        player.sendMessage(setMessage);

        String reasonTemplate = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.reason", "Причина: {reason}");
        List<String> wrappedReason = wrapText(reason, MAX_REASON_LINE_LENGTH);
        StringBuilder finalReason = new StringBuilder();
        for (String line : wrappedReason) {
            finalReason.append("§f").append(line).append("\n");
        }
        String reasonMessage = reasonTemplate.replace("{reason}", finalReason.toString().trim());
        player.sendMessage(reasonMessage);

        // Оповещаем полицейских об объявлении розыска
        String notifyTemplate = RefontSearch.getInstance().getConfig()
                .getString("messages.wanted.notify", "§3Игрок {player} объявлен в розыск по причине: {reason}");
        String notifyMsg = notifyTemplate.replace("{player}", target.getName())
                .replace("{reason}", reason);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("refontsearch.police")) {
                p.sendMessage(notifyMsg);
            }
        }

        return true;
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
            String partial = args[1];
            for (int i = 1; i <= 7; i++) {
                String num = String.valueOf(i);
                if (num.startsWith(partial)) {
                    completions.add(num);
                }
            }
        }
        return completions;
    }
}
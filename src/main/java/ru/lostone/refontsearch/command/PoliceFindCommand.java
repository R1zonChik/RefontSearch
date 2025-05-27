package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.WantedManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PoliceFindCommand implements CommandExecutor, TabCompleter {

    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!RefontSearch.getInstance().getConfig().getBoolean("policefind.enabled", true)) {
            sender.sendMessage("§cЭта команда отключена.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            player.sendMessage("Использование: /policefind <ник>");
            return true;
        }
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cИгрок " + targetName + " не найден или не в сети.");
            return true;
        }
        if (!WantedManager.isWanted(target.getUniqueId())) {
            player.sendMessage("§cИгрок " + targetName + " не находится в розыске.");
            return true;
        }
        int cooldownSeconds = RefontSearch.getInstance().getConfig().getInt("policefind.cooldown", 1800);
        long now = System.currentTimeMillis();
        UUID senderId = player.getUniqueId();
        if (cooldowns.containsKey(senderId)) {
            long last = cooldowns.get(senderId);
            if (now - last < cooldownSeconds * 1000L) {
                long remaining = (cooldownSeconds * 1000L - (now - last)) / 1000;
                player.sendMessage("§cПодождите ещё " + remaining + " секунд перед следующим поиском.");
                return true;
            }
        }
        cooldowns.put(senderId, now);

        Location loc = target.getLocation();
        String x = String.format("%.1f", loc.getX());
        String y = String.format("%.1f", loc.getY());
        String z = String.format("%.1f", loc.getZ());

        String messageTemplate = RefontSearch.getInstance().getConfig().getString("messages.policefind",
                "§3По спутникам мы видели игрока {player} на координатах §a[{x}, {y}, {z}]§3. Следующий снимок через {time} секунд. Попробуйте позже.");
        String msg = messageTemplate.replace("{player}", target.getName())
                .replace("{x}", x)
                .replace("{y}", y)
                .replace("{z}", z)
                .replace("{time}", String.valueOf(cooldownSeconds));
        player.sendMessage(msg);
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
                if (WantedManager.isWanted(p.getUniqueId()) && p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
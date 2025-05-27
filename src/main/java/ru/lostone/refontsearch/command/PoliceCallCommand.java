package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.PoliceCallManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PoliceCallCommand implements CommandExecutor {

    // Проверка спама: время между вызовами (60 секунд)
    private static Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MILLIS = 60000;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(uuid)) {
            long lastCall = cooldowns.get(uuid);
            if (now - lastCall < COOLDOWN_MILLIS) {
                long wait = (COOLDOWN_MILLIS - (now - lastCall)) / 1000;
                player.sendMessage("§cПодождите " + wait + " секунд перед повторным вызовом полиции.");
                return true;
            }
        }
        cooldowns.put(uuid, now);

        // Если сообщение не указано – сообщаем об ошибке
        if (args.length == 0) {
            player.sendMessage("§cПожалуйста, укажите сообщение при вызове полиции!");
            return true;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : args) {
            sb.append(s).append(" ");
        }
        String message = sb.toString().trim();

        Location loc = player.getLocation();
        String x = String.format("%.1f", loc.getX());
        String y = String.format("%.1f", loc.getY());
        String z = String.format("%.1f", loc.getZ());

        String notifyTemplate = RefontSearch.getInstance().getConfig().getString("messages.policecall.notify", "§7Вызов полиции от {player} на координатах §a[{x}, {y}, {z}] Сообщение: {message}");
        String notifyMessage = notifyTemplate
                .replace("{player}", player.getName())
                .replace("{x}", x)
                .replace("{y}", y)
                .replace("{z}", z)
                .replace("{message}", message);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("refontsearch.police")) {
                p.sendMessage(notifyMessage);
            }
        }

        String sentTemplate = RefontSearch.getInstance().getConfig().getString("messages.policecall.sent", "§7Вы позвонили в полицию. Ваше сообщение: {message}");
        String sentMessage = sentTemplate.replace("{message}", message);
        player.sendMessage(sentMessage);

        // Сохраняем заявку для последующего принятия
        PoliceCallManager.addCall(player, message);

        return true;
    }
}
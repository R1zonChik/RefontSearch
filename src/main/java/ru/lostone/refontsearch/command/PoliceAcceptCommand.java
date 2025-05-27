package ru.lostone.refontsearch.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.manager.PoliceCallManager;
import ru.lostone.refontsearch.manager.PoliceCallManager.CallInfo;

import java.util.ArrayList;
import java.util.List;

public class PoliceAcceptCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player police = (Player) sender;
        if (!police.hasPermission("refontsearch.police")) {
            police.sendMessage("§cУ вас нет прав для данной команды.");
            return true;
        }
        if (args.length < 1) {
            police.sendMessage(RefontSearch.getInstance().getConfig().getString("messages.policeaccept.error", "§cВыберите игрока из списка предупреждений!"));
            return true;
        }
        String targetName = args[0];
        CallInfo callInfo = PoliceCallManager.getCallByName(targetName);
        if (callInfo == null) {
            police.sendMessage("§cИгрок " + targetName + " не подавал заявку.");
            return true;
        }
        Player caller = callInfo.getCaller();
        if (caller == null || !caller.isOnline()) {
            police.sendMessage("§cИгрок " + targetName + " не найден или не в сети.");
            PoliceCallManager.removeCall(caller);
            return true;
        }
        // Убираем заявку, так как она принята
        PoliceCallManager.removeCall(caller);

        // Отправляем сообщение принимающему (полицейскому)
        String acceptedTemplate = RefontSearch.getInstance().getConfig().getString("messages.policeaccept.accepted", "§7Вы приняли вызов от игрока {caller}");
        String acceptedMessage = acceptedTemplate.replace("{caller}", caller.getName());
        police.sendMessage(acceptedMessage);

        // Уведомляем вызывающего
        String notifyTemplate = RefontSearch.getInstance().getConfig().getString("messages.policeaccept.notify", "§7Сотрудник полиции {police} принял ваш вызов.");
        String notifyMessage = notifyTemplate.replace("{police}", police.getName());
        caller.sendMessage(notifyMessage);

        return true;
    }

    // Подсказка TAB – возвращаем только имена игроков, подавших заявку
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) {
            return completions;
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String name : PoliceCallManager.getCallersNames()) {
                if (name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}
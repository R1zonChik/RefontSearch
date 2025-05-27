package ru.lostone.refontsearch.command;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.lostone.refontsearch.RefontSearch;

public class WantedItemsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Если команда вызывается не игроком, прекращаем выполнение.
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }
        Player player = (Player) sender;

        // Перезагружаем конфиг, чтобы изменения из файла config.yml были применены
        RefontSearch.getInstance().reloadConfig();
        // Читаем материал для выдачи предмета из конфига (кейс не важен), по умолчанию STICK
        String materialName = RefontSearch.getInstance().getConfig().getString("wanteditems.item", "STICK");
        Material mat;
        try {
            // Приводим строку к верхнему регистру и пытаемся получить значение из Material
            mat = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cНеверный материал для выдачи предмета в конфиге: " + materialName);
            return true;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Полицейская Дубинка");
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        player.sendMessage("§7Вам выдан полицейский предмет " + mat.name() + ".");
        return true;
    }
}
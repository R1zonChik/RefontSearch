package ru.lostone.refontsearch.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.lostone.refontsearch.RefontSearch;
import ru.lostone.refontsearch.command.WantedCommand;

public class WantedInventoryListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith("§0Розыск Страница")) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null)
            return;
        if (clicked.getType() == Material.ARROW) {
            String displayName = clicked.getItemMeta().getDisplayName();
            String[] parts = title.split(" ");
            int page = 1;
            try {
                page = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
            }
            if (displayName.contains("Следующая")) {
                ((WantedCommand) RefontSearch.getInstance().getCommand("wanted").getExecutor()).openWantedList((Player) event.getWhoClicked(), page + 1);
            } else if (displayName.contains("Предыдущая")) {
                ((WantedCommand) RefontSearch.getInstance().getCommand("wanted").getExecutor()).openWantedList((Player) event.getWhoClicked(), page - 1);
            }
        }
    }
}
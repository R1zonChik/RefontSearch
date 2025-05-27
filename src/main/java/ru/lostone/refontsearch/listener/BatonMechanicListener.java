package ru.lostone.refontsearch.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.lostone.refontsearch.RefontSearch;

public class BatonMechanicListener implements Listener {

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player))
            return;

        Player damager = (Player) event.getDamager();
        Player target = (Player) event.getEntity();
        ItemStack item = damager.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName())
            return;
        if (item.getItemMeta().getDisplayName().equals("§6Полицейская Дубинка")) {
            target.sendMessage(ChatColor.RED + "Вас ударила полицейская дубинка!");
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1));
        }
    }
}
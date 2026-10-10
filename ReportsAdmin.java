package dev.sentinel.report;

import dev.sentinel.Sentinel;
import dev.sentinel.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

/** /reports: відкриття меню, телепорт до порушника, видалення жалоб. */
public final class ReportsAdmin implements Listener, CommandExecutor {
    private final Sentinel plugin;

    public ReportsAdmin(Sentinel plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command c, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Тільки для гравців.");
            return true;
        }
        if (!p.hasPermission("reports.view")) {
            p.sendMessage(Util.c("&cУ вас немає прав (reports.view)."));
            return true;
        }
        p.openInventory(new ReportsMenu(plugin, 0).getInventory());
        return true;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ReportsMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ReportsMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("reports.view")) {
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }
        if (e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getRawSlot();
        if (slot == ReportsMenu.SLOT_PREV && menu.page > 0) {
            reopen(p, menu.page - 1);
        } else if (slot == ReportsMenu.SLOT_NEXT && menu.hasNext) {
            reopen(p, menu.page + 1);
        } else if (slot >= 0 && slot < menu.slotTargets.size()) {
            UUID id = menu.slotTargets.get(slot);
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                if (!p.hasPermission("reports.delete")) {
                    p.sendMessage(Util.c("&cУ вас немає прав видаляти жалоби (reports.delete)."));
                    return;
                }
                int n = plugin.reports().removeFor(id);
                p.sendMessage(Util.c("&8[&cЖалоби&8] &aВидалено жалоб: " + n));
                reopen(p, menu.page);
            } else if (e.getClick().isLeftClick()) {
                teleport(p, id);
            }
        }
    }

    private void reopen(Player p, int page) {
        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(new ReportsMenu(plugin, page).getInventory()));
    }

    private void teleport(Player staff, UUID id) {
        Player target = Bukkit.getPlayer(id);
        if (target != null) {
            Bukkit.getScheduler().runTask(plugin, staff::closeInventory);
            staff.teleportAsync(target.getLocation());
            staff.sendMessage(Util.c("&8[&cЖалоби&8] &aТелепорт до гравця &f" + target.getName()));
            return;
        }
        Location loc = plugin.reports().getLocation(id);
        if (loc == null) {
            staff.sendMessage(Util.c("&8[&cЖалоби&8] &cГравець офлайн, а його остання позиція невідома."));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, staff::closeInventory);
        staff.teleportAsync(loc);
        staff.sendMessage(Util.c("&8[&cЖалоби&8] &aГравець офлайн — телепорт до його останньої позиції."));
    }
}

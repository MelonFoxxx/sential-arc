package dev.sentinel.anticheat;

import dev.sentinel.Sentinel;
import dev.sentinel.Util;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/** Відстежує відкриті інвентарі (для InvMove) і швидкість кліків (ChestStealer / InvCleaner). */
public final class InventoryCheck implements Listener {
    private final Sentinel plugin;

    public InventoryCheck(Sentinel plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) {
            PlayerData d = plugin.data(p);
            d.invOpen = true;
            d.invOpenAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            plugin.data(p).invOpen = false;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!plugin.enabled("inventory-clicks")) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        PlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        Util.prune(d.invClicks, now, 1000);
        d.invClicks.addLast(now);
        int max = (int) plugin.num("inventory-clicks.max-per-second", 30);
        if (d.invClicks.size() > max) {
            plugin.violations().flag(p, "InvClickSpeed", 1, d.invClicks.size() + " кліків/с");
            e.setCancelled(true);
        }
    }
}

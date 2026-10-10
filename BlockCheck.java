package dev.sentinel.anticheat;

import dev.sentinel.Sentinel;
import dev.sentinel.Util;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** Nuker, FastBreak-reach, BlockDirection (ламає не те, куди дивиться), FastPlace, Scaffold + збір даних для Xray. */
public final class BlockCheck implements Listener {
    private final Sentinel plugin;

    public BlockCheck(Sentinel plugin) {
        this.plugin = plugin;
    }

    private static boolean skip(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (skip(p)) return;
        PlayerData d = plugin.data(p);
        ViolationManager vm = plugin.violations();
        long now = System.currentTimeMillis();
        Block b = e.getBlock();

        // Дальність
        if (plugin.enabled("block-reach")) {
            double dist = p.getEyeLocation().distance(b.getLocation().add(0.5, 0.5, 0.5));
            double max = plugin.num("block-reach.max", 6.5);
            if (dist > max) {
                vm.flag(p, "BlockReach", 2, String.format("%.1f блоків", dist));
                e.setCancelled(true);
                return;
            }
        }

        // Nuker
        if (plugin.enabled("nuker")) {
            Util.prune(d.breaks, now, 1000);
            d.breaks.addLast(now);
            int max = (int) plugin.num("nuker.max-per-second", 10);
            if (d.breaks.size() > max) {
                vm.flag(p, "Nuker", 2, d.breaks.size() + " блоків/с");
                e.setCancelled(true);
                return;
            }
        }

        // Напрямок погляду
        if (plugin.enabled("block-direction") && !b.isPassable()) {
            Block t = p.getTargetBlockExact(8);
            boolean bad = t == null || t.getLocation().distanceSquared(b.getLocation()) > 2.25;
            if (bad) {
                d.dirBuf++;
                if (d.dirBuf >= 5) {
                    vm.flag(p, "BlockDirection", 2, "ламає блок, на який не дивиться");
                    d.dirBuf = 2;
                    e.setCancelled(true);
                    return;
                }
            } else {
                d.dirBuf = Math.max(0, d.dirBuf - 0.5);
            }
        }

        plugin.xray().onBreak(p, b);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (skip(p)) return;
        PlayerData d = plugin.data(p);
        ViolationManager vm = plugin.violations();
        long now = System.currentTimeMillis();
        Block placed = e.getBlockPlaced();

        // FastPlace
        if (plugin.enabled("fastplace")) {
            Util.prune(d.places, now, 1000);
            d.places.addLast(now);
            int max = (int) plugin.num("fastplace.max-per-second", 12);
            if (d.places.size() > max) {
                vm.flag(p, "FastPlace", 2, d.places.size() + " блоків/с");
                e.setCancelled(true);
                return;
            }
        }

        // Scaffold
        if (plugin.enabled("scaffold")) {
            boolean suspicious = false;
            String why = "";

            // 1) блок під ногами, а гравець дивиться вперед/вгору
            var loc = p.getLocation();
            boolean under = placed.getY() == loc.getBlockY() - 1
                    && Math.abs(placed.getX() + 0.5 - loc.getX()) < 1.3
                    && Math.abs(placed.getZ() + 0.5 - loc.getZ()) < 1.3;
            if (under && loc.getPitch() < 25) {
                suspicious = true;
                why = "блок під ногами при pitch=" + Math.round(loc.getPitch());
            }

            // 2) ставить не на той блок, на який дивиться
            Block t = p.getTargetBlockExact(7);
            if (t == null || t.getLocation().distanceSquared(e.getBlockAgainst().getLocation()) > 2.25) {
                suspicious = true;
                why = why.isEmpty() ? "ставить блок не за поглядом" : why;
            }

            if (suspicious) {
                d.scaffoldBuf++;
                if (d.scaffoldBuf >= 3) {
                    vm.flag(p, "Scaffold", 2, why);
                    d.scaffoldBuf = 1;
                    e.setCancelled(true);
                    return;
                }
            } else {
                d.scaffoldBuf = Math.max(0, d.scaffoldBuf - 0.5);
            }
        }
    }
}

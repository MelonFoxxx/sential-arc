package dev.sentinel.anticheat;

import dev.sentinel.Sentinel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public final class PlayerDataListener implements Listener {
    private final Sentinel plugin;

    public PlayerDataListener(Sentinel plugin) {
        this.plugin = plugin;
    }

    private void markTeleport(Player p) {
        PlayerData d = plugin.data(p);
        d.lastTeleport = System.currentTimeMillis();
        d.lastMoveMs = 0;
        d.windowStart = 0;
        d.moveCount = 0;
        d.airTicks = 0;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        markTeleport(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.reports().storeLocation(p.getUniqueId(), p.getLocation());
        plugin.removeData(p.getUniqueId());
        plugin.violations().remove(p.getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        markTeleport(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        markTeleport(e.getPlayer());
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        plugin.data(e.getPlayer()).lastVelocity = System.currentTimeMillis();
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent e) {
        plugin.data(e.getPlayer()).lastVelocity = System.currentTimeMillis();
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) {
            plugin.data(p).lastVelocity = System.currentTimeMillis();
        }
    }
}

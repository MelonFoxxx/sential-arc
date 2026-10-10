package dev.sentinel.anticheat;

import dev.sentinel.Sentinel;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Reach, KillAura/Aimbot (кут), удар крізь стіну, MultiAura, AutoClicker. */
public final class CombatCheck implements Listener {
    private final Sentinel plugin;

    public CombatCheck(Sentinel plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (!plugin.enabled("autoclicker")) return;
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        dev.sentinel.Util.prune(d.clicks, now, 1000);
        d.clicks.addLast(now);
        int max = (int) plugin.num("autoclicker.max-cps", 22);
        if (d.clicks.size() > max) {
            plugin.violations().flag(p, "AutoClicker", 1, "CPS=" + d.clicks.size());
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double angle(Vector a, Vector b) {
        if (a.lengthSquared() == 0 || b.lengthSquared() == 0) return 0;
        return Math.toDegrees(a.angle(b));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        Entity target = e.getEntity();
        if (target.getWorld() != p.getWorld()) return;

        PlayerData d = plugin.data(p);
        ViolationManager vm = plugin.violations();
        long now = System.currentTimeMillis();

        Location eye = p.getEyeLocation();
        Vector eyeV = eye.toVector();
        BoundingBox box = target.getBoundingBox();
        Vector nearest = new Vector(
                clamp(eyeV.getX(), box.getMinX(), box.getMaxX()),
                clamp(eyeV.getY(), box.getMinY(), box.getMaxY()),
                clamp(eyeV.getZ(), box.getMinZ(), box.getMaxZ()));
        double dist = nearest.distance(eyeV);
        Vector toNearest = nearest.clone().subtract(eyeV);

        // ---- Reach ----
        if (plugin.enabled("reach")) {
            double max = plugin.num("reach.max", 3.8);
            if (dist > max) {
                vm.flag(p, "Reach", 1.5, String.format("%.2f блоків (макс %.1f)", dist, max));
                e.setCancelled(true);
                return;
            }
        }

        // ---- MultiAura (різні цілі майже одночасно) ----
        if (plugin.enabled("multiaura")) {
            if (d.lastTarget != null && !d.lastTarget.equals(target.getUniqueId()) && now - d.lastAttack < 35) {
                vm.flag(p, "MultiAura", 2, "2 цілі за " + (now - d.lastAttack) + " мс");
                e.setCancelled(true);
                return;
            }
            d.lastTarget = target.getUniqueId();
            d.lastAttack = now;
        }

        // ---- KillAura / Aimbot: кут між поглядом і ціллю ----
        if (plugin.enabled("killaura-angle") && dist > 1.0) {
            Vector dir = eye.getDirection();
            double a = Math.min(angle(dir, toNearest), angle(dir, box.getCenter().subtract(eyeV)));
            if (a > plugin.num("killaura-angle.max-angle", 75)) {
                d.auraBuf++;
                if (d.auraBuf >= 3) {
                    vm.flag(p, "KillAura", 2, String.format("кут %.0f°", a));
                    d.auraBuf = 1;
                    e.setCancelled(true);
                    return;
                }
            } else {
                d.auraBuf = Math.max(0, d.auraBuf - 0.5);
            }
        }

        // ---- Удар крізь стіну ----
        if (plugin.enabled("hit-through-wall") && dist > 0.8) {
            RayTraceResult rt = p.getWorld().rayTraceBlocks(eye, toNearest.clone().normalize(),
                    dist - 0.05, FluidCollisionMode.NEVER, true);
            if (rt != null) {
                d.wallBuf++;
                if (d.wallBuf >= 3) {
                    vm.flag(p, "HitThroughWall", 2, "ціль за блоком");
                    d.wallBuf = 1;
                    e.setCancelled(true);
                }
            } else {
                d.wallBuf = Math.max(0, d.wallBuf - 0.5);
            }
        }
    }
}

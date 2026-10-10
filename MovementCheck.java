package dev.sentinel.anticheat;

import dev.sentinel.Sentinel;
import dev.sentinel.Util;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Speed, Fly, HighJump/Step, NoFall, Jesus, Phase/NoClip, Timer, InvMove. */
public final class MovementCheck implements Listener {
    private final Sentinel plugin;

    public MovementCheck(Sentinel plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::airTask, 5L, 5L);
    }

    public static boolean exempt(Player p, PlayerData d, long now) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR
                || p.getAllowFlight() || p.isFlying() || p.isGliding()
                || p.isInsideVehicle() || p.isRiptiding() || p.isDead() || p.isSleeping()
                || now - d.lastTeleport < 1500 || now - d.lastVelocity < 2000;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getWorld() != to.getWorld()) return;

        PlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();

        if (exempt(p, d, now)) {
            d.lastMoveMs = 0;
            d.windowStart = 0;
            d.moveCount = 0;
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double h = Math.hypot(dx, dz);
        double dt = d.lastMoveMs == 0 ? 1.0 : Math.min(10.0, Math.max(1.0, (now - d.lastMoveMs) / 50.0));
        d.lastMoveMs = now;
        if (Util.onIce(to)) d.lastIce = now;

        ViolationManager vm = plugin.violations();
        boolean special = Util.special(to);

        // ---- Timer (більше пакетів руху за секунду, ніж можливо) ----
        if (plugin.enabled("timer")) {
            if (d.windowStart == 0) {
                d.windowStart = now;
                d.moveCount = 0;
            }
            d.moveCount++;
            long el = now - d.windowStart;
            if (el >= 1000) {
                double rate = d.moveCount * 1000.0 / el;
                d.windowStart = now;
                d.moveCount = 0;
                if (rate > plugin.num("timer.max-moves-per-second", 26)) {
                    d.timerBuf++;
                    if (d.timerBuf >= 3) {
                        vm.flag(p, "Timer", 1, String.format("%.1f пакетів/с", rate));
                        d.timerBuf = 1;
                        e.setCancelled(true);
                        return;
                    }
                } else {
                    d.timerBuf = Math.max(0, d.timerBuf - 0.5);
                }
            }
        }

        // ---- Speed ----
        if (plugin.enabled("speed") && !special) {
            double base = p.getWalkSpeed() / 0.2 * 0.2158 * 1.3; // спринт
            PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
            if (sp != null) base *= 1.0 + 0.2 * (sp.getAmplifier() + 1);
            double max = (base + 0.2) * plugin.num("speed.tolerance", 1.10);
            if (now - d.lastIce < 1500) max *= 2.2;
            ItemStack boots = p.getInventory().getBoots();
            if (boots != null && boots.containsEnchantment(Enchantment.SOUL_SPEED)) max *= 2.5;
            double perTick = h / dt;
            if (perTick > max) {
                d.speedBuf++;
                if (d.speedBuf >= 4) {
                    vm.flag(p, "Speed", 1, String.format("%.2f b/tick (макс %.2f)", perTick, max));
                    d.speedBuf = 2;
                    e.setCancelled(true);
                    return;
                }
            } else {
                d.speedBuf = Math.max(0, d.speedBuf - 0.5);
            }
        }

        // ---- HighJump / Step ----
        if (plugin.enabled("highjump") && !special
                && dy > plugin.num("highjump.max-dy", 0.85)
                && !p.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                && !p.hasPotionEffect(PotionEffectType.LEVITATION)) {
            vm.flag(p, "HighJump/Step", 2, String.format("dy=%.2f", dy));
            e.setCancelled(true);
            return;
        }

        // ---- Jesus (ходьба по воді) ----
        if (plugin.enabled("jesus")) {
            Block feet = to.getBlock();
            Block below = to.clone().subtract(0, 0.3, 0).getBlock();
            if (below.isLiquid() && feet.isPassable() && !feet.isLiquid()
                    && Math.abs(dy) < 0.005 && h > 0.05 && !p.isSwimming()) {
                d.jesusBuf++;
                if (d.jesusBuf >= 6) {
                    d.jesusBuf = 0;
                    if (!Util.entitySupport(p)) {
                        vm.flag(p, "Jesus", 1, "ходьба по рідині");
                        e.setCancelled(true);
                        return;
                    }
                }
            } else {
                d.jesusBuf = Math.max(0, d.jesusBuf - 0.5);
            }
        }

        // ---- NoFall (клієнт каже "я на землі", хоча під ним повітря) ----
        if (plugin.enabled("nofall") && !special) {
            boolean claim = p.isOnGround();
            if (claim && dy < -0.08 && !Util.nearGround(from) && !Util.nearGround(to)) {
                d.noFallBuf++;
                if (d.noFallBuf >= 3) {
                    vm.flag(p, "NoFall", 1, "onGround у повітрі");
                    d.noFallBuf = 0;
                    e.setCancelled(true);
                    return;
                }
            } else {
                d.noFallBuf = Math.max(0, d.noFallBuf - 0.25);
            }
        }

        // ---- Phase / NoClip ----
        if (plugin.enabled("phase") && h < 8) {
            Block toFeet = to.getBlock();
            Block toHead = to.clone().add(0, 1.0, 0).getBlock();
            Block fromFeet = from.getBlock();
            Block fromHead = from.clone().add(0, 1.0, 0).getBlock();
            boolean enteredFeet = Util.occluding(toFeet) && !Util.occluding(fromFeet);
            boolean enteredHead = Util.occluding(toHead) && !Util.occluding(fromHead);
            if (enteredFeet || enteredHead) {
                d.phaseBuf++;
                if (d.phaseBuf >= 2) {
                    vm.flag(p, "Phase", 2, "вхід у твердий блок " + (enteredFeet ? toFeet : toHead).getType());
                    d.phaseBuf = 0;
                    e.setCancelled(true);
                    return;
                }
            } else {
                d.phaseBuf = Math.max(0, d.phaseBuf - 0.25);
            }
        }

        // ---- InvMove (рух з відкритим контейнером) ----
        if (plugin.enabled("invmove")) {
            if (d.invOpen && !special && now - d.invOpenAt > 800 && h > 0.15 && now - d.lastIce > 1500) {
                d.invMoveBuf++;
                if (d.invMoveBuf >= 6) {
                    vm.flag(p, "InvMove", 1, "рух з відкритим інвентарем");
                    d.invMoveBuf = 0;
                    e.setCancelled(true);
                    return;
                }
            } else if (!d.invOpen) {
                d.invMoveBuf = 0;
            }
        }
    }

    /** Fly: кожні 5 тіків дивимось, чи гравець у повітрі довше за можливе й не падає. */
    private void airTask() {
        if (!plugin.enabled("fly")) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data(p);
            Location l = p.getLocation();
            if (exempt(p, d, now) || Util.special(l)
                    || p.hasPotionEffect(PotionEffectType.LEVITATION)
                    || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                    || p.hasPotionEffect(PotionEffectType.JUMP_BOOST)) {
                d.airTicks = 0;
                d.lastY = l.getY();
                continue;
            }
            if (Util.nearGround(l)) {
                d.airTicks = 0;
                d.lastY = l.getY();
                d.lastGround = l;
                d.flyBuf = Math.max(0, d.flyBuf - 0.5);
                continue;
            }
            d.airTicks += 5;
            double dy = l.getY() - d.lastY;
            d.lastY = l.getY();
            // У вільному падінні через 25+ тіків швидкість падіння >1 блок/тік. Якщо майже не падає — fly.
            if (d.airTicks >= 25 && dy > -0.5) {
                d.flyBuf++;
                if (d.flyBuf >= 2) {
                    d.flyBuf = 0;
                    d.airTicks = 0;
                    if (Util.entitySupport(p)) continue;
                    plugin.violations().flag(p, "Fly", 2, String.format("dy=%.2f за 5 тіків", dy));
                    if (d.lastGround != null) p.teleportAsync(d.lastGround);
                }
            }
        }
    }
}

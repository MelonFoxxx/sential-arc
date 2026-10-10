package dev.sentinel.anticheat;

import dev.sentinel.Sentinel;
import dev.sentinel.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ViolationManager {
    private final Sentinel plugin;
    private final Map<UUID, Map<String, Double>> vls = new HashMap<>();
    private final Map<String, Long> lastAlert = new HashMap<>();

    public ViolationManager(Sentinel plugin) {
        this.plugin = plugin;
        // Кожні 30 секунд знімаємо по 1 VL
        Bukkit.getScheduler().runTaskTimer(plugin, this::decay, 600L, 600L);
    }

    public void flag(Player p, String check, double add, String info) {
        if (p.hasPermission("sentinel.bypass")) return;
        Map<String, Double> m = vls.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        double v = m.merge(check, add, Double::sum);

        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":" + check;
        if (now - lastAlert.getOrDefault(key, 0L) >= 1000) {
            lastAlert.put(key, now);
            Component msg = Util.c("&8[&cSentinel&8] &f" + p.getName() + " &7→ &c" + check
                    + " &7(VL &e" + String.format("%.1f", v) + "&7) &8" + info);
            for (Player a : Bukkit.getOnlinePlayers()) {
                if (a.hasPermission("sentinel.alerts")) a.sendMessage(msg);
            }
            if (plugin.getConfig().getBoolean("alerts.console", true)) {
                plugin.getLogger().info(p.getName() + " flagged " + check
                        + " VL=" + String.format("%.1f", v) + " " + info);
            }
        }

        double max = plugin.getConfig().getDouble("punishment.max-vl", 25);
        if (v >= max) {
            m.put(check, 0.0);
            if (!plugin.getConfig().getStringList("punishment.alert-only").contains(check)) {
                String name = p.getName();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String cmd : plugin.getConfig().getStringList("punishment.commands")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                cmd.replace("%player%", name).replace("%check%", check));
                    }
                });
            }
        }
    }

    public Map<String, Double> get(UUID id) {
        return vls.getOrDefault(id, Map.of());
    }

    public void reset(UUID id) {
        vls.remove(id);
    }

    public void remove(UUID id) {
        vls.remove(id);
        lastAlert.keySet().removeIf(k -> k.startsWith(id.toString()));
    }

    private void decay() {
        for (Map<String, Double> m : vls.values()) {
            m.replaceAll((k, v) -> Math.max(0, v - 1));
            m.values().removeIf(v -> v <= 0);
        }
    }
}

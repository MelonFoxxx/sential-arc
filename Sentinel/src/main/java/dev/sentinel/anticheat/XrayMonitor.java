package dev.sentinel.anticheat;

import dev.sentinel.Sentinel;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Статистичний детектор Xray: порівнює частку цінних руд серед останніх N видобутих блоків.
 * Справжнє приховування руд від клієнта робить вбудований Anti-Xray Paper (див. README) —
 * цей модуль лише сповіщає адмінів про підозрілу статистику.
 */
public final class XrayMonitor {
    private static final Set<Material> FILLER = Set.of(
            Material.STONE, Material.DEEPSLATE, Material.NETHERRACK, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.BLACKSTONE,
            Material.BASALT, Material.COBBLESTONE, Material.COBBLED_DEEPSLATE);

    private final Sentinel plugin;

    public XrayMonitor(Sentinel plugin) {
        this.plugin = plugin;
    }

    /** 0 = звичайний камінь, 1 = руда, 2 = рідкісна руда, -1 = ігноруємо. */
    private static int kind(Material m) {
        if (FILLER.contains(m)) return 0;
        String n = m.name();
        if (m == Material.ANCIENT_DEBRIS || n.contains("DIAMOND_ORE") || n.contains("EMERALD_ORE")) return 2;
        if (n.endsWith("_ORE")) return 1;
        return -1;
    }

    public void onBreak(Player p, Block b) {
        if (!plugin.enabled("xray")) return;
        int k = kind(b.getType());
        if (k < 0) return;

        PlayerData d = plugin.data(p);
        int window = Math.max(20, (int) plugin.num("xray.window", 100));
        d.xray.addLast(k);
        while (d.xray.size() > window) d.xray.pollFirst();
        if (d.xray.size() < window) return;

        int filler = 0, ore = 0, rare = 0;
        for (int v : d.xray) {
            if (v == 0) filler++;
            else {
                ore++;
                if (v == 2) rare++;
            }
        }
        // Якщо видобуто мало каменю (напр. вибух ліжками в Незері) — вибірка нерепрезентативна.
        if (filler < window * 0.6) return;

        double rareRatio = rare / (double) window;
        double oreRatio = ore / (double) window;
        if (rareRatio > plugin.num("xray.max-rare-ratio", 0.08)
                || oreRatio > plugin.num("xray.max-ore-ratio", 0.30)) {
            plugin.violations().flag(p, "Xray", 5, String.format(
                    "рідкісних %.0f%%, усіх руд %.0f%% з останніх %d блоків",
                    rareRatio * 100, oreRatio * 100, window));
            d.xray.clear();
        }
    }
}

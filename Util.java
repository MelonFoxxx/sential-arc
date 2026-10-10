package dev.sentinel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Util {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Util() {}

    public static Component c(String s) {
        return LEGACY.deserialize(s);
    }

    /** Компонент без курсиву (для назв і lore предметів). */
    public static Component ci(String s) {
        return c(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Прибирає кольорові коди з тексту гравця. */
    public static String clean(String s) {
        return s.replace("&", "").replace("§", "");
    }

    public static ItemStack item(Material m, String name, String... lore) {
        ItemStack is = new ItemStack(m);
        ItemMeta im = is.getItemMeta();
        im.displayName(ci(name));
        if (lore.length > 0) {
            im.lore(Arrays.stream(lore).map(Util::ci).toList());
        }
        is.setItemMeta(im);
        return is;
    }

    public static ItemStack glow(ItemStack is) {
        ItemMeta im = is.getItemMeta();
        im.setEnchantmentGlintOverride(true);
        is.setItemMeta(im);
        return is;
    }

    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.trim().split("\\s+")) {
            while (w.length() > width) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.add(w.substring(0, width));
                w = w.substring(width);
            }
            if (cur.length() > 0 && cur.length() + 1 + w.length() > width) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    public static void prune(ArrayDeque<Long> q, long now, long windowMs) {
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
    }

    // ---------- Світ / блоки ----------

    /** Чи є під гравцем реальна опора (твердий блок), незалежно від прапорця onGround від клієнта. */
    public static boolean nearGround(Location l) {
        World w = l.getWorld();
        double[] offs = {-0.31, 0.0, 0.31};
        double[] ys = {-0.05, -0.5};
        for (double ox : offs) {
            for (double oz : offs) {
                for (double oy : ys) {
                    Block b = w.getBlockAt(
                            (int) Math.floor(l.getX() + ox),
                            (int) Math.floor(l.getY() + oy),
                            (int) Math.floor(l.getZ() + oz));
                    if (!b.isPassable() || b.isLiquid()) return true;
                }
            }
        }
        return false;
    }

    public static boolean isSpecial(Material m) {
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN
                || m == Material.COBWEB || m == Material.HONEY_BLOCK || m == Material.SLIME_BLOCK
                || m == Material.POWDER_SNOW || m == Material.SWEET_BERRY_BUSH
                || m == Material.KELP || m == Material.KELP_PLANT
                || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS
                || Tag.CLIMBABLE.isTagged(m) || Tag.BEDS.isTagged(m);
    }

    /** Блоки поруч, які дають нестандартну фізику (вода, драбини, павутиння, слайм, мед тощо). */
    public static boolean special(Location l) {
        World w = l.getWorld();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        for (int dy = -1; dy <= 2; dy++) {
            if (isSpecial(w.getBlockAt(x, y + dy, z).getType())) return true;
        }
        return false;
    }

    public static boolean onIce(Location l) {
        Material m = l.getWorld().getBlockAt(l.getBlockX(),
                (int) Math.floor(l.getY() - 0.5), l.getBlockZ()).getType();
        return m == Material.ICE || m == Material.PACKED_ICE
                || m == Material.BLUE_ICE || m == Material.FROSTED_ICE;
    }

    public static boolean occluding(Block b) {
        return b.getType().isOccluding();
    }

    /** Чи стоїть гравець на човні/вагонетці/шалкері (тоді немає блока опори). */
    public static boolean entitySupport(Player p) {
        for (Entity e : p.getNearbyEntities(1.5, 2.0, 1.5)) {
            if (e instanceof Boat || e instanceof Minecart || e instanceof Shulker) return true;
        }
        return false;
    }
}

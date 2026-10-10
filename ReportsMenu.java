package dev.sentinel.report;

import dev.sentinel.Sentinel;
import dev.sentinel.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Адмін-меню зі списком гравців, на яких подали жалобу (/reports). */
public final class ReportsMenu implements InventoryHolder {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    public static final int PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final Inventory inv;
    public final int page;
    public final boolean hasNext;
    /** Порушник для кожного слота 0..44. */
    public final List<UUID> slotTargets = new ArrayList<>();

    public ReportsMenu(Sentinel plugin, int requestedPage) {
        List<Map.Entry<UUID, List<Report>>> list =
                new ArrayList<>(plugin.reports().groupedByReported().entrySet());
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(requestedPage, pages - 1));
        this.hasNext = page < pages - 1;
        this.inv = Bukkit.createInventory(this, 54, Util.c("&8Жалоби &7(" + (page + 1) + "/" + pages + ")"));

        ItemStack filler = Util.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int start = page * PER_PAGE;
        int end = Math.min(list.size(), start + PER_PAGE);
        for (int i = start, slot = 0; i < end; i++, slot++) {
            Map.Entry<UUID, List<Report>> en = list.get(i);
            inv.setItem(slot, head(en.getKey(), en.getValue()));
            slotTargets.add(en.getKey());
        }

        if (page > 0) inv.setItem(SLOT_PREV, Util.item(Material.ARROW, "&eПопередня сторінка"));
        if (hasNext) inv.setItem(SLOT_NEXT, Util.item(Material.ARROW, "&eНаступна сторінка"));
        inv.setItem(SLOT_INFO, Util.item(Material.BOOK, "&6Усього порушників: &f" + list.size(),
                "&eЛКМ &7— телепорт до гравця",
                "&7(офлайн — до останньої позиції)",
                "&eShift+ПКМ &7— видалити жалоби"));
    }

    private static ItemStack head(UUID uuid, List<Report> reports) {
        boolean online = Bukkit.getPlayer(uuid) != null;
        String name = reports.get(0).reportedName();

        List<Component> lore = new ArrayList<>();
        lore.add(Util.ci(online ? "&aОнлайн" : "&7Офлайн"));
        lore.add(Util.ci("&7Жалоб: &c" + reports.size()));
        lore.add(Component.empty());

        int shown = 0;
        for (Report r : reports) {
            if (shown++ >= 4) {
                lore.add(Util.ci("&8… ще " + (reports.size() - 4)));
                break;
            }
            lore.add(Util.ci("&6" + DATE.format(Instant.ofEpochMilli(r.time()))
                    + " &7від &f" + Util.clean(r.reporterName())));
            lore.add(Util.ci("&7 Причина: &f" + Util.clean(r.reason())));
            if (!r.comment().isBlank()) {
                for (String line : Util.wrap(Util.clean(r.comment()), 36)) {
                    lore.add(Util.ci("&7 » &f" + line));
                }
            }
        }
        lore.add(Component.empty());
        lore.add(Util.ci("&eЛКМ &7— телепорт"));
        lore.add(Util.ci("&eShift+ПКМ &7— видалити жалоби"));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) head.getItemMeta();
        m.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        m.displayName(Util.ci("&c" + Util.clean(name)));
        m.lore(lore);
        head.setItemMeta(m);
        return head;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}

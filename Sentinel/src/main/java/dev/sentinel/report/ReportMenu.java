package dev.sentinel.report;

import dev.sentinel.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Меню подачі жалоби (/report). */
public final class ReportMenu implements InventoryHolder {
    public static final int SLOT_NICK = 10;
    public static final int FIRST_REASON_SLOT = 12; // 12..16
    public static final int SLOT_COMMENT = 28;
    public static final int SLOT_SUBMIT = 31;
    public static final int SLOT_CANCEL = 34;

    private static final Material[] ICONS = {
            Material.DIAMOND_SWORD, Material.WRITABLE_BOOK, Material.HOPPER, Material.TNT, Material.BARRIER
    };

    private final Inventory inv;
    private final ReportSession session;
    private final List<String> reasons;

    public ReportMenu(ReportSession session, List<String> reasons) {
        this.session = session;
        this.reasons = reasons;
        this.inv = Bukkit.createInventory(this, 36, Util.c("&8Нова жалоба"));
        render();
    }

    public ReportSession session() {
        return session;
    }

    public void render() {
        ItemStack filler = Util.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        inv.setItem(SLOT_NICK, Util.item(Material.NAME_TAG, "&eНікнейм порушника",
                "&7Зараз: " + (session.targetName == null ? "&cне вказано" : "&a" + session.targetName),
                "",
                "&aНатисніть і напишіть нік у чат"));

        for (int i = 0; i < reasons.size() && i < ICONS.length; i++) {
            boolean sel = i == session.reasonIndex;
            ItemStack it = Util.item(ICONS[i], (sel ? "&a✔ " : "&f") + Util.clean(reasons.get(i)),
                    sel ? "&aОбрано" : "&7Натисніть, щоб обрати");
            if (sel) Util.glow(it);
            inv.setItem(FIRST_REASON_SLOT + i, it);
        }

        String shown = session.comment.isBlank() ? "&8немає"
                : "&f" + Util.clean(session.comment.length() > 40
                ? session.comment.substring(0, 40) + "…" : session.comment);
        inv.setItem(SLOT_COMMENT, Util.item(Material.BOOK, "&eОпис / причина",
                "&7Зараз: " + shown,
                "",
                "&aНатисніть і напишіть опис у чат"));

        inv.setItem(SLOT_SUBMIT, Util.item(Material.LIME_CONCRETE, "&a&lНадіслати жалобу",
                "&7Порушник та причина мають бути вказані"));
        inv.setItem(SLOT_CANCEL, Util.item(Material.RED_CONCRETE, "&cСкасувати"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}

package dev.sentinel.report;

import dev.sentinel.Sentinel;
import dev.sentinel.Util;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /report: меню, введення ніка/опису через чат, збереження жалоби. */
public final class ReportFlow implements Listener, CommandExecutor, TabCompleter {
    private enum Input { NICK, COMMENT }

    private final Sentinel plugin;
    private final Map<UUID, ReportSession> sessions = new HashMap<>();
    private final Map<UUID, Input> awaiting = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ReportFlow(Sentinel plugin) {
        this.plugin = plugin;
    }

    private List<String> reasons() {
        List<String> r = new ArrayList<>(plugin.getConfig().getStringList("report.reasons"));
        if (r.isEmpty()) r = new ArrayList<>(List.of("Читерство", "Образи", "Спам", "Гріфінг", "Інше"));
        return r.size() > 5 ? r.subList(0, 5) : r;
    }

    private static String err(String s) {
        return "&8[&cЖалоба&8] &c" + s;
    }

    private static String ok(String s) {
        return "&8[&aЖалоба&8] &a" + s;
    }

    private void open(Player p) {
        ReportSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        p.openInventory(new ReportMenu(s, reasons()).getInventory());
    }

    // ---------- /report ----------

    @Override
    public boolean onCommand(CommandSender sender, Command c, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Тільки для гравців.");
            return true;
        }
        ReportSession s = new ReportSession();
        sessions.put(p.getUniqueId(), s);
        awaiting.remove(p.getUniqueId());
        if (a.length >= 1 && !resolveTarget(p, s, a[0])) {
            // помилку вже показано; меню все одно відкриємо
        }
        open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a[0].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }

    private boolean resolveTarget(Player p, ReportSession s, String nick) {
        if (!nick.matches("[A-Za-z0-9_.]{2,20}")) {
            p.sendMessage(Util.c(err("Некоректний нік.")));
            return false;
        }
        UUID id;
        String name;
        Player online = Bukkit.getPlayerExact(nick);
        if (online != null) {
            id = online.getUniqueId();
            name = online.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(nick);
            if (off == null || off.getName() == null) {
                p.sendMessage(Util.c(err("Гравця з ніком " + nick + " не знайдено (він ніколи не заходив на сервер).")));
                return false;
            }
            id = off.getUniqueId();
            name = off.getName();
        }
        if (id.equals(p.getUniqueId())) {
            p.sendMessage(Util.c(err("Не можна скаржитись на себе.")));
            return false;
        }
        s.targetUuid = id;
        s.targetName = name;
        return true;
    }

    // ---------- Кліки в меню ----------

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ReportMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ReportMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        ReportSession s = menu.session();
        int slot = e.getRawSlot();
        List<String> reasons = reasons();

        if (slot == ReportMenu.SLOT_NICK) {
            prompt(p, Input.NICK, "Напишіть у чат нік гравця, на якого скаржитесь. &7(або «cancel»)");
        } else if (slot == ReportMenu.SLOT_COMMENT) {
            prompt(p, Input.COMMENT, "Напишіть у чат опис порушення. &7(або «cancel»)");
        } else if (slot >= ReportMenu.FIRST_REASON_SLOT
                && slot < ReportMenu.FIRST_REASON_SLOT + reasons.size()) {
            s.reasonIndex = slot - ReportMenu.FIRST_REASON_SLOT;
            menu.render();
        } else if (slot == ReportMenu.SLOT_SUBMIT) {
            submit(p, s);
        } else if (slot == ReportMenu.SLOT_CANCEL) {
            sessions.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        }
    }

    private void prompt(Player p, Input type, String msg) {
        awaiting.put(p.getUniqueId(), type);
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        p.sendMessage(Util.c(ok(msg)));
    }

    // ---------- Введення з чату ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Input in = awaiting.get(p.getUniqueId());
        if (in == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(p, in, msg));
    }

    private void handleInput(Player p, Input in, String msg) {
        if (awaiting.remove(p.getUniqueId()) == null) return;
        ReportSession s = sessions.get(p.getUniqueId());
        if (s == null) return;

        if (!msg.equalsIgnoreCase("cancel") && !msg.equalsIgnoreCase("скасувати")) {
            switch (in) {
                case NICK -> resolveTarget(p, s, msg);
                case COMMENT -> s.comment = msg.length() > 200 ? msg.substring(0, 200) : msg;
            }
        }
        open(p);
    }

    // ---------- Надсилання ----------

    private void submit(Player p, ReportSession s) {
        List<String> reasons = reasons();
        if (s.targetUuid == null) {
            p.sendMessage(Util.c(err("Спочатку вкажіть нік порушника.")));
            return;
        }
        if (s.reasonIndex < 0 || s.reasonIndex >= reasons.size()) {
            p.sendMessage(Util.c(err("Оберіть причину.")));
            return;
        }
        if (s.reasonIndex == reasons.size() - 1 && s.comment.isBlank()) {
            p.sendMessage(Util.c(err("Для цієї причини напишіть опис.")));
            return;
        }

        long cd = plugin.getConfig().getLong("report.cooldown-seconds", 60) * 1000L;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            long left = (cd - (now - last) + 999) / 1000;
            p.sendMessage(Util.c(err("Зачекайте ще " + left + " с перед наступною жалобою.")));
            return;
        }

        String reason = reasons.get(s.reasonIndex);
        plugin.reports().add(s.targetUuid, s.targetName, p.getUniqueId(), p.getName(), reason, s.comment);

        Player target = Bukkit.getPlayer(s.targetUuid);
        if (target != null) plugin.reports().storeLocation(target.getUniqueId(), target.getLocation());

        cooldowns.put(p.getUniqueId(), now);
        sessions.remove(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        p.sendMessage(Util.c(ok("Жалобу на " + s.targetName + " надіслано. Дякуємо!")));

        var notice = Util.c("&8[&cЖалоба&8] &f" + p.getName() + " &7→ &c" + s.targetName
                + " &8(" + Util.clean(reason) + ") &7— /reports");
        for (Player a : Bukkit.getOnlinePlayers()) {
            if (a.hasPermission("reports.view")) a.sendMessage(notice);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        sessions.remove(id);
        awaiting.remove(id);
    }
}

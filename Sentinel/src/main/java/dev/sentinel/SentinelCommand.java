package dev.sentinel;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SentinelCommand implements CommandExecutor, TabCompleter {
    private final Sentinel plugin;

    public SentinelCommand(Sentinel plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            s.sendMessage(Util.c("&8[&cSentinel&8] &7/sentinel reload | vl <гравець> | reset <гравець>"));
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage(Util.c("&8[&cSentinel&8] &aКонфіг перезавантажено."));
            }
            case "vl", "reset" -> {
                if (a.length < 2) {
                    s.sendMessage(Util.c("&cВкажіть гравця."));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    s.sendMessage(Util.c("&cГравець не в мережі."));
                    return true;
                }
                if (a[0].equalsIgnoreCase("reset")) {
                    plugin.violations().reset(t.getUniqueId());
                    s.sendMessage(Util.c("&8[&cSentinel&8] &aVL гравця " + t.getName() + " скинуто."));
                } else {
                    Map<String, Double> m = plugin.violations().get(t.getUniqueId());
                    if (m.isEmpty()) {
                        s.sendMessage(Util.c("&8[&cSentinel&8] &7У " + t.getName() + " порушень немає."));
                    } else {
                        s.sendMessage(Util.c("&8[&cSentinel&8] &7VL гравця &f" + t.getName() + "&7:"));
                        m.forEach((k, v) -> s.sendMessage(Util.c(" &c" + k + " &7- &e" + String.format("%.1f", v))));
                    }
                }
            }
            default -> s.sendMessage(Util.c("&cНевідома підкоманда."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : List.of("reload", "vl", "reset")) {
                if (o.startsWith(a[0].toLowerCase())) out.add(o);
            }
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}

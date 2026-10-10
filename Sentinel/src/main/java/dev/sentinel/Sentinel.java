package dev.sentinel;

import dev.sentinel.anticheat.*;
import dev.sentinel.report.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Sentinel extends JavaPlugin {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private ViolationManager violations;
    private ReportManager reports;
    private XrayMonitor xray;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        violations = new ViolationManager(this);
        reports = new ReportManager(this);
        xray = new XrayMonitor(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerDataListener(this), this);
        pm.registerEvents(new MovementCheck(this), this);
        pm.registerEvents(new CombatCheck(this), this);
        pm.registerEvents(new BlockCheck(this), this);
        pm.registerEvents(new InventoryCheck(this), this);

        ReportFlow flow = new ReportFlow(this);
        pm.registerEvents(flow, this);
        getCommand("report").setExecutor(flow);
        getCommand("report").setTabCompleter(flow);

        ReportsAdmin admin = new ReportsAdmin(this);
        pm.registerEvents(admin, this);
        getCommand("reports").setExecutor(admin);

        SentinelCommand cmd = new SentinelCommand(this);
        getCommand("sentinel").setExecutor(cmd);
        getCommand("sentinel").setTabCompleter(cmd);

        checkPaperAntiXray();
        getLogger().info("Sentinel увімкнено.");
    }

    @Override
    public void onDisable() {
        if (reports != null) reports.save();
    }

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new PlayerData());
    }

    public void removeData(UUID id) {
        data.remove(id);
    }

    public ViolationManager violations() { return violations; }
    public ReportManager reports() { return reports; }
    public XrayMonitor xray() { return xray; }

    public boolean enabled(String check) {
        return getConfig().getBoolean("checks." + check + ".enabled", true);
    }

    public double num(String path, double def) {
        return getConfig().getDouble("checks." + path, def);
    }

    /** Справжній Anti-Xray (підміна руд у пакетах чанків) робить сам Paper — перевіряємо, чи він увімкнений. */
    private void checkPaperAntiXray() {
        File f = new File(Bukkit.getWorldContainer(), "config/paper-world-defaults.yml");
        if (!f.exists()) {
            getLogger().warning("Не знайдено config/paper-world-defaults.yml — перевірте налаштування anti-xray вручну.");
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        boolean on = y.getBoolean("anti-xray.enabled", false);
        int mode = y.getInt("anti-xray.engine-mode", 1);
        if (!on) {
            getLogger().warning("Paper Anti-Xray ВИМКНЕНО! У config/paper-world-defaults.yml встановіть "
                    + "anti-xray.enabled: true та engine-mode: 2 (див. README).");
        } else {
            getLogger().info("Paper Anti-Xray увімкнено (engine-mode " + mode + ").");
        }
    }
}

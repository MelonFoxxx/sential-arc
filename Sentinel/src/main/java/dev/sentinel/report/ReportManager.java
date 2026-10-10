package dev.sentinel.report;

import dev.sentinel.Sentinel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ReportManager {
    private record StoredLoc(String world, double x, double y, double z, float yaw, float pitch) {
        Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final Sentinel plugin;
    private final File file;
    private final List<Report> reports = new ArrayList<>();
    private final Map<UUID, StoredLoc> locs = new HashMap<>();
    private int nextId = 1;
    private boolean dirty;

    public ReportManager(Sentinel plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
        load();
        // Раз на 5 хвилин зберігаємо останні позиції гравців
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 6000L, 6000L);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next-id", 1);

        ConfigurationSection rs = y.getConfigurationSection("reports");
        if (rs != null) {
            for (String key : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    reports.add(new Report(Integer.parseInt(key),
                            UUID.fromString(Objects.requireNonNull(s.getString("reported"))),
                            s.getString("reported-name", "?"),
                            UUID.fromString(Objects.requireNonNull(s.getString("reporter"))),
                            s.getString("reporter-name", "?"),
                            s.getString("reason", "?"),
                            s.getString("comment", ""),
                            s.getLong("time")));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Пошкоджена жалоба #" + key + " пропущена.");
                }
            }
        }

        ConfigurationSection ls = y.getConfigurationSection("locations");
        if (ls != null) {
            for (String key : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(key);
                if (s == null || s.getString("world") == null) continue;
                try {
                    locs.put(UUID.fromString(key), new StoredLoc(s.getString("world"),
                            s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                            (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        for (Report r : reports) {
            String p = "reports." + r.id();
            y.set(p + ".reported", r.reportedUuid().toString());
            y.set(p + ".reported-name", r.reportedName());
            y.set(p + ".reporter", r.reporterUuid().toString());
            y.set(p + ".reporter-name", r.reporterName());
            y.set(p + ".reason", r.reason());
            y.set(p + ".comment", r.comment());
            y.set(p + ".time", r.time());
        }
        for (Map.Entry<UUID, StoredLoc> en : locs.entrySet()) {
            String p = "locations." + en.getKey();
            StoredLoc l = en.getValue();
            y.set(p + ".world", l.world());
            y.set(p + ".x", l.x());
            y.set(p + ".y", l.y());
            y.set(p + ".z", l.z());
            y.set(p + ".yaw", l.yaw());
            y.set(p + ".pitch", l.pitch());
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Не вдалося зберегти reports.yml: " + e.getMessage());
        }
    }

    public synchronized Report add(UUID reported, String reportedName, UUID reporter,
                                   String reporterName, String reason, String comment) {
        Report r = new Report(nextId++, reported, reportedName, reporter, reporterName,
                reason, comment, System.currentTimeMillis());
        reports.add(r);
        save();
        return r;
    }

    /** Жалоби, згруповані по порушнику; спочатку ті, на кого скаржились останніми. */
    public synchronized Map<UUID, List<Report>> groupedByReported() {
        Map<UUID, List<Report>> m = new HashMap<>();
        for (Report r : reports) m.computeIfAbsent(r.reportedUuid(), k -> new ArrayList<>()).add(r);
        m.values().forEach(l -> l.sort(Comparator.comparingLong(Report::time).reversed()));
        List<Map.Entry<UUID, List<Report>>> entries = new ArrayList<>(m.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().get(0).time(), a.getValue().get(0).time()));
        Map<UUID, List<Report>> out = new LinkedHashMap<>();
        for (var en : entries) out.put(en.getKey(), en.getValue());
        return out;
    }

    public synchronized int removeFor(UUID reported) {
        int before = reports.size();
        reports.removeIf(r -> r.reportedUuid().equals(reported));
        int removed = before - reports.size();
        if (removed > 0) save();
        return removed;
    }

    public synchronized void storeLocation(UUID id, Location l) {
        if (l == null || l.getWorld() == null) return;
        locs.put(id, new StoredLoc(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        dirty = true;
    }

    public synchronized Location getLocation(UUID id) {
        StoredLoc l = locs.get(id);
        return l == null ? null : l.toLocation();
    }
}

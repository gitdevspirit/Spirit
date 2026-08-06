package myau.management;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Personal player blacklist with a reason attached to each entry, persisted
 * to disk so it survives a full game restart. Player names are stored
 * lowercase internally for lookups, but the original-cased name used with
 * .blacklist is preserved for display.
 */
public class BlacklistManager {

    private static final BlacklistManager INSTANCE = new BlacklistManager();
    private static final File FILE = new File("./config/Myau/blacklist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Entry {
        public String name;
        public String reason;

        public Entry() {
        }

        public Entry(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }

    // key = lowercase name
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static BlacklistManager getInstance() {
        return INSTANCE;
    }

    private BlacklistManager() {
        load();
    }

    /** Returns true if this was a new entry (false if it just updated an existing reason). */
    public synchronized boolean add(String name, String reason) {
        String key = name.toLowerCase();
        boolean isNew = !entries.containsKey(key);

        String finalReason = (reason == null || reason.trim().isEmpty())
                ? "No reason given"
                : reason.trim();

        entries.put(key, new Entry(name, finalReason));
        save();

        return isNew;
    }

    public synchronized boolean remove(String name) {
        boolean removed = entries.remove(name.toLowerCase()) != null;

        if (removed) {
            save();
        }

        return removed;
    }

    public synchronized boolean isBlacklisted(String name) {
        return entries.containsKey(name.toLowerCase());
    }

    public synchronized String getReason(String name) {
        Entry entry = entries.get(name.toLowerCase());
        return entry != null ? entry.reason : null;
    }

    public synchronized Map<String, String> getAllReasons() {
        Map<String, String> result = new LinkedHashMap<>();

        for (Entry entry : entries.values()) {
            result.put(entry.name, entry.reason);
        }

        return result;
    }

    private synchronized void save() {
        try {
            File dir = FILE.getParentFile();

            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                System.err.println("[Blacklist] Failed to create directory: " + dir.getAbsolutePath());
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(entries, writer);
            }
        } catch (Exception e) {
            System.err.println("[Blacklist] Failed to save " + FILE.getAbsolutePath() + ": " + e);
            e.printStackTrace();
        }
    }

    private synchronized void load() {
        try {
            if (!FILE.exists()) {
                System.out.println("[Blacklist] No existing file at " + FILE.getAbsolutePath() + " — starting empty.");
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<LinkedHashMap<String, Entry>>() {}.getType();
                Map<String, Entry> loaded = GSON.fromJson(reader, type);

                if (loaded != null) {
                    entries.clear();
                    entries.putAll(loaded);
                    System.out.println("[Blacklist] Loaded " + entries.size() + " entries from " + FILE.getAbsolutePath());
                } else {
                    System.err.println("[Blacklist] " + FILE.getAbsolutePath() + " parsed to null — file may be empty or corrupt.");
                }
            }
        } catch (Exception e) {
            System.err.println("[Blacklist] Failed to load " + FILE.getAbsolutePath() + ": " + e);
            e.printStackTrace();
        }
    }
}

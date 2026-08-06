package myau.management;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Personal player blacklist with persistent storage.
 */
public class BlacklistManager {

    private static final BlacklistManager INSTANCE = new BlacklistManager();

    private static final File FILE = new File(
            Minecraft.getMinecraft().mcDataDir,
            "config/Myau/blacklist.json"
    );

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static class Entry {
        String name;
        String reason;

        Entry(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }

    // key = lowercase player name
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static BlacklistManager getInstance() {
        return INSTANCE;
    }

    private BlacklistManager() {
        load();
    }

    public synchronized boolean add(String name, String reason) {
        String key = name.toLowerCase();

        boolean isNew = !entries.containsKey(key);

        if (reason == null || reason.trim().isEmpty()) {
            reason = "No reason given";
        } else {
            reason = reason.trim();
        }

        entries.put(key, new Entry(name, reason));
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
        Map<String, String> map = new LinkedHashMap<>();

        for (Entry entry : entries.values()) {
            map.put(entry.name, entry.reason);
        }

        return map;
    }

    private synchronized void save() {
        try {
            File parent = FILE.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(entries, writer);
            }

            System.out.println("[Myau] Saved blacklist (" + entries.size() + " players)");
            System.out.println("[Myau] File: " + FILE.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("[Myau] Failed to save blacklist!");
            e.printStackTrace();
        }
    }

    private synchronized void load() {
        try {
            if (!FILE.exists()) {
                System.out.println("[Myau] No blacklist file found.");
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {

                Type type = new TypeToken<LinkedHashMap<String, Entry>>() {
                }.getType();

                Map<String, Entry> loaded = GSON.fromJson(reader, type);

                entries.clear();

                if (loaded != null) {
                    entries.putAll(loaded);
                }
            }

            System.out.println("[Myau] Loaded blacklist (" + entries.size() + " players)");
            System.out.println("[Myau] File: " + FILE.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("[Myau] Failed to load blacklist!");
            e.printStackTrace();
        }
    }
}

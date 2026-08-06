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

public class BlacklistManager {

    private static final BlacklistManager INSTANCE = new BlacklistManager();

    private static final File FILE = new File(
            Minecraft.getMinecraft().mcDataDir,
            "config/Myau/blacklist.json"
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Entry {
        public String name;
        public String reason;

        public Entry() {
        }

        public Entry(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static BlacklistManager getInstance() {
        return INSTANCE;
    }

    private BlacklistManager() {
        load();
    }

    public synchronized boolean add(String name, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            reason = "No reason given";
        }

        String key = name.toLowerCase();
        boolean isNew = !entries.containsKey(key);

        entries.put(key, new Entry(name, reason.trim()));
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
        return entry == null ? null : entry.reason;
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
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(entries, writer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void load() {
        entries.clear();

        private synchronized void load() {
    entries.clear();

    System.out.println("=========== BLACKLIST LOAD ===========");
    System.out.println("File exists: " + FILE.exists());
    System.out.println("Path: " + FILE.getAbsolutePath());

    try {
        if (!FILE.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(FILE)) {

            Type type = new TypeToken<Map<String, Entry>>() {}.getType();

            Map<String, Entry> loaded = GSON.fromJson(reader, type);

            System.out.println("Loaded: " + loaded);

            if (loaded != null) {
                entries.putAll(loaded);
            }

            System.out.println("Entries size: " + entries.size());
            System.out.println("[Blacklist] Loaded " + entries.size() + " players.");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}
        try {
            if (!FILE.exists()) {
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {

                Type type = new TypeToken<Map<String, Entry>>() {}.getType();

                Map<String, Entry> loaded = GSON.fromJson(reader, type);

                if (loaded != null) {
                    entries.putAll(loaded);
                }

                System.out.println("[Blacklist] Loaded " + entries.size() + " players.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

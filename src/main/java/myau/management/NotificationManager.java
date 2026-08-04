package myau.management;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {

    public static class NotificationEntry {
        public final String message;
        public final long startMillis;
        public final long durationMillis;
        public final int color; // RGB

        public NotificationEntry(String message, long durationMillis) {
            this(message, durationMillis, 0xFFFFFF);
        }

        public NotificationEntry(String message, long durationMillis, int color) {
            this.message = message;
            this.durationMillis = durationMillis;
            this.color = color;
            this.startMillis = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return this.durationMillis >= 0 && System.currentTimeMillis() - this.startMillis >= this.durationMillis;
        }

        public long getAge() {
            return System.currentTimeMillis() - this.startMillis;
        }
    }

    private final List<NotificationEntry> entries = new ArrayList<>();

    // Batches rapid-fire module toggles (e.g. loading a config that flips many
    // modules at once) into a single "N modules toggled" notification instead
    // of spamming one card per module.
    private static final long BATCH_WINDOW_MS = 150L;
    private static final int  BATCH_THRESHOLD = 3;

    private final List<PendingToggle> pendingToggles = new ArrayList<>();
    private long batchStartMillis = -1L;

    private static final class PendingToggle {
        final String name;
        final boolean enabled;
        final long durationMillis;
        final int color;

        PendingToggle(String name, boolean enabled, long durationMillis, int color) {
            this.name = name;
            this.enabled = enabled;
            this.durationMillis = durationMillis;
            this.color = color;
        }
    }

    /** Use this (instead of add()) for module enable/disable notifications so rapid bursts get batched. */
    public synchronized void addToggle(String moduleName, boolean enabled, long durationMillis, int color) {
        pendingToggles.add(new PendingToggle(moduleName, enabled, durationMillis, color));

        if (batchStartMillis < 0) {
            batchStartMillis = System.currentTimeMillis();
        }
    }

    private synchronized void flushBatchIfDue() {
        if (batchStartMillis < 0) return;
        if (System.currentTimeMillis() - batchStartMillis < BATCH_WINDOW_MS) return;

        List<PendingToggle> batch = new ArrayList<>(pendingToggles);
        pendingToggles.clear();
        batchStartMillis = -1L;

        List<PendingToggle> enabledList = new ArrayList<>();
        List<PendingToggle> disabledList = new ArrayList<>();

        for (PendingToggle t : batch) {
            (t.enabled ? enabledList : disabledList).add(t);
        }

        if (enabledList.size() >= BATCH_THRESHOLD) {
            PendingToggle sample = enabledList.get(0);
            entries.add(new NotificationEntry(
                    enabledList.size() + " modules toggled", sample.durationMillis, sample.color));
        } else {
            for (PendingToggle t : enabledList) {
                entries.add(new NotificationEntry(t.name + " toggled", t.durationMillis, t.color));
            }
        }

        if (disabledList.size() >= BATCH_THRESHOLD) {
            PendingToggle sample = disabledList.get(0);
            entries.add(new NotificationEntry(
                    disabledList.size() + " modules untoggled", sample.durationMillis, sample.color));
        } else {
            for (PendingToggle t : disabledList) {
                entries.add(new NotificationEntry(t.name + " untoggled", t.durationMillis, t.color));
            }
        }
    }

    public synchronized void add(String message) {
        this.add(message, 3000L);
    }

    public synchronized void add(String message, long durationMillis) {
        this.add(message, durationMillis, 0xFFFFFF);
    }

    public synchronized void add(String message, int color) {
        this.add(message, 3000L, color);
    }

    public synchronized void add(String message, long durationMillis, int color) {
        this.entries.add(new NotificationEntry(message, durationMillis, color));
    }

    public synchronized List<NotificationEntry> getActive() {
        flushBatchIfDue();

        // cleanup expired entries and return a copy of active entries (newest last)
        Iterator<NotificationEntry> it = this.entries.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired()) {
                it.remove();
            }
        }
        return new ArrayList<>(this.entries);
    }

    public synchronized void clear() {
        this.entries.clear();
        this.pendingToggles.clear();
        this.batchStartMillis = -1L;
    }
}

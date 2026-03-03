package myau.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal self-contained Discord IPC client.
 * Connects to Discord's local named pipe and sends Rich Presence JSON.
 */
public class DiscordIPC {

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME     = 1;
    private static final int OP_CLOSE     = 2;

    private RandomAccessFile pipe;
    private boolean connected = false;
    private final long clientId;

    public DiscordIPC(long clientId) {
        this.clientId = clientId;
    }

    public boolean isConnected() { return connected; }

    public boolean connect() {
        try {
            pipe = openPipe();
            if (pipe == null) return false;

            // Handshake
            String handshake = "{\"v\":1,\"client_id\":\"" + clientId + "\"}";
            write(OP_HANDSHAKE, handshake);

            // Read response (discard, just check we get something back)
            read();
            connected = true;
            return true;
        } catch (Exception e) {
            connected = false;
            closePipe();
            return false;
        }
    }

    public void setActivity(String details, String state, long startTimestamp) {
        if (!connected) return;
        try {
            String payload = "{"
                + "\"cmd\":\"SET_ACTIVITY\","
                + "\"args\":{"
                    + "\"pid\":" + getPid() + ","
                    + "\"activity\":{"
                        + "\"details\":\"" + escape(details) + "\","
                        + "\"state\":\"" + escape(state) + "\","
                        + "\"timestamps\":{\"start\":" + startTimestamp + "},"
                        + "\"assets\":{\"large_image\":\"spirit_logo\",\"large_text\":\"Spirit\"}"
                    + "}"
                + "},"
                + "\"nonce\":\"" + System.currentTimeMillis() + "\""
                + "}";
            write(OP_FRAME, payload);
            read(); // consume response
        } catch (Exception e) {
            connected = false;
            closePipe();
        }
    }

    public void close() {
        try {
            if (pipe != null) write(OP_CLOSE, "{}");
        } catch (Exception ignored) {}
        closePipe();
        connected = false;
    }

    // ── Pipe helpers ──────────────────────────────────────────────────────────

    private RandomAccessFile openPipe() {
        // Try pipes 0-9
        for (int i = 0; i < 10; i++) {
            String path = getPipePath(i);
            try {
                RandomAccessFile raf = new RandomAccessFile(path, "rw");
                return raf;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getPipePath(int i) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "\\\\.\\pipe\\discord-ipc-" + i;
        }
        // Unix: check XDG_RUNTIME_DIR, /tmp, /var/folders
        String[] dirs = {
            System.getenv("XDG_RUNTIME_DIR"),
            System.getenv("TMPDIR"),
            System.getenv("TMP"),
            System.getenv("TEMP"),
            "/tmp"
        };
        for (String dir : dirs) {
            if (dir != null && !dir.isEmpty()) {
                return dir + "/discord-ipc-" + i;
            }
        }
        return "/tmp/discord-ipc-" + i;
    }

    private void write(int opcode, String json) throws Exception {
        byte[] data = json.getBytes("UTF-8");
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(data.length);
        buf.put(data);
        pipe.write(buf.array());
    }

    private String read() throws Exception {
        byte[] header = new byte[8];
        pipe.readFully(header);
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        buf.getInt(); // opcode
        int len = buf.getInt();
        if (len <= 0 || len > 65536) return "";
        byte[] data = new byte[len];
        pipe.readFully(data);
        return new String(data, "UTF-8");
    }

    private void closePipe() {
        try { if (pipe != null) pipe.close(); } catch (Exception ignored) {}
        pipe = null;
    }

    private int getPid() {
        try {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return Integer.parseInt(name.split("@")[0]);
        } catch (Exception e) { return 0; }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

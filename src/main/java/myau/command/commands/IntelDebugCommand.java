package myau.command.commands;

import myau.command.Command;
import myau.ui.intel.IntelManager;
import myau.util.ChatUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * .inteldebug <name>  — prints raw Urchin API response to chat
 */
public class IntelDebugCommand extends Command {

    public IntelDebugCommand() {
        super("inteldebug", "idebug");
        setDescription("Debug Urchin API response for a player.");
    }

    @Override
    public void execute(String[] args) {
        String name = args.length > 0 ? args[0] : "OFFICER_SPIRIT";
        reply("&7Querying Urchin for &f" + name + "&7...");

        new Thread(() -> {
            try {
                String url = "https://urchin.ws/cubelify"
                        + "?id=&name=" + java.net.URLEncoder.encode(name, "UTF-8")
                        + "&sources="
                        + "&key=" + java.net.URLEncoder.encode(IntelManager.urchinApiKey, "UTF-8");

                reply("&7URL: &f" + url);

                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setRequestProperty("User-Agent", "Spirit-Client/1.0");

                int code = con.getResponseCode();
                reply("&7HTTP: &f" + code);

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? con.getInputStream() : con.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                String resp = sb.toString();
                // Split into 200-char chunks for chat
                for (int i = 0; i < resp.length(); i += 200) {
                    reply("&f" + resp.substring(i, Math.min(i + 200, resp.length())));
                }
            } catch (Exception e) {
                reply("&cError: " + e.getMessage());
            }
        }).start();
    }
}

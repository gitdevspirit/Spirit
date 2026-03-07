package myau.command.commands;

import myau.command.Command;
import myau.ui.intel.IntelDebugGui;
import myau.ui.intel.IntelManager;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class IntelDebugCommand extends Command {

    public IntelDebugCommand() {
        super("inteldebug", "idebug");
        setDescription("Opens a GUI showing the raw Urchin API response for a player.");
    }

    @Override
    public void execute(String[] args) {
        String name = args.length > 0 ? args[0] : "OFFICER_SPIRIT";
        reply("&7Querying Urchin for &f" + name + "&7...");

        new Thread(() -> {
            try {
                String url = "https://urchin.ws/cubelify"
                        + "?id="
                        + "&name=" + java.net.URLEncoder.encode(name, "UTF-8")
                        + "&sources="
                        + "&key=" + IntelManager.urchinApiKey;

                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setRequestProperty("User-Agent", "Spirit-Client/1.0");

                int code = con.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? con.getInputStream() : con.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();

                String result = "URL: " + url + "\nHTTP: " + code + "\n\n" + sb.toString();

                Minecraft.getMinecraft().addScheduledTask(() ->
                        Minecraft.getMinecraft().displayGuiScreen(
                                new IntelDebugGui("Urchin Debug — " + name, result)));

            } catch (Exception e) {
                reply("&cError: " + e.getMessage());
            }
        }).start();
    }
}

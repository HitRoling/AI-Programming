package com.hitroling.mt5desktop;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String MT5_URL = "https://download.mql5.com/cdn/web/metaquotes.software.corp/mt5/mt5setup.exe";
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 70, 48, 48);
        root.setBackgroundColor(Color.rgb(17, 24, 39));

        TextView title = new TextView(this);
        title.setText("MT5 Phone Desktop");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("Installs the Windows-compatible runtime and downloads MetaTrader 5 with MetaEditor.");
        info.setTextColor(Color.rgb(209, 213, 219));
        info.setTextSize(16);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 24, 0, 36);
        root.addView(info, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT));

        Button setup = button("SET UP MT5");
        setup.setOnClickListener(v -> startSetup());
        root.addView(setup, fullWidth(150));

        Button open = button("OPEN WINDOWS RUNTIME");
        open.setOnClickListener(v -> openWinlator());
        LinearLayout.LayoutParams openParams = fullWidth(150);
        openParams.topMargin = 20;
        root.addView(open, openParams);

        status = new TextView(this);
        status.setText("Ready");
        status.setTextColor(Color.rgb(147, 197, 253));
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 34, 0, 0);
        root.addView(status, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private void startSetup() {
        status.setText("Downloading MT5 and Windows runtime…");
        enqueueDownload(MT5_URL, "mt5setup.exe", "MetaTrader 5 installer");

        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/brunodev85/winlator/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "MT5-Phone-Desktop");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
                reader.close();

                JSONArray assets = new JSONObject(json.toString()).getJSONArray("assets");
                String apkUrl = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.toLowerCase().endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url");
                        break;
                    }
                }
                if (apkUrl == null) throw new Exception("No APK in latest release");
                String finalApkUrl = apkUrl;
                runOnUiThread(() -> {
                    enqueueDownload(finalApkUrl, "Winlator.apk", "Windows-compatible runtime");
                    status.setText("Downloads started. Install Winlator.apk, then open this app again.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("MT5 downloaded. Tap to open the Winlator download page.");
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/brunodev85/winlator/releases/latest")));
                });
            }
        }).start();
    }

    private void enqueueDownload(String url, String fileName, String description) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setDescription(description);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        manager.enqueue(request);
    }

    private void openWinlator() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.winlator");
        if (launch != null) {
            startActivity(launch);
        } else {
            Toast.makeText(this, "Install Winlator.apk from Downloads first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/brunodev85/winlator/releases/latest")));
        }
    }
}

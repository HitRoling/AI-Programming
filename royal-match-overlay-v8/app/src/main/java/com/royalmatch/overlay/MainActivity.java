package com.royalmatch.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,32,32,32);
        TextView t = new TextView(this);
        t.setText("Royal Match Selector\n\nGrant 'Display over other apps', then tap START OVERLAY. Keep the GameGuardian V8 script running hidden.");
        t.setTextSize(18);
        root.addView(t);
        Button grant = new Button(this); grant.setText("GRANT OVERLAY PERMISSION"); root.addView(grant);
        Button start = new Button(this); start.setText("START OVERLAY"); root.addView(start);
        Button stop = new Button(this); stop.setText("STOP OVERLAY"); root.addView(stop);
        grant.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) { grant.performClick(); return; }
            startService(new Intent(this, OverlayService.class));
            finish();
        });
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        setContentView(root);
    }
}

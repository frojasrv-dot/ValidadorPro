package com.serviem.validador;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.os.Handler;
import android.graphics.Color;

public class MainActivity extends Activity {
    TextView tv;
    Handler h = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv = new TextView(this);
        tv.setTextSize(20);
        tv.setTextColor(Color.BLACK);
        tv.setPadding(60, 60, 60, 60);
        setContentView(tv);

        // AUTO-DETECCIÓN DE PERMISO: Si no está activo, abre el menú del sistema
        String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (listeners == null || !listeners.contains(getPackageName())) {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }

        actualizar();
    }

    void actualizar() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String log = getSharedPreferences("Debug", MODE_PRIVATE).getString("log", "Esperando pago...");
        
        tv.setText("🛡️ VALIDADOR PRO\n\n" +
                   "ID EQUIPO:\n" + id + "\n\n" +
                   "ESTADO:\n" + log);
                   
        h.postDelayed(this::actualizar, 2000);
    }
}

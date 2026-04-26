package com.serviem.validador;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.provider.Settings;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    TextView tv;
    Handler handler = new Handler();
    Runnable actualizador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Diseño básico de la pantalla
        tv = new TextView(this);
        tv.setTextSize(18);
        tv.setPadding(60, 60, 60, 60);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setBackgroundColor(Color.WHITE);
        tv.setTextColor(Color.BLACK);
        setContentView(tv);

        // Iniciamos el ciclo de actualización en tiempo real
        actualizador = new Runnable() {
            @Override
            public void run() {
                actualizarInformacion();
                handler.postDelayed(this, 2000); // Revisa cada 2 segundos
            }
        };
        handler.post(actualizador);
    }

    private void actualizarInformacion() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        // Leer lo que el NotificationService guardó al recibir el Yape
        SharedPreferences pref = getSharedPreferences("DebugLog", Context.MODE_PRIVATE);
        String ultimoEstado = pref.getString("ultimo", "Esperando primera notificación...");

        String textoPantalla = "🛡️ VALIDADOR PRO ACTIVO\n" +
                               "--------------------------------\n" +
                               "ID DISPOSITIVO:\n" + androidId + "\n\n" +
                               "ESTADO DEL SENSOR:\n" +
                               "● " + ultimoEstado + "\n\n" +
                               "--------------------------------\n" +
                               "Huaral - San Fernando Mallki";
        
        tv.setText(textoPantalla);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(actualizador); // Detener el reloj al cerrar
    }
}

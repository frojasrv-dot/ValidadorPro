package com.serviem.validador;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Context;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            // Extraer texto de la notificación
            CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
            CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");
            String fullText = (title + " " + text).toLowerCase();

            // Si es un pago de Yape o Plin
            if (fullText.contains("yape") || fullText.contains("plin")) {
                String monto = "0.00";
                if (fullText.contains("s/")) {
                    monto = fullText.split("s/")[1].trim().split(" ")[0].replaceAll("[^0-9.]", "");
                }

                // Guardar log para la pantalla del celular
                getSharedPreferences("Debug", MODE_PRIVATE).edit().putString("log", "Ultimo: " + monto).apply();
                
                // Enviar al servidor de PythonAnywhere
                enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), monto);
            }
        } catch (Exception e) {}
    }

    private void enviarSvr(String id, String m) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://serviem.pythonanywhere.com/api/notificacion").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                
                JSONObject j = new JSONObject();
                j.put("device_id", id);
                j.put("monto", m);
                j.put("metodo", "AUTO");
                j.put("operacion", "OP_" + System.currentTimeMillis());

                try (OutputStream os = c.getOutputStream()) {
                    os.write(j.toString().getBytes());
                }
                c.getResponseCode();
                c.disconnect();
            } catch (Exception e) {}
        }).start();
    }
}

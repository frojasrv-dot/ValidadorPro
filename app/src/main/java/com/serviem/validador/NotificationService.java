package com.serviem.validador;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Context;
import android.provider.Settings;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Bundle extras = sbn.getNotification().extras;
            String title = String.valueOf(extras.getCharSequence("android.title"));
            String text = String.valueOf(extras.getCharSequence("android.text"));
            String fullText = (title + " " + text).toLowerCase();

            // LOG DE EMERGENCIA: Para ver qué capturó exactamente
            getSharedPreferences("Debug", MODE_PRIVATE).edit()
                .putString("log", "Ultima notif: " + title).apply();

            // Si el mensaje es de Yape o Plin
            if (fullText.contains("yape") || fullText.contains("plin") || fullText.contains("recibido")) {
                
                // Intentamos capturar el monto de varias formas
                String monto = "0.00";
                if (fullText.contains("s/")) {
                    try {
                        monto = fullText.split("s/")[1].trim().split(" ")[0].replaceAll("[^0-9.]", "");
                    } catch (Exception e) { monto = "Error Monto"; }
                }

                // Guardar log detallado para que me digas qué sale
                getSharedPreferences("Debug", MODE_PRIVATE).edit()
                    .putString("log", "¡PAGO DETECTADO!\nOrigen: " + title + "\nMonto: " + monto).apply();
                
                enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), monto, fullText.contains("yape") ? "YAPE" : "PLIN");
            }
        } catch (Exception e) {
            getSharedPreferences("Debug", MODE_PRIVATE).edit().putString("log", "Error sensor: " + e.getMessage()).apply();
        }
    }

    private void enviarSvr(String id, String m, String tipo) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://serviem.pythonanywhere.com/api/notificacion").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                
                JSONObject j = new JSONObject();
                j.put("device_id", id);
                j.put("monto", m);
                j.put("metodo", tipo);
                j.put("operacion", "AUTO_" + System.currentTimeMillis());

                try (OutputStream os = c.getOutputStream()) {
                    os.write(j.toString().getBytes());
                }
                c.getResponseCode();
                c.disconnect();
            } catch (Exception e) {}
        }).start();
    }
}

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
            if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;

            Bundle extras = sbn.getNotification().extras;
            
            // Extracción ultra-segura para evitar que la app se cuelgue
            Object titleObj = extras.get("android.title");
            Object textObj = extras.get("android.text");
            
            String title = titleObj != null ? titleObj.toString() : "";
            String text = textObj != null ? textObj.toString() : "";
            String fullText = (title + " " + text).toLowerCase();

            // 1. CHIVATO: Mostrar cualquier cosa que llegue para confirmar que el sensor no está sordo
            getSharedPreferences("Debug", MODE_PRIVATE).edit()
                .putString("log", "Última lectura:\nT: " + title + "\nM: " + text).apply();

            // 2. Filtro (Si dice pago, confirmación, yape o plin)
            if (fullText.contains("yape") || fullText.contains("plin") || fullText.contains("pago") || fullText.contains("confirmación")) {
                
                String monto = "0.00";
                String operacion = "AUTO_" + System.currentTimeMillis();
                String metodo = fullText.contains("plin") ? "PLIN" : "YAPE"; // Asume Yape si no dice Plin

                // Extracción de Monto simplificada (Busca números como 0.10 o 10.50)
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+[.,][0-9]{2})").matcher(fullText);
                    if (m.find()) monto = m.group(1).replace(",", ".");
                } catch (Exception e) {}

                // Extracción de Operación (Busca un bloque de 6 a 12 números seguidos)
                try {
                    java.util.regex.Matcher opM = java.util.regex.Pattern.compile("([0-9]{6,12})").matcher(fullText);
                    if (opM.find()) operacion = opM.group(1);
                } catch (Exception e) {}

                // Mostrar el resultado limpio en la pantalla blanca
                getSharedPreferences("Debug", MODE_PRIVATE).edit()
                    .putString("log", "✅ PAGO DETECTADO\nMetodo: " + metodo + "\nMonto: S/ " + monto + "\nOp: " + operacion + "\n\nTEXTO: " + title).apply();
                
                enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), monto, metodo, operacion);
            }
        } catch (Exception e) {
            // Si hay un error, nos lo chismosea en la pantalla
            getSharedPreferences("Debug", MODE_PRIVATE).edit()
                .putString("log", "Error interno: " + e.getMessage()).apply();
        }
    }

    private void enviarSvr(String id, String m, String met, String op) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://serviem.pythonanywhere.com/api/notificacion").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(10000);

                JSONObject j = new JSONObject();
                j.put("device_id", id);
                j.put("monto", m);
                j.put("metodo", met);
                j.put("operacion", op);

                try (OutputStream os = c.getOutputStream()) {
                    os.write(j.toString().getBytes("UTF-8"));
                }
                c.getResponseCode();
                c.disconnect();
            } catch (Exception e) {}
        }).start();
    }
}

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;

            Bundle extras = sbn.getNotification().extras;
            
            // 1. MODO RAYOS X: Extraer TODO el texto de cualquier rincón de la notificación
            StringBuilder volcado = new StringBuilder();
            for (String key : extras.keySet()) {
                Object val = extras.get(key);
                if (val != null) {
                    volcado.append(val.toString()).append(" | ");
                }
            }
            String fullText = volcado.toString().toLowerCase();

            // 2. Filtro de Billeteras
            if (fullText.contains("yape") || fullText.contains("plin") || fullText.contains("pago") || fullText.contains("confirmación")) {
                
                String monto = "0.00";
                String operacion = "AUTO_" + System.currentTimeMillis();
                String metodo = fullText.contains("plin") ? "PLIN" : "YAPE";

                // 3. Extractor de Monto (Busca cualquier número con 2 decimales en todo el texto)
                try {
                    Matcher m = Pattern.compile("([0-9]+[.,][0-9]{2})").matcher(fullText);
                    if (m.find()) monto = m.group(1).replace(",", ".");
                } catch (Exception e) {}

                // 4. Extractor de Operación (Busca bloques numéricos de 7 a 12 dígitos, ej: 25815851)
                try {
                    Matcher opM = Pattern.compile("(?<!\\d)(\\d{7,12})(?!\\d)").matcher(fullText);
                    if (opM.find()) operacion = opM.group(1);
                } catch (Exception e) {}

                // Mostrar en pantalla para depuración (mostramos un resumen del texto crudo)
                String textoCrudo = fullText.length() > 150 ? fullText.substring(0, 150) + "..." : fullText;
                
                getSharedPreferences("Debug", MODE_PRIVATE).edit()
                    .putString("log", "✅ PAGO DETECTADO\nMetodo: " + metodo + "\nMonto: S/ " + monto + "\nOp: " + operacion + "\n\nDATOS CRUDOS:\n" + textoCrudo).apply();
                
                enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), monto, metodo, operacion);
            }
        } catch (Exception e) {
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

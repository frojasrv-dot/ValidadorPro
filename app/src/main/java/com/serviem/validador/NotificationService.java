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
        // Ejecutamos la lógica en un hilo nuevo inmediatamente para no bloquear el sensor
        new Thread(() -> {
            try {
                if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;

                Bundle extras = sbn.getNotification().extras;
                StringBuilder volcado = new StringBuilder();
                for (String key : extras.keySet()) {
                    Object val = extras.get(key);
                    if (val != null) volcado.append(val.toString()).append(" | ");
                }
                String fullText = volcado.toString().toLowerCase();

                // Filtro de plataformas de pago
                if (fullText.contains("yape") || fullText.contains("plin") || fullText.contains("pago") || fullText.contains("confirmación")) {
                    
                    String monto = "0.00";
                    String operacion = "SEG_" + System.currentTimeMillis();
                    String metodo = fullText.contains("plin") ? "PLIN" : "YAPE";

                    // Extractor de Monto (Ajustado para el formato "s/ 0.1" de Yape)
                    try {
                        Matcher m = Pattern.compile("s/\\s*([0-9]+([.,][0-9]+)?)").matcher(fullText);
                        if (m.find()) {
                            monto = m.group(1).replace(",", ".");
                        }
                    } catch (Exception e) {}

                    // Extractor de Código de Seguridad o Nro de Operación
                    try {
                        Matcher opM = Pattern.compile("(seguridad es:|operación:|op:|nro:)\\s*([0-9]+)").matcher(fullText);
                        if (opM.find()) {
                            operacion = opM.group(2);
                        }
                    } catch (Exception e) {}

                    // Registro visual en la app
                    getSharedPreferences("Debug", MODE_PRIVATE).edit()
                        .putString("log", "✅ PAGO RECIBIDO\nMetodo: " + metodo + "\nMonto: S/ " + monto + "\nID: " + operacion).apply();
                    
                    // Envío al servidor de validación
                    enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), monto, metodo, operacion);
                }
            } catch (Exception e) {}
        }).start();
    }

    private void enviarSvr(String id, String m, String met, String op) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://serviem.pythonanywhere.com/api/notificacion").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(15000); // 15 segundos de margen para redes lentas

            JSONObject j = new JSONObject();
            j.put("device_id", id);
            j.put("monto", m);
            j.put("metodo", met);
            j.put("operacion", op);
            j.put("timestamp", System.currentTimeMillis());

            try (OutputStream os = c.getOutputStream()) {
                byte[] input = j.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) {}
    }
}

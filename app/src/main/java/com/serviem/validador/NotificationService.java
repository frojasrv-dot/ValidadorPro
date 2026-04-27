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
        new Thread(() -> {
            try {
                if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;

                Bundle extras = sbn.getNotification().extras;
                StringBuilder volcado = new StringBuilder();
                
                // Extraer título y texto (lo más común)
                String title = String.valueOf(extras.getCharSequence("android.title"));
                String text = String.valueOf(extras.getCharSequence("android.text"));
                volcado.append(title).append(" | ").append(text).append(" | ");

                // Escaneo profundo de otros campos (por si viene agrupado)
                CharSequence[] lines = extras.getCharSequenceArray("android.textLines");
                if (lines != null) {
                    for (CharSequence line : lines) volcado.append(line).append(" | ");
                }
                
                String fullText = volcado.toString().toLowerCase();

                // Detección mejorada para PLIN (BBVA, Interbank, etc. a veces no dicen "pago")
                boolean esPago = fullText.contains("yape") || fullText.contains("plin") || 
                                 fullText.contains("pago") || fullText.contains("confirmación") ||
                                 fullText.contains("transferencia") || fullText.contains("recibiste") ||
                                 fullText.contains("envió s/");

                if (esPago) {
                    String monto = "0.00";
                    String operacion = "AUTO_" + System.currentTimeMillis();
                    String metodo = fullText.contains("plin") || fullText.contains("interbank") || fullText.contains("bbva") ? "PLIN" : "YAPE";

                    // Extractor de Monto (Soporta s/0.1, s/ 0.10, s/. 10)
                    Matcher mMonto = Pattern.compile("(s/|s/\\.)\\s*([0-9]+([.,][0-9]+)?)").matcher(fullText);
                    if (mMonto.find()) {
                        monto = mMonto.group(2).replace(",", ".");
                    }

                    // Extractor de Operación o Código de Seguridad
                    Matcher mOp = Pattern.compile("(operación|seguridad es:|op:|nro:)\\s*([0-9]+)").matcher(fullText);
                    if (mOp.find()) {
                        operacion = mOp.group(2);
                    }

                    // Actualizar UI con la ÚLTIMA captura
                    getSharedPreferences("Debug", MODE_PRIVATE).edit()
                        .putString("log", "🔔 ÚLTIMA CAPTURA: " + metodo + 
                                         "\n💰 MONTO: S/ " + monto + 
                                         "\n🔢 ID: " + operacion +
                                         "\n\n📝 RAW: " + title).apply();
                    
                    // Envío independiente al servidor
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
            c.setDoOutput(true);
            c.setConnectTimeout(10000);

            JSONObject j = new JSONObject();
            j.put("device_id", id);
            j.put("monto", m);
            j.put("metodo", met);
            j.put("operacion", op);

            try (OutputStream os = c.getOutputStream()) {
                os.write(j.toString().getBytes("utf-8"));
            }
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) {}
    }
}

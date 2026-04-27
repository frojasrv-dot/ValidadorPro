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
            String packageName = sbn.getPackageName().toLowerCase();
            Bundle extras = sbn.getNotification().extras;
            String title = String.valueOf(extras.getCharSequence("android.title"));
            String text = String.valueOf(extras.getCharSequence("android.text"));
            String fullText = (title + " " + text).toLowerCase();

            // 1. Identificar Plataforma
            String metodo = "OTRO";
            if (packageName.contains("yape") || fullText.contains("yape")) metodo = "YAPE";
            else if (packageName.contains("plin") || fullText.contains("plin")) metodo = "PLIN";
            else if (packageName.contains("izipay") || fullText.contains("izipay")) metodo = "IZIPAY";

            // Solo procesamos si detectamos una de nuestras plataformas
            if (!metodo.equals("OTRO") || fullText.contains("confirmación") || fullText.contains("pago")) {
                
                // 2. Extraer Monto (Busca formato 0.00 o S/ 0.00)
                String monto = "0.00";
                Pattern pMonto = Pattern.compile("(\\d+\\.\\d{2})");
                Matcher mMonto = pMonto.matcher(fullText);
                if (mMonto.find()) {
                    monto = mMonto.group(1);
                }

                // 3. Extraer Número de Operación (Busca bloques de 8 a 12 dígitos)
                String operacion = "AUTO_" + System.currentTimeMillis();
                Pattern pOp = Pattern.compile("(\\d{8,12})");
                Matcher mOp = pOp.matcher(fullText);
                if (mOp.find()) {
                    operacion = mOp.group(1);
                }

                // Actualizar pantalla para que veas el resultado
                getSharedPreferences("Debug", MODE_PRIVATE).edit()
                    .putString("log", "✅ DETECTADO: " + metodo + 
                                     "\n💰 MONTO: S/ " + monto + 
                                     "\n🔢 OP: " + operacion).apply();
                
                // 4. Enviar al Servidor
                enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), monto, metodo, operacion);
            }
        } catch (Exception e) {}
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

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
            
            CharSequence titleChars = extras.getCharSequence("android.title");
            CharSequence textChars = extras.getCharSequence("android.text");
            
            String title = titleChars != null ? titleChars.toString() : "";
            String text = textChars != null ? textChars.toString() : "";
            String fullText = (title + " " + text).toLowerCase();

            // 1. Identificar Plataforma
            String metodo = "OTRO";
            if (packageName.contains("yape") || fullText.contains("yape")) metodo = "YAPE";
            else if (packageName.contains("plin") || fullText.contains("plin")) metodo = "PLIN";
            else if (packageName.contains("izipay") || fullText.contains("izipay")) metodo = "IZIPAY";

            if (!metodo.equals("OTRO") || fullText.contains("confirmación") || fullText.contains("pago")) {
                
                String monto = "0.00";
                
                // 2. Extractor Agresivo de Monto (Atrapa puntos, comas y espacios)
                Pattern pMonto = Pattern.compile("([0-9]+[.,][0-9]{1,2})");
                Matcher mMonto = pMonto.matcher(fullText);
                if (mMonto.find()) {
                    monto = mMonto.group(1).replace(",", "."); // Cambia coma por punto si la hubiera
                } else {
                    // Plan B: Si envían número entero sin decimales después de S/
                    Pattern pMontoEntero = Pattern.compile("s/\\s*([0-9]+)");
                    Matcher mMontoEntero = pMontoEntero.matcher(fullText);
                    if (mMontoEntero.find()) {
                        monto = mMontoEntero.group(1) + ".00";
                    }
                }

                // 3. Extractor de Operación (Busca cualquier bloque de números aislado de 6 a 12 dígitos)
                String operacion = "";
                Pattern pOp = Pattern.compile("(?<!\\d)(\\d{6,12})(?!\\d)");
                Matcher mOp = pOp.matcher(fullText);
                if (mOp.find()) {
                    operacion = mOp.group(1);
                } else {
                    // Si la notificación de Yape NO trae número de operación, creamos uno seguro
                    operacion = "AUTO_" + System.currentTimeMillis();
                }

                // IMPRIMIR DIAGNÓSTICO EN PANTALLA
                getSharedPreferences("Debug", MODE_PRIVATE).edit()
                    .putString("log", "✅ DETECTADO: " + metodo + 
                                     "\n💰 MONTO: S/ " + monto + 
                                     "\n🔢 OP: " + operacion +
                                     "\n\n📝 TEXTO REAL QUE LLEGÓ:\n" + title + " | " + text).apply();
                
                // 4. Enviar al Servidor
                String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                enviarSvr(id, monto, metodo, operacion);
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

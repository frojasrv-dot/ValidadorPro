package com.serviem.validador;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.provider.Settings;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class NotificationService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;
            
            String packageName = sbn.getPackageName();
            Bundle extras = sbn.getNotification().extras;
            
            // Extraer Título y Texto de forma segura (CharSequence)
            CharSequence titleChars = extras.getCharSequence("android.title");
            CharSequence textChars = extras.getCharSequence("android.text");
            
            String title = titleChars != null ? titleChars.toString() : "";
            String text = textChars != null ? textChars.toString() : "";
            String contenidoCompleto = title + " " + text;

            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

            String metodo = "";
            String lowerPackage = packageName.toLowerCase();
            String lowerContenido = contenidoCompleto.toLowerCase();

            // Radar de Billeteras
            if (lowerPackage.contains("yape") || lowerContenido.contains("yape")) metodo = "YAPE";
            else if (lowerPackage.contains("plin") || lowerContenido.contains("plin")) metodo = "PLIN";
            else if (lowerPackage.contains("izipay") || lowerContenido.contains("izipay")) metodo = "IZIPAY";

            if (!metodo.equals("")) {
                String monto = "0.00";
                if (contenidoCompleto.contains("S/")) {
                    try {
                        String[] partes = contenidoCompleto.split("S/");
                        if (partes.length > 1) {
                            // Limpia el monto: toma el número después de S/
                            monto = partes[1].trim().split(" ")[0].replaceAll("[^0-9.]", "");
                        }
                    } catch (Exception e) {
                        monto = "ERROR";
                    }
                }

                // --- SISTEMA DE LOG PARA LA PANTALLA ---
                SharedPreferences pref = getSharedPreferences("DebugLog", Context.MODE_PRIVATE);
                pref.edit().putString("ultimo", "Detectado: " + metodo + " | S/ " + monto).apply();
                // ---------------------------------------

                enviarAServidor(deviceId, metodo, monto);
            }
        } catch (Exception e) {
            // Error silencioso para no detener el servicio
        }
    }

    private void enviarAServidor(String id, String met, String mon) {
        new Thread(() -> {
            try {
                URL url = new URL("https://serviem.pythonanywhere.com/api/notificacion");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000); 
                conn.setReadTimeout(15000);

                JSONObject json = new JSONObject();
                json.put("device_id", id);
                json.put("metodo", met);
                json.put("monto", mon);
                json.put("operacion", "AUTO_" + System.currentTimeMillis());

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                    os.flush();
                }

                // Forzar lectura para asegurar que el paquete salió
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    is.read();
                    is.close();
                }
                conn.disconnect();
            } catch (Exception e) {
                // Si falla el internet, el log en pantalla nos avisará que al menos se intentó
            }
        }).start();
    }
}

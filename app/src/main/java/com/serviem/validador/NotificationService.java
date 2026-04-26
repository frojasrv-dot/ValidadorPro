package com.serviem.validador;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.provider.Settings;
import android.os.Bundle;
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
            
            // Reparación 1: Usar CharSequence para evitar el cortocircuito silencioso
            CharSequence titleChars = extras.getCharSequence("android.title");
            CharSequence textChars = extras.getCharSequence("android.text");
            
            String title = titleChars != null ? titleChars.toString() : "";
            String text = textChars != null ? textChars.toString() : "";
            // Unimos título y texto para que el radar no se pierda nada
            String contenidoCompleto = title + " " + text;

            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

            String metodo = "";
            String lowerPackage = packageName.toLowerCase();
            String lowerContenido = contenidoCompleto.toLowerCase();

            // Reparación 2: Detección mejorada
            if (lowerPackage.contains("yape") || lowerContenido.contains("yape")) metodo = "YAPE";
            else if (lowerPackage.contains("plin") || lowerContenido.contains("plin")) metodo = "PLIN";
            else if (lowerPackage.contains("izipay") || lowerContenido.contains("izipay")) metodo = "IZIPAY";

            if (!metodo.equals("")) {
                String monto = "0.00";
                if (contenidoCompleto.contains("S/")) {
                    try {
                        String[] partes = contenidoCompleto.split("S/");
                        if (partes.length > 1) {
                            monto = partes[1].trim().split(" ")[0];
                        }
                    } catch (Exception e) {}
                }
                enviarAServidor(deviceId, metodo, monto);
            }
        } catch (Exception e) {
            // Silenciar para mantener el sensor vivo
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
                // Tiempo de espera para evitar que la app se cuelgue si no hay internet
                conn.setConnectTimeout(10000); 
                conn.setReadTimeout(10000);

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

                // Reparación 3: Forzar lectura de respuesta para confirmar el envío
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (is != null) {
                    is.read(); // Leemos el primer dato del servidor y cerramos
                    is.close();
                }
                conn.disconnect();
            } catch (Exception e) {}
        }).start();
    }
}

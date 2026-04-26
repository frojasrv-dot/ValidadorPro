package com.serviem.validador;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.app.Notification;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class NotificationService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Detecta cambios en las notificaciones a nivel de sistema (Accesibilidad)
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            try {
                // Capturamos la notificación del evento
                Notification notification = (Notification) event.getParcelableData();
                if (notification == null || notification.extras == null) return;

                Bundle extras = notification.extras;
                
                // Extraemos Título y Texto de forma segura
                CharSequence titleChars = extras.getCharSequence("android.title");
                CharSequence textChars = extras.getCharSequence("android.text");
                
                String title = titleChars != null ? titleChars.toString() : "";
                String text = textChars != null ? textChars.toString() : "";
                String contenidoCompleto = (title + " " + text).toLowerCase();

                String metodo = "";
                // Radar de Billeteras
                if (contenidoCompleto.contains("yape")) metodo = "YAPE";
                else if (contenidoCompleto.contains("plin")) metodo = "PLIN";
                else if (contenidoCompleto.contains("izipay")) metodo = "IZIPAY";

                if (!metodo.equals("")) {
                    String monto = "0.00";
                    if (contenidoCompleto.contains("s/")) {
                        try {
                            String[] partes = contenidoCompleto.split("s/");
                            if (partes.length > 1) {
                                // Extrae el número después de S/ y limpia caracteres no numéricos
                                monto = partes[1].trim().split(" ")[0].replaceAll("[^0-9.]", "");
                            }
                        } catch (Exception e) {
                            monto = "0.00";
                        }
                    }

                    // --- ACTUALIZAR LOG EN PANTALLA ---
                    SharedPreferences pref = getSharedPreferences("DebugLog", Context.MODE_PRIVATE);
                    pref.edit().putString("ultimo", "Capturado: " + metodo + " S/ " + monto).apply();
                    // ----------------------------------

                    // Identificador del celular
                    String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                    
                    // Disparamos el envío al servidor
                    enviarAServidor(deviceId, metodo, monto);
                }
            } catch (Exception e) {
                // Silenciamos errores para que el servicio no se detenga
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Obligatorio para AccessibilityService
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
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                JSONObject json = new JSONObject();
                json.put("device_id", id);
                json.put("metodo", met);
                json.put("monto", mon);
                // Generamos un ID de operación temporal basado en el tiempo
                json.put("operacion", "AUTO_" + System.currentTimeMillis());

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                    os.flush();
                }

                // Verificamos respuesta para confirmar que el servidor recibió el dato
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                // Error de red
            }
        }).start();
    }
}

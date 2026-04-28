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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 1. EXTRAER RÁPIDO EN EL HILO PRINCIPAL ANTES DE QUE ANDROID DESTRUYA EL MENSAJE
        try {
            if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;

            String packageName = sbn.getPackageName() != null ? sbn.getPackageName().toLowerCase() : "";

            // Filtro de marcas (Rápido)
            if (!packageName.contains("yape") && !packageName.contains("scotia") && 
                !packageName.contains("interbank") && !packageName.contains("bbva") && 
                !packageName.contains("izipay") && !packageName.contains("bcp")) {
                return;
            }

            Bundle extras = sbn.getNotification().extras;
            StringBuilder volcado = new StringBuilder();
            
            Object titleObj = extras.get("android.title");
            Object textObj = extras.get("android.text");
            String title = titleObj != null ? titleObj.toString() : "";
            String text = textObj != null ? textObj.toString() : "";
            
            volcado.append(title).append(" | ").append(text).append(" | ");
            
            CharSequence[] lines = extras.getCharSequenceArray("android.textLines");
            if (lines != null) {
                for (CharSequence line : lines) volcado.append(line).append(" | ");
            }
            
            String fullText = volcado.toString().toLowerCase();

            // Verificamos si es un pago
            if (fullText.contains("yape") || fullText.contains("plin") || fullText.contains("pago") || fullText.contains("s/")) {
                
                String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                actualizarPantalla("⚡ [" + hora + "] LEYENDO...\nApp: " + packageName);

                // 2. UNA VEZ EXTRAÍDO, LO PASAMOS AL HILO DE RED PARA QUE NO SE CUELGUE LA APP
                String idEquipo = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                enviarSvrSeguro(idEquipo, packageName, title, fullText, hora);
            }
        } catch (Exception e) {
            actualizarPantalla("❌ ERROR DE LECTURA:\n" + e.getMessage());
        }
    }

    // 3. MOTOR DE RED BLINDADO CON VISIBILIDAD DE ERRORES
    private void enviarSvrSeguro(String id, String pkg, String titulo, String crudo, String hora) {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL("https://serviem.pythonanywhere.com/api/notificacion");
                c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                c.setDoOutput(true);
                c.setConnectTimeout(6000); // 6 segundos máximo, si no, aborta
                c.setReadTimeout(6000); 

                JSONObject j = new JSONObject();
                j.put("device_id", id);
                j.put("paquete", pkg);
                j.put("titulo", titulo);
                j.put("texto_crudo", crudo);

                try (OutputStream os = c.getOutputStream()) {
                    byte[] input = j.toString().getBytes("UTF-8");
                    os.write(input, 0, input.length);
                    os.flush();
                }

                int code = c.getResponseCode();
                
                // VALIDACIÓN REAL: Solo canta victoria si el servidor responde OK
                if (code == 200 || code == 201) {
                    actualizarPantalla("✅ [" + hora + "] ÉXITO\nDatos en servidor (HTTP " + code + ")");
                } else {
                    actualizarPantalla("⚠️ [" + hora + "] RECHAZO SERVIDOR\nEl servidor respondió: HTTP " + code);
                }

            } catch (Exception e) {
                // LA VERDAD AL DESCUBIERTO: Si no hay internet o falla, lo imprimirá aquí
                actualizarPantalla("❌ [" + hora + "] ERROR DE RED:\n" + e.toString());
            } finally {
                if (c != null) c.disconnect(); // Liberar memoria obligatoriamente
            }
        }).start();
    }

    private void actualizarPantalla(String mensaje) {
        getSharedPreferences("Debug", MODE_PRIVATE).edit().putString("log", mensaje).apply();
    }
}

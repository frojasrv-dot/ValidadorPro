package com.serviem.validador;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Context;
import android.content.ComponentName;
import android.provider.Settings;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        getSharedPreferences("Debug", MODE_PRIVATE).edit()
            .putString("log", "🟢 SENSOR CONECTADO\nModo Diagnóstico Activado...").apply();
    }

    @Override
    public void onListenerDisconnected() {
        getSharedPreferences("Debug", MODE_PRIVATE).edit()
            .putString("log", "🔴 SENSOR DESCONECTADO").apply();
        try { requestRebind(new ComponentName(this, NotificationListenerService.class)); } catch (Exception e) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        new Thread(() -> {
            try {
                if (sbn == null || sbn.getNotification() == null) return;

                String packageName = sbn.getPackageName() != null ? sbn.getPackageName().toLowerCase() : "desconocido";

                // MODO RADAR AMPLIO: Solo validamos que el nombre de la app tenga estas letras
                if (packageName.contains("yape") || packageName.contains("scotia") || packageName.contains("interbank") || packageName.contains("bbva") || packageName.contains("bcp") || packageName.contains("izipay")) {
                    
                    Bundle extras = sbn.getNotification().extras;
                    if (extras == null) return;

                    // Extracción ultra-segura
                    Object titleObj = extras.get("android.title");
                    Object textObj = extras.get("android.text");
                    
                    String title = titleObj != null ? titleObj.toString() : "Sin título";
                    String text = textObj != null ? textObj.toString() : "Sin texto";
                    String fullText = (title + " " + text).toLowerCase();

                    // 🚨 IMPRESIÓN DIRECTA EN PANTALLA: Nos chismosea todo sin filtros
                    getSharedPreferences("Debug", MODE_PRIVATE).edit()
                        .putString("log", "🚨 ¡CONTACTO DETECTADO!\nApp: " + packageName + 
                                         "\nTítulo: " + title + 
                                         "\nTexto: " + text).apply();

                    // Enviamos al servidor para que el cerebro de Python lo analice
                    enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), packageName, title, fullText);
                }
            } catch (Exception e) {
                // SI HAY UN ERROR INTERNO, LO VERÁS EN PANTALLA
                getSharedPreferences("Debug", MODE_PRIVATE).edit()
                    .putString("log", "❌ ERROR INTERNO:\n" + e.toString()).apply();
            }
        }).start();
    }

    private void enviarSvr(String id, String pkg, String titulo, String crudo) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://serviem.pythonanywhere.com/api/notificacion").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setDoOutput(true);
            c.setConnectTimeout(8000); 
            c.setReadTimeout(8000); 

            JSONObject j = new JSONObject();
            j.put("device_id", id);
            j.put("paquete", pkg);
            j.put("titulo", titulo);
            j.put("texto_crudo", crudo);

            try (OutputStream os = c.getOutputStream()) {
                os.write(j.toString().getBytes("UTF-8"));
                os.flush(); 
            }
            c.getResponseCode();
        } catch (Exception e) {
        } finally {
            if (c != null) { c.disconnect(); }
        }
    }
}

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
import java.util.Arrays;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationService extends NotificationListenerService {

    private static final List<String> APPS_PERMITIDAS = Arrays.asList(
        "com.bcp.innovacxion.yape", "com.bcp.innovacxion.yapeapp",
        "com.scotiabank.bancamovil", "pe.com.interbank.mobilebanking",
        "com.bbva.bbvacontinental", "pe.com.banbif.movil",
        "com.izipay.app", "pe.com.izipay.app", "com.izipay.izipayya"
    );

    // 🟢 SENSOR DE VIDA: Nos avisa si Android conectó el cable
    @Override
    public void onListenerConnected() {
        getSharedPreferences("Debug", MODE_PRIVATE).edit()
            .putString("log", "🟢 SENSOR CONECTADO Y ACTIVO\nEsperando pagos...").apply();
    }

    // 🔴 SENSOR DE MUERTE: Nos avisa si Android nos cortó el cable e intenta auto-repararse
    @Override
    public void onListenerDisconnected() {
        getSharedPreferences("Debug", MODE_PRIVATE).edit()
            .putString("log", "🔴 SENSOR DESCONECTADO POR ANDROID\nIntentando auto-reconectar...").apply();
        try {
            requestRebind(new ComponentName(this, NotificationListenerService.class));
        } catch (Exception e) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        new Thread(() -> {
            try {
                if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;

                String packageName = sbn.getPackageName().toLowerCase();
                boolean esBancoAutorizado = false;
                for (String app : APPS_PERMITIDAS) {
                    if (packageName.contains(app)) { esBancoAutorizado = true; break; }
                }
                if (!esBancoAutorizado) return; 

                Bundle extras = sbn.getNotification().extras;
                StringBuilder volcado = new StringBuilder();
                
                String title = String.valueOf(extras.getCharSequence("android.title"));
                String text = String.valueOf(extras.getCharSequence("android.text"));
                volcado.append(title).append(" | ").append(text).append(" | ");

                CharSequence[] lines = extras.getCharSequenceArray("android.textLines");
                if (lines != null) {
                    for (CharSequence line : lines) volcado.append(line).append(" | ");
                }
                String fullText = volcado.toString().toLowerCase();

                if (fullText.contains("yape") || fullText.contains("plin") || 
                    fullText.contains("pago") || fullText.contains("confirmación") ||
                    fullText.contains("transferencia") || fullText.contains("recibiste") ||
                    fullText.contains("envió") || fullText.contains("s/")) {
                    
                    String horaExacta = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
                    
                    getSharedPreferences("Debug", MODE_PRIVATE).edit()
                        .putString("log", "🕒 ÚLTIMA CAPTURA: " + horaExacta + 
                                         "\n🔒 BANCO: " + packageName + 
                                         "\n📡 ESTADO: Enviado al servidor...").apply();
                    
                    enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), packageName, title, fullText);
                }
            } catch (Exception e) {}
        }).start();
    }

    private void enviarSvr(String id, String pkg, String titulo, String crudo) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://serviem.pythonanywhere.com/api/notificacion").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setDoOutput(true);
            c.setConnectTimeout(8000); // Ajustado a 8 seg para liberar más rápido
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

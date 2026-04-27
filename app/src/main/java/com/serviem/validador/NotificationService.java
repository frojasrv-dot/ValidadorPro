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
import java.util.Arrays;
import java.util.List;

public class NotificationService extends NotificationListenerService {

    // 🛡️ LA LISTA BLANCA: Solo estos "DNI" tienen permiso para ser leídos
    private static final List<String> APPS_PERMITIDAS = Arrays.asList(
        "com.bcp.innovacxion.yape",      // Yape
        "com.scotiabank.bancamovil",     // Scotiabank (Plin)
        "pe.com.interbank.mobilebanking",// Interbank (Plin)
        "com.bbva.bbvacontinental",      // BBVA (Plin)
        "pe.com.banbif.movil",           // BanBif (Plin)
        "com.izipay.app",                // Izipay
        "pe.com.izipay.app",             // Izipay (Variante)
        "com.izipay.izipayya"            // Izipay YA
    );

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        new Thread(() -> {
            try {
                if (sbn == null || sbn.getNotification() == null || sbn.getNotification().extras == null) return;

                String packageName = sbn.getPackageName().toLowerCase();

                // 🛑 FILTRO DE SEGURIDAD MÁXIMA: Si la app NO está en la lista blanca, la ignoramos por completo.
                boolean esBancoAutorizado = false;
                for (String app : APPS_PERMITIDAS) {
                    if (packageName.contains(app)) {
                        esBancoAutorizado = true;
                        break;
                    }
                }

                // Si es WhatsApp, Mensajes, Facebook, etc., el proceso muere aquí mismo. Cero espionaje.
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

                // 🚦 SEGUNDO FILTRO: Validamos que el banco nos esté avisando de un dinero y no sea publicidad
                if (fullText.contains("yape") || fullText.contains("plin") || 
                    fullText.contains("pago") || fullText.contains("confirmación") ||
                    fullText.contains("transferencia") || fullText.contains("recibiste") ||
                    fullText.contains("envió") || fullText.contains("s/")) {
                    
                    getSharedPreferences("Debug", MODE_PRIVATE).edit()
                        .putString("log", "🔒 BANCO AUTORIZADO DETECTADO\nApp: " + packageName + "\n\nEnviando datos seguros al servidor...").apply();
                    
                    enviarSvr(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), packageName, title, fullText);
                }
            } catch (Exception e) {}
        }).start();
    }

    private void enviarSvr(String id, String pkg, String titulo, String crudo) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://serviem.pythonanywhere.com/api/notificacion").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            c.setDoOutput(true);
            c.setConnectTimeout(10000);

            JSONObject j = new JSONObject();
            j.put("device_id", id);
            j.put("paquete", pkg);
            j.put("titulo", titulo);
            j.put("texto_crudo", crudo);

            try (OutputStream os = c.getOutputStream()) {
                os.write(j.toString().getBytes("UTF-8"));
            }
            c.getResponseCode();
            c.disconnect();
        } catch (Exception e) {}
    }
}

package com.serviem.validador;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.provider.Settings;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class NotificationService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        if (sbn.getNotification().extras == null) return;
        
        String text = sbn.getNotification().extras.getString("android.text");
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        String metodo = "";
        if (packageName.contains("com.bcp.yape")) metodo = "YAPE";
        else if (packageName.contains("plin")) metodo = "PLIN";
        else if (packageName.contains("izipay")) metodo = "IZIPAY";

        if (!metodo.equals("") && text != null) {
            String monto = "0.00";
            try {
                if (text.contains("S/")) {
                    monto = text.split("S/")[1].trim().split(" ")[0];
                }
            } catch (Exception e) {}
            enviarAServidor(deviceId, metodo, monto);
        }
    }

    private void enviarAServidor(String id, String met, String mon) {
        new Thread(() -> {
            try {
                URL url = new URL("https://serviem.pythonanywhere.com/api/notificacion");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("device_id", id);
                json.put("metodo", met);
                json.put("monto", mon);
                json.put("operacion", "AUTO_" + System.currentTimeMillis());

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.flush();
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {}
        }).start();
    }
}

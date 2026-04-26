package com.serviem.validador;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.provider.Settings;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        tv.setText("SERVICIO VALIDADOR ACTIVO\nTU ID ES: " + androidId);
        tv.setTextSize(20);
        tv.setPadding(50, 50, 50, 50);
        setContentView(tv);
    }
}

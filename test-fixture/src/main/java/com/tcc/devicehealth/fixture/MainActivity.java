package com.tcc.devicehealth.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView content = new TextView(this);
        content.setText("Disposable test application");
        content.setPadding(32, 32, 32, 32);
        setContentView(content);
    }
}

package com.achellies.android.ultron.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TestModule module = new TestModule(1, 1, 1);
        long value = module.calculate();

        Toast.makeText(this, "Hello Hotfix, value = " + Long.toString(value), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
//        super.onResume();
    }
}

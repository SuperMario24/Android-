package com.example.saber.handlertest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ThreadLocal<Boolean> mBooleanThreadLocal = new ThreadLocal<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBooleanThreadLocal.set(true);
        Log.d(TAG, "[Thread#main]mBooleanThreadLocal: "+mBooleanThreadLocal.get());

        new Thread(new Runnable() {
            @Override
            public void run() {
                mBooleanThreadLocal.set(false);
                Log.d(TAG, "[Thread#1]mBooleanThreadLocal: "+mBooleanThreadLocal.get());
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "[Thread#2]mBooleanThreadLocal: "+mBooleanThreadLocal.get());
            }
        }).start();

    }
}

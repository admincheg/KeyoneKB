package com.sateda.keyonekb2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static com.sateda.keyonekb2.SettingsActivity.*;

public class MainActivity extends Activity {

    private Button btn_power_manager;

    private Button btn_sys_phone_permission;

    private KbSettings kbSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        kbSettings = KbSettings.Get(getSharedPreferences(KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        Button btn_settings = (Button) findViewById(R.id.btn_settings);
        Button btn_test_key = (Button) findViewById(R.id.btn_test_key);
        btn_power_manager = (Button) findViewById(R.id.btn_power_manager);
        Button btn_sys_kb_setting = (Button) findViewById(R.id.btn_sys_kb_setting);
        Button btn_sys_kb_accessibility_setting = (Button) findViewById(R.id.btn_sys_kb_accessibility_setting);
        btn_sys_phone_permission = (Button) findViewById(R.id.btn_sys_phone_permission);
        TextView tv_version = (TextView) findViewById(R.id.tv_version);

        String text = String.format("\n\nApp: %s\nVersion: %s\nBuild type: %s", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE);
        tv_version.setText(text);

        btn_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(switchActivityIntent);
            }
        });

        btn_test_key.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent switchActivityIntent = new Intent(MainActivity.this, KeyboardTestActivity.class);
                startActivity(switchActivityIntent);
            }
        });

        btn_sys_kb_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });

        btn_sys_kb_accessibility_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });

        btn_power_manager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckPowerState(MainActivity.this, true);
            }
        });

        btn_sys_phone_permission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckPermissionState(true);
            }
        });

        CheckPermissionState(false);
        CheckPowerState(this, false);
    }

    private static void CheckPowerState(MainActivity mainActivity, boolean andRequest) {
        Intent intent = new Intent();
        String packageName = mainActivity.getApplicationContext().getPackageName();
        PowerManager pm = (PowerManager) mainActivity.getApplicationContext().getSystemService(Context.POWER_SERVICE);

        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            if(andRequest) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                mainActivity.getApplicationContext().startActivity(intent);
                mainActivity.btn_power_manager.setText(R.string.btn_power_manager_deactivated);
                mainActivity.btn_power_manager.setEnabled(false);
            } else {
                mainActivity.btn_power_manager.setText(R.string.btn_power_manager_activated);
                mainActivity.btn_power_manager.setEnabled(true);
            }
        } else {
            mainActivity.btn_power_manager.setText(R.string.btn_power_manager_deactivated);
            mainActivity.btn_power_manager.setEnabled(false);
        }
    }

    private void CheckPermissionState(boolean andRequest) {
        if(kbSettings.GetBooleanValue(kbSettings.APP_PREFERENCES_6_MANAGE_CALL)) {

                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    if(!andRequest) {
                        ButtonPermissionActivate(this);
                    }
                    else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ANSWER_PHONE_CALLS}, REQUEST_PERMISSION_CODE);
                        ButtonPermissionDeactivate(this);
                    }
                } else {
                    ButtonPermissionDeactivate(this);
                }
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    if(!andRequest) {
                        ButtonPermissionActivate(this);
                    }
                    else {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_PERMISSION_CODE);
                        ButtonPermissionDeactivate(this);
                    }
                } else {
                    ButtonPermissionDeactivate(this);
                }

            } else {
            ButtonPermissionDeactivate(this);
        }

        }

    private static void ButtonPermissionActivate(MainActivity mainActivity) {
        mainActivity.btn_sys_phone_permission.setText(R.string.btn_sys_phone_permission_activated);
        mainActivity.btn_sys_phone_permission.setEnabled(true);
    }

    private static void ButtonPermissionDeactivate(MainActivity mainActivity) {
        mainActivity.btn_sys_phone_permission.setEnabled(false);
        mainActivity.btn_sys_phone_permission.setText(R.string.btn_sys_phone_permission_deactivated);
    }


}
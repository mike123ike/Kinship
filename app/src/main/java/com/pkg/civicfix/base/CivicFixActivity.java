package com.pkg.civicfix.base;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public abstract class CivicFixActivity extends AppCompatActivity {
    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void goToActivity(Class<? extends Activity> newActivity, boolean finishCurrent) {
        Intent intent = new Intent(this, newActivity);
        startActivity(intent);
        if (finishCurrent) finish();
    }
}

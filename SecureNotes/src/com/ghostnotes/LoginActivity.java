package com.ghostnotes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * LoginActivity - First screen shown when app opens.
 *
 * Two modes:
 * 1. SETUP: First launch - user creates master password
 * 2. UNLOCK: Subsequent launches - user enters password to decrypt
 *
 * Security features:
 * - FLAG_SECURE prevents screenshots and screen recording
 * - FLAG_SECURE prevents recent apps thumbnail
 * - No network operations
 * - Password never stored in plaintext
 */
public class LoginActivity extends Activity {

    private EditText etPassword;
    private EditText etConfirmPassword;
    private TextView tvError;
    private TextView tvSubtitle;
    private Button btnUnlock;
    private CryptoManager crypto;
    private boolean isSetupMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Prevent screenshots, screen recording, and recent apps preview
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        setContentView(R.layout.activity_login);
        crypto = new CryptoManager(this);

        etPassword = (EditText) findViewById(R.id.etPassword);
        etConfirmPassword = (EditText) findViewById(R.id.etConfirmPassword);
        tvError = (TextView) findViewById(R.id.tvError);
        tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
        btnUnlock = (Button) findViewById(R.id.btnUnlock);

        // Check if this is first run or unlock
        isSetupMode = !crypto.isSetUp();
        configureUiMode();

        btnUnlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSetupMode) {
                    attemptSetup();
                } else {
                    attemptUnlock();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If session is already active (came back from notes), go straight in
        if (!isSetupMode && crypto.isSessionActive()) {
            openMainActivity();
        }
    }

    private void configureUiMode() {
        if (isSetupMode) {
            tvSubtitle.setText(R.string.setup_subtitle);
            btnUnlock.setText(R.string.set_password);
            etConfirmPassword.setVisibility(View.VISIBLE);
        } else {
            tvSubtitle.setText(R.string.enter_to_unlock);
            btnUnlock.setText(R.string.unlock);
            etConfirmPassword.setVisibility(View.GONE);
        }
    }

    private void attemptSetup() {
        String password = etPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();

        if (password.length() < 8) {
            showError(getString(R.string.password_too_short));
            return;
        }

        if (!password.equals(confirm)) {
            showError(getString(R.string.passwords_dont_match));
            etConfirmPassword.requestFocus();
            return;
        }

        if (crypto.setupPassword(password)) {
            clearPasswordFields();
            openMainActivity();
        } else {
            showError(getString(R.string.encryption_error));
        }
    }

    private void attemptUnlock() {
        String password = etPassword.getText().toString();

        if (password.isEmpty()) return;

        // Disable button during key derivation (PBKDF2 is intentionally slow)
        btnUnlock.setEnabled(false);
        btnUnlock.setText("Unlocking...");
        tvError.setVisibility(View.GONE);

        final String pw = password;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = crypto.unlockWithPassword(pw);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnUnlock.setEnabled(true);
                        btnUnlock.setText(R.string.unlock);
                        if (success) {
                            clearPasswordFields();
                            openMainActivity();
                        } else {
                            showError(getString(R.string.wrong_password));
                            etPassword.selectAll();
                            etPassword.requestFocus();
                        }
                    }
                });
            }
        }).start();
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        // Don't finish - keep in back stack so back button locks app
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void clearPasswordFields() {
        // Securely clear password from EditText memory
        etPassword.setText("");
        etConfirmPassword.setText("");
    }

    @Override
    public void onBackPressed() {
        // Lock the app and exit when back is pressed on login screen
        crypto.lockSession();
        finish();
    }
}

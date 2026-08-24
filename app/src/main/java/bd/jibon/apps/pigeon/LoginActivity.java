package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity implements PigeonService.PigeonCallback {

    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private Button btnLogin;

    private PigeonService pigeonService;
    private boolean isBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PigeonService.PigeonBinder binder = (PigeonService.PigeonBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(LoginActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            pigeonService = null;
        }
    };
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        prefs = getSharedPreferences("PigeonPrefs", Context.MODE_PRIVATE);

        btnLogin.setOnClickListener(v -> attemptLogin());

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            if (pigeonService != null) {
                pigeonService.unregisterCallback(this);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }

    private void attemptLogin() {
        if (etUsername.getText() == null || etPassword.getText() == null) {
            return;
        }
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty()) {
            etUsername.setError("Username required");
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Password required");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("event", "login");
            JSONObject data = new JSONObject();
            data.put("username", username);
            data.put("password", password);
            payload.put("data", data);

            if (isBound && pigeonService != null && pigeonService.isConnected()) {
                pigeonService.sendMessage(payload.toString());
            } else {
                Toast.makeText(this, "Not connected to PIGEON node", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "JSON packing error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
        runOnUiThread(() -> {
            if (!connected) {
                Toast.makeText(LoginActivity.this, "Lost connection to node", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, ConnectionActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    public void onMessageReceived(String json) {
        runOnUiThread(() -> {
            try {
                JSONObject root = new JSONObject(json);
                String event = root.optString("event", "");
                if ("login_response".equals(event)) {
                    JSONObject data = root.optJSONObject("data");
                    if (data != null && data.optBoolean("success", false)) {
                        String token = data.optString("token", "");
                        String username = data.optString("username", "");
                        prefs.edit()
                                .putString("login_token", token)
                                .putString("username", username)
                                .apply();

                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        String errMsg = "Authentication Failed";
                        if (data != null && data.has("message")) {
                            errMsg = data.optString("message");
                        }
                        Toast.makeText(LoginActivity.this, errMsg, Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (JSONException e) {
                Toast.makeText(LoginActivity.this, "Invalid response received from node", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

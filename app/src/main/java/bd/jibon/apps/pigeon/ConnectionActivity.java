package bd.jibon.apps.pigeon;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONException;
import org.json.JSONObject;

public class ConnectionActivity extends AppCompatActivity implements PigeonService.PigeonCallback {

    private static final int PERMISSION_REQUEST_CODE = 200;

    private TextInputEditText etDeviceId;
    private Button btnConnectWifi;
    private TextView tvStatus;

    private PigeonService pigeonService;
    private boolean isBound = false;
    private SharedPreferences prefs;
    private AlertDialog loadingDialog;
    private TextView tvLoadingMessage;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PigeonService.PigeonBinder binder = (PigeonService.PigeonBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(ConnectionActivity.this);

            // Populates field but waits for the user to press CONNECT
            String savedDevice = prefs.getString("saved_device_name", "");
            if (!savedDevice.isEmpty()) {
                etDeviceId.setText(savedDevice);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            pigeonService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        etDeviceId = findViewById(R.id.etDeviceId);
        btnConnectWifi = findViewById(R.id.btnConnectWifi);
        tvStatus = findViewById(R.id.tvStatus);

        prefs = getSharedPreferences("PigeonPrefs", Context.MODE_PRIVATE);

        btnConnectWifi.setOnClickListener(v -> checkPermissionsAndConnect());

        Intent intent = new Intent(this, PigeonService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void showLoadingDialog(String message) {
        runOnUiThread(() -> {
            if (loadingDialog == null) {
                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setPadding(60, 60, 60, 60);
                layout.setGravity(Gravity.CENTER_VERTICAL);

                ProgressBar progressBar = new ProgressBar(this);
                layout.addView(progressBar);

                tvLoadingMessage = new TextView(this);
                tvLoadingMessage.setText(message);
                tvLoadingMessage.setTextSize(16);
                tvLoadingMessage.setPadding(50, 0, 0, 0);
                layout.addView(tvLoadingMessage);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setCancelable(true);
                builder.setOnCancelListener(dialog -> {
                    if (pigeonService != null) {
                        pigeonService.disconnect();
                    }
                });
                builder.setView(layout);
                loadingDialog = builder.create();
            } else if (tvLoadingMessage != null) {
                tvLoadingMessage.setText(message);
            }
            if (!loadingDialog.isShowing()) {
                loadingDialog.show();
            }
        });
    }

    private void hideLoadingDialog() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    @Override
    protected void onDestroy() {
        hideLoadingDialog();
        if (isBound) {
            if (pigeonService != null) {
                pigeonService.unregisterCallback(this);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.NEARBY_WIFI_DEVICES,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_CODE);
                return;
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        startNodeConnection();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                startNodeConnection();
            } else {
                Toast.makeText(this, "Permissions are required to connect to the node WiFi AP", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startNodeConnection() {
        if (etDeviceId.getText() == null) {
            return;
        }
        String deviceId = etDeviceId.getText().toString().trim().toUpperCase();
        if (deviceId.isEmpty()) {
            etDeviceId.setError("Device Name required");
            return;
        }
        if (isBound && pigeonService != null) {
            showLoadingDialog("Connecting to PIGEON Mesh...");
            pigeonService.connectToNode(deviceId);
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
        runOnUiThread(() -> {
            tvStatus.setText(message);
            if (connected) {
                hideLoadingDialog();
                String nodeName = pigeonService.getConnectedNodeName();
                prefs.edit().putString("saved_device_name", nodeName).apply();
                checkTokenAndNavigate();
            } else {
                String msgLower = message.toLowerCase();
                if (msgLower.contains("fail") || msgLower.contains("timeout") || msgLower.contains("lost") || msgLower.contains("disconnected manually") || msgLower.equals("disconnected")) {
                    hideLoadingDialog();
                } else if (msgLower.contains("connecting") || msgLower.contains("locating") || msgLower.contains("bound") || msgLower.contains("initializing") || msgLower.contains("reconnecting")) {
                    showLoadingDialog(message);
                }
            }
        });
    }

    @Override
    public void onMessageReceived(String json) {
        runOnUiThread(() -> {
            try {
                JSONObject root = new JSONObject(json);
                String event = root.optString("event", "");
                if ("token_validated".equals(event)) {
                    JSONObject data = root.optJSONObject("data");
                    if (data != null && data.optBoolean("success", false)) {
                        Intent intent = new Intent(ConnectionActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        navigateToLogin();
                    }
                }
            } catch (JSONException e) {
                navigateToLogin();
            }
        });
    }

    private void checkTokenAndNavigate() {
        String token = prefs.getString("login_token", "");
        if (token.isEmpty()) {
            navigateToLogin();
        } else {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "token_validate");
                JSONObject data = new JSONObject();
                data.put("token", token);
                payload.put("data", data);
                if (isBound && pigeonService != null) {
                    pigeonService.sendMessage(payload.toString());
                } else {
                    navigateToLogin();
                }
            } catch (JSONException e) {
                navigateToLogin();
            }
        }
    }

    private void navigateToLogin() {
        Intent intent = new Intent(ConnectionActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
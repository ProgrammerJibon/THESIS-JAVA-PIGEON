package bd.jibon.apps.pigeon;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class TestConnectWifiSendReceiveWebSocket extends AppCompatActivity {

    private static final String TAG = "PigeonMeshApp";

    private TextView tvStatus, tvReceivedData;
    private Button btnConnectWifi, btnSendTest;

    private WebSocket webSocket;
    private OkHttpClient client;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_connect_wifi_send_receive_web_socket);

        tvStatus = findViewById(R.id.tvStatus);
        tvReceivedData = findViewById(R.id.tvReceivedData);
        btnConnectWifi = findViewById(R.id.btnConnectWifi);
        btnSendTest = findViewById(R.id.btnSendTest);

        btnSendTest.setEnabled(false);

        client = new OkHttpClient.Builder().build();
        connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        btnConnectWifi.setOnClickListener(v -> checkPermissionsAndConnect());
        btnSendTest.setOnClickListener(v -> sendTestJson());
    }

    private void checkPermissionsAndConnect() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.NEARBY_WIFI_DEVICES},
                        2
                );
                return;
            }
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1
            );
            return;
        }

        connectToEspWifi();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == 1
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            connectToEspWifi();

        } else {
            Toast.makeText(
                    this,
                    "Location permission required",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void connectToEspWifi() {
        updateStatus("Connecting to WiFi P1...");

        WifiNetworkSpecifier specifier =
                null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            specifier = new WifiNetworkSpecifier.Builder()
                    .setSsid("P1")
                    .setWpa2Passphrase("P1-12345")
                    .build();
        }

        NetworkRequest request =
                null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request = new NetworkRequest.Builder()
                    .addTransportType(
                            NetworkCapabilities.TRANSPORT_WIFI
                    )
                    .removeCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                    .setNetworkSpecifier(specifier)
                    .build();
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);

                connectivityManager.bindProcessToNetwork(network);

                String gatewayIp = extractGatewayIp(network);

                runOnUiThread(() -> {
                    if (gatewayIp != null) {
                        updateStatus(
                                "WiFi Connected. ESP32: " + gatewayIp
                        );

                        connectWebSocket(gatewayIp);

                    } else {
                        updateStatus("ESP32 gateway IP not found");
                    }
                });
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();

                runOnUiThread(() ->
                        updateStatus("Unable to connect to WiFi P1")
                );
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);

                runOnUiThread(() -> {
                    updateStatus("WiFi Disconnected");
                    btnSendTest.setEnabled(false);

                    if (webSocket != null) {
                        webSocket.close(
                                1000,
                                "WiFi disconnected"
                        );
                        webSocket = null;
                    }
                });
            }
        };

        connectivityManager.requestNetwork(
                request,
                networkCallback
        );
    }

    private String extractGatewayIp(Network network) {
        LinkProperties linkProperties =
                connectivityManager.getLinkProperties(network);

        if (linkProperties != null) {
            for (RouteInfo route : linkProperties.getRoutes()) {
                if (route.isDefaultRoute()
                        && route.getGateway() != null) {

                    return route.getGateway().getHostAddress();
                }
            }
        }

        return null;
    }

    private void connectWebSocket(String gatewayIp) {
        if (webSocket != null) {
            webSocket.close(1000, "Reconnecting");
            webSocket = null;
        }

        String wsUrl = "ws://" + gatewayIp + ":81/";

        updateStatus("Connecting WebSocket...");

        Request request =
                new Request.Builder()
                        .url(wsUrl)
                        .build();

        webSocket = client.newWebSocket(
                request,
                new WebSocketListener() {

                    @Override
                    public void onOpen(
                            @NonNull WebSocket webSocket,
                            @NonNull Response response) {

                        runOnUiThread(() -> {
                            updateStatus(
                                    "WebSocket Connected"
                            );

                            btnSendTest.setEnabled(true);
                        });
                    }

                    @Override
                    public void onMessage(
                            @NonNull WebSocket webSocket,
                            @NonNull String text) {

                        runOnUiThread(() ->
                                handleReceivedMessage(text)
                        );
                    }

                    @Override
                    public void onClosing(
                            @NonNull WebSocket webSocket,
                            int code,
                            @NonNull String reason) {

                        webSocket.close(code, reason);

                        runOnUiThread(() -> {
                            updateStatus(
                                    "WebSocket Closing"
                            );

                            btnSendTest.setEnabled(false);
                        });
                    }

                    @Override
                    public void onClosed(
                            @NonNull WebSocket webSocket,
                            int code,
                            @NonNull String reason) {

                        runOnUiThread(() -> {
                            updateStatus(
                                    "WebSocket Closed"
                            );

                            btnSendTest.setEnabled(false);
                        });
                    }

                    @Override
                    public void onFailure(
                            @NonNull WebSocket webSocket,
                            @NonNull Throwable t,
                            Response response) {

                        Log.e(
                                TAG,
                                "WebSocket Error",
                                t
                        );

                        runOnUiThread(() -> {
                            updateStatus(
                                    "WebSocket Error: "
                                            + t.getMessage()
                            );

                            btnSendTest.setEnabled(false);
                        });
                    }
                }
        );
    }

    private void handleReceivedMessage(String jsonString) {
        try {
            JSONObject jsonObject =
                    new JSONObject(jsonString);

            String event =
                    jsonObject.optString("event");

            if ("info".equals(event)) {

                JSONObject data =
                        jsonObject.getJSONObject("data");

                int id =
                        data.getInt("id");

                String name =
                        data.getString("name");

                String formatted =
                        "--- INFO EVENT ---\n"
                                + "ID: " + id + "\n"
                                + "Name: " + name + "\n\n";

                appendDataToUI(formatted);

            } else if ("test_response".equals(event)) {

                JSONObject data =
                        jsonObject.getJSONObject("data");

                boolean success =
                        data.optBoolean("success");

                int userId =
                        data.optInt("userId");

                String msg =
                        data.optString("msg");

                String formatted =
                        "--- TEST RESPONSE ---\n"
                                + "Success: " + success + "\n"
                                + "UserID: " + userId + "\n"
                                + "Message: " + msg + "\n\n";

                appendDataToUI(formatted);

            } else {

                appendDataToUI(
                        "Received: "
                                + jsonString
                                + "\n"
                );
            }

        } catch (JSONException e) {

            appendDataToUI(
                    "Invalid JSON: "
                            + jsonString
                            + "\n"
            );
        }
    }

    private void sendTestJson() {
        if (webSocket == null) {
            return;
        }

        try {
            JSONObject dataObj =
                    new JSONObject();

            dataObj.put(
                    "userId",
                    44
            );

            dataObj.put(
                    "msg",
                    "This is a test data"
            );

            JSONObject jsonObject =
                    new JSONObject();

            jsonObject.put(
                    "event",
                    "test"
            );

            jsonObject.put(
                    "data",
                    dataObj
            );

            String payload =
                    jsonObject.toString();

            boolean sent =
                    webSocket.send(payload);

            if (sent) {

                appendDataToUI(
                        "-> Sent: "
                                + payload
                                + "\n"
                );

            } else {

                appendDataToUI(
                        "-> Send failed\n"
                );
            }

        } catch (JSONException e) {

            Log.e(
                    TAG,
                    "JSON Build Error",
                    e
            );
        }
    }

    private void updateStatus(String status) {
        tvStatus.setText(
                "Status: " + status
        );
    }

    private void appendDataToUI(String text) {
        tvReceivedData.append(text);
    }

    @Override
    protected void onDestroy() {
        if (webSocket != null) {
            webSocket.close(
                    1000,
                    "App closed"
            );
            webSocket = null;
        }

        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
            } catch (Exception ignored) {
            }

            networkCallback = null;
        }

        connectivityManager.bindProcessToNetwork(null);

        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }

        super.onDestroy();
    }
}
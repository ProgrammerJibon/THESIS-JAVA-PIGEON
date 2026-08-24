package bd.jibon.apps.pigeon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class PigeonService extends Service {

    private static final String TAG = "PigeonService";
    private static final String CHANNEL_ID = "PigeonServiceChannel";
    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new PigeonBinder();
    private final List<PigeonCallback> callbacks = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private OkHttpClient client;
    private WebSocket webSocket;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private String connectedNodeName = "";
    private String gatewayIp = "192.168.4.1";
    private boolean isConnecting = false;
    private boolean isConnected = false;
    private boolean isManuallyClosed = false;

    public static String generatePassword(String deviceName) {
        String base64 = Base64.encodeToString(deviceName.getBytes(), Base64.NO_WRAP);
        if (base64.length() > 8) {
            base64 = base64.substring(0, 8);
        }
        StringBuilder builder = new java.lang.StringBuilder(base64);
        while (builder.length() < 8) {
            builder.append("7");
        }
        return builder.toString();
    }

    private void establishWebSocketConnection() {
        String wsUrl = "ws://" + gatewayIp + ":81/";
        Request request = new Request.Builder().url(wsUrl).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                handler.post(() -> {
                    isConnecting = false;
                    isConnected = true;
                    updateNotification("Connected", "Linked to " + connectedNodeName + " mesh gateway");
                    notifyStateChange(true, "Connected to " + connectedNodeName);
                });
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull final String text) {
                handler.post(() -> {
                    notifyMessageReceived(text);
                });
            }

            @Override
            public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
                ws.close(code, reason);
            }

            @Override
            public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                handler.post(() -> {
                    isConnected = false;
                    if (!isManuallyClosed) {
                        attemptReconnection();
                    }
                });
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull final Throwable t, Response response) {
                handler.post(() -> {
                    isConnected = false;
                    if (!isManuallyClosed) {
                        attemptReconnection();
                    } else {
                        handleConnectionFailure(t.getMessage());
                    }
                });
            }
        });
    }

    public interface PigeonCallback {
        void onConnectionStateChanged(boolean connected, String message);

        void onMessageReceived(String json);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Disconnected", "Not connected to any node"));
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        initOkHttpClient();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pigeon Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String title, String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PIGEON: " + title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String title, String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(title, content));
        }
    }

    private void initOkHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            client = new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "SSL Init Error", e);
        }
    }

    public class LocalBinder extends Binder {
        public PigeonService getService() {
            return PigeonService.this;
        }
    }

    public void connectToNode(final String nodeName) {
        if (isConnecting || isConnected) {
            return;
        }
        isConnecting = true;
        isManuallyClosed = false;
        connectedNodeName = nodeName;
        updateNotification("Connecting", "Locating PIGEON Node " + nodeName);

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(nodeName)
                .setWpa2Passphrase(generatePassword(nodeName))
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();

        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                connectivityManager.bindProcessToNetwork(network);
                gatewayIp = extractGatewayIp(network);
                handler.post(() -> establishWebSocketConnection());
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                handler.post(() -> handleConnectionFailure("WiFi connection timed out"));
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                handler.post(() -> {
                    if (!isManuallyClosed) {
                        handleConnectionFailure("WiFi connection lost");
                    }
                });
            }
        };

        connectivityManager.requestNetwork(request, networkCallback);
    }

    private String extractGatewayIp(Network network) {
        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties != null) {
            for (RouteInfo route : linkProperties.getRoutes()) {
                if (route.isDefaultRoute() && route.getGateway() != null) {
                    String ip = route.getGateway().getHostAddress();
                    if (ip != null && !ip.equals("0.0.0.0")) {
                        return ip;
                    }
                }
            }
        }
        return "192.168.4.1";
    }

    public class PigeonBinder extends LocalBinder {
        @Override
        public PigeonService getService() {
            return PigeonService.this;
        }
    }

    private void attemptReconnection() {
        if (isManuallyClosed) {
            return;
        }
        isConnecting = true;
        updateNotification("Reconnecting", "Attempting automatic link recovery...");
        notifyStateChange(false, "Reconnecting...");
        handler.postDelayed(() -> {
            if (isConnecting && !isConnected && !isManuallyClosed) {
                establishWebSocketConnection();
            }
        }, 5000);
    }

    private void handleConnectionFailure(String error) {
        isConnecting = false;
        isConnected = false;
        updateNotification("Disconnected", "Uplink offline: " + error);
        notifyStateChange(false, error);
    }

    public void sendMessage(String json) {
        if (webSocket != null && isConnected) {
            webSocket.send(json);
        }
    }

    public void sendWssMessage(String json) {
        sendMessage(json);
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getConnectedNodeName() {
        return connectedNodeName;
    }

    public String getGatewayIp() {
        return gatewayIp;
    }

    public void disconnect() {
        isManuallyClosed = true;
        isConnecting = false;
        isConnected = false;
        if (webSocket != null) {
            webSocket.close(1000, "User logout/shutdown");
            webSocket = null;
        }
        if (networkCallback != null) {
            try {
                connectivityManager.bindProcessToNetwork(null);
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            networkCallback = null;
        }
        updateNotification("Disconnected", "Session terminated");
        notifyStateChange(false, "Disconnected manually");
    }

    public void registerCallback(PigeonCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
        callback.onConnectionStateChanged(isConnected, isConnected ? "Connected" : "Disconnected");
    }

    public void unregisterCallback(PigeonCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyStateChange(boolean connected, String message) {
        for (PigeonCallback cb : callbacks) {
            cb.onConnectionStateChanged(connected, message);
        }
    }

    private void notifyMessageReceived(String json) {
        for (PigeonCallback cb : callbacks) {
            cb.onMessageReceived(json);
        }
    }
}

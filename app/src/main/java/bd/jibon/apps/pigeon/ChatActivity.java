package bd.jibon.apps.pigeon;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private String peerUsername;
    private String myUsername;
    private PigeonService pigeonService;
    private boolean isBound = false;
    private boolean isBlockedByMe = false;
    private boolean isBlockedByPeer = false;

    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private List<Message> messageList;
    private EditText etInput;
    private Button btnSend;
    private ImageButton btnToggleActions;
    private LinearLayout layoutAttachments;
    private ImageButton btnAttachImage;
    private ImageButton btnAttachLocation;
    private PigeonDatabaseHelper dbHelper;
    private AlertDialog reconnectDialog;

    private AlertDialog transferDialog;
    private ProgressBar transferProgress;
    private List<String> appChunks;

    private boolean isSendingImage = false;
    private int currentAppChunk = -1;
    private int expectedIncomingTotal = 0;
    private String currentTransferType = "";
    private Handler transferHandler = new Handler();
    private StringBuilder incomingImageBuffer;
    private int expectedIncomingChunk = 0;
    private final Runnable transferTimeoutRunnable = new Runnable() {
        public void run() {
            if (transferDialog != null && transferDialog.isShowing()) {
                if (isSendingImage) {
                    if (currentAppChunk == -1) {
                        sendAppChunkControl("img_start", appChunks.size(), currentTransferType, 0);
                    } else if (currentAppChunk < appChunks.size()) {
                        sendAppChunkData(appChunks.get(currentAppChunk), currentAppChunk, appChunks.size());
                    }
                } else {
                    sendAppChunkControl("img_ack", expectedIncomingTotal, "", expectedIncomingChunk);
                }
                transferHandler.postDelayed(this, 5000);
            }
        }
    };

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    processAndSendImage(imageUri);
                }
            }
    );

    private AlertDialog forwardDialog;
    private ForwardTargetAdapter forwardAdapter;
    private List<ForwardTarget> forwardTargets = new ArrayList<>();
    private String incomingImageType = "";
    private final PigeonService.PigeonCallback callback = new PigeonService.PigeonCallback() {
        @Override
        public void onConnectionStateChanged(boolean connected, String message) {
            runOnUiThread(() -> {
                if (!connected && !isFinishing()) {
                    if (reconnectDialog == null) {
                        reconnectDialog = new AlertDialog.Builder(ChatActivity.this)
                                .setTitle("Connection Lost")
                                .setMessage("Disconnected from PIGEON Node. Reconnecting...")
                                .setCancelable(false)
                                .setPositiveButton("Exit", (dialog, which) -> finish())
                                .create();
                    }
                    if (!reconnectDialog.isShowing()) reconnectDialog.show();
                } else {
                    if (reconnectDialog != null && reconnectDialog.isShowing())
                        reconnectDialog.dismiss();
                }
            });
        }

        @Override
        public void onMessageReceived(String payload) {
            runOnUiThread(() -> {
                try {
                    JSONObject root = new JSONObject(payload);
                    String event = root.optString("event", "");

                    if ("message".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        String sender = data.getString("sender");
                        String type = data.optString("type", "text");

                        if (type.startsWith("img_")) {
                            if (peerUsername.equals(sender)) handleImageChunkProtocol(data);
                            return;
                        }

                        if (peerUsername.equals(sender)) {
                            String text = data.optString("text", "");
                            String timestamp = data.getString("timestamp");
                            Message m;
                            if ("image".equals(type)) {
                                m = new Message(sender, text, timestamp, false, Message.TYPE_IMAGE);
                            } else if ("location".equals(type)) {
                                m = new Message(sender, text, timestamp, false, Message.TYPE_LOCATION);
                            } else {
                                m = new Message(sender, text, timestamp, false);
                            }
                            messageList.add(m);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            rvMessages.scrollToPosition(messageList.size() - 1);
                        }
                    } else if ("msg_delivered".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        if (peerUsername.equals(data.getString("receiver"))) {
                            dbHelper.markAllMessagesDelivered(peerUsername);
                            refreshLocalMessages();
                        }
                    } else if ("connections_list".equals(event)) {
                        JSONArray array = root.getJSONArray("data");
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            String username = obj.getString("username");
                            if (peerUsername.equals(username)) {
                                boolean active = obj.optBoolean("active", false);
                                if (!isBlockedByMe && !isBlockedByPeer) {
                                    btnSend.setVisibility(active ? View.VISIBLE : View.GONE);
                                    btnToggleActions.setVisibility(active ? View.VISIBLE : View.GONE);
                                    etInput.setEnabled(active);
                                    etInput.setHint(active ? getString(R.string.hint_message) : "User is currently offline");
                                }
                            }
                            forwardTargets.add(new ForwardTarget(username, username, false));
                        }
                        if (forwardAdapter != null) forwardAdapter.updateData(forwardTargets);
                    } else if ("groups_list".equals(event)) {
                        JSONArray array = root.getJSONArray("data");
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            forwardTargets.add(new ForwardTarget(obj.getString("name"), obj.getString("id"), true));
                        }
                        if (forwardAdapter != null) forwardAdapter.updateData(forwardTargets);
                    } else if ("delete_message_both".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        if (peerUsername.equals(data.getString("sender"))) {
                            dbHelper.deleteMessage(peerUsername, data.getString("timestamp"), data.getString("text"));
                            refreshLocalMessages();
                        }
                    } else if ("delete_chat".equals(event)) {
                        if (peerUsername.equals(root.getJSONObject("data").getString("peer"))) {
                            dbHelper.clearHistory(peerUsername);
                            refreshLocalMessages();
                            Toast.makeText(ChatActivity.this, "Peer deleted chat history.", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }
    };

    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if ((fine != null && fine) || (coarse != null && coarse)) {
                    fetchAndSendLocation();
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            PigeonService.LocalBinder binder = (PigeonService.LocalBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(callback);
            requestConnectionsList();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };
    private Message currentForwardingMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        peerUsername = getIntent().getStringExtra("username");
        if (peerUsername == null) peerUsername = "Peer Node";
        isBlockedByMe = getIntent().getBooleanExtra("isBlockedByMe", false);
        isBlockedByPeer = getIntent().getBooleanExtra("isBlockedByPeer", false);

        myUsername = getSharedPreferences("PigeonPrefs", MODE_PRIVATE).getString("username", "OFFLINE_NODE");

        Toolbar toolbar = findViewById(R.id.toolbarChat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(peerUsername);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rvMessages = findViewById(R.id.rvChatMessages);
        etInput = findViewById(R.id.etMessageInput);
        btnSend = findViewById(R.id.btnSendMessage);
        btnToggleActions = findViewById(R.id.btnToggleActions);
        layoutAttachments = findViewById(R.id.layoutAttachments);
        btnAttachImage = findViewById(R.id.btnAttachImage);
        btnAttachLocation = findViewById(R.id.btnAttachLocation);

        dbHelper = new PigeonDatabaseHelper(this);
        messageList = dbHelper.getMessages(peerUsername);

        adapter = new MessageAdapter(messageList, false);
        adapter.setListener(new MessageAdapter.MessageInteractionListener() {
            @Override
            public void onDeleteForMe(Message msg, int position) {
                String payload = (msg.getType() == Message.TYPE_IMAGE) ? msg.getImageBase64() : msg.getText();
                dbHelper.deleteMessage(peerUsername, msg.getTimestamp(), payload);
                messageList.remove(position);
                adapter.notifyItemRemoved(position);
            }

            @Override
            public void onDeleteForBoth(Message msg, int position) {
                String payload = (msg.getType() == Message.TYPE_IMAGE) ? msg.getImageBase64() : msg.getText();
                dbHelper.deleteMessage(peerUsername, msg.getTimestamp(), payload);
                messageList.remove(position);
                adapter.notifyItemRemoved(position);
                if (isBound && pigeonService != null) {
                    try {
                        JSONObject reqPayload = new JSONObject();
                        reqPayload.put("event", "delete_message_both");
                        JSONObject data = new JSONObject();
                        data.put("target", peerUsername);
                        data.put("sender", myUsername);
                        data.put("timestamp", msg.getTimestamp());
                        data.put("text", payload);
                        reqPayload.put("data", data);
                        pigeonService.sendMessage(reqPayload.toString());
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onForward(Message msg) {
                showForwardDialog(msg);
            }
        });

        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
        rvMessages.scrollToPosition(messageList.size() - 1);

        btnToggleActions.setOnClickListener(v -> {
            if (layoutAttachments.getVisibility() == View.GONE) {
                layoutAttachments.setVisibility(View.VISIBLE);
                btnToggleActions.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            } else {
                layoutAttachments.setVisibility(View.GONE);
                btnToggleActions.setImageResource(R.drawable.ic_add);
            }
        });

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                transmitPayload(text, "text");
                etInput.setText("");
            }
        });

        btnAttachImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
            layoutAttachments.setVisibility(View.GONE);
            btnToggleActions.setImageResource(R.drawable.ic_add);
        });

        btnAttachLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fetchAndSendLocation();
            } else {
                locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
            layoutAttachments.setVisibility(View.GONE);
            btnToggleActions.setImageResource(R.drawable.ic_add);
        });

        if (isBlockedByMe || isBlockedByPeer) {
            etInput.setEnabled(false);
            btnSend.setVisibility(View.GONE);
            btnToggleActions.setVisibility(View.GONE);
            etInput.setHint("Messaging blocked by network policy");
        }

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void refreshLocalMessages() {
        messageList.clear();
        messageList.addAll(dbHelper.getMessages(peerUsername));
        adapter.notifyDataSetChanged();
    }

    private void requestConnectionsList() {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "get_connections");
                JSONObject data = new JSONObject();
                data.put("username", myUsername);
                payload.put("data", data);
                pigeonService.sendMessage(payload.toString());
            } catch (Exception ignored) {
            }
        }
    }

    private void fetchAndSendLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null)
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    transmitPayload("GPS: " + location.getLatitude() + "," + location.getLongitude(), "location");
                } else {
                    Toast.makeText(this, "Unable to get location. Try opening Maps first.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    private void processAndSendImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();
            if (width > 200 || height > 200) {
                float ratio = Math.min((float) 200 / width, (float) 200 / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
            }
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream);
            String base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);

            startAppLevelChunking(base64Image, "image");
        } catch (Exception ignored) {
        }
    }

    private void transmitPayload(String text, String type) {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "message");
                JSONObject data = new JSONObject();
                data.put("sender", myUsername);
                data.put("receiver", peerUsername);
                data.put("text", text);
                data.put("timestamp", "Now");
                data.put("type", type);
                payload.put("data", data);
                pigeonService.sendMessage(payload.toString());

                dbHelper.insertMessage(peerUsername, myUsername, peerUsername, text, "Now", true, type, false);
                Message m = ("image".equals(type)) ? new Message(myUsername, text, "Now", true, Message.TYPE_IMAGE) :
                        ("location".equals(type)) ? new Message(myUsername, text, "Now", true, Message.TYPE_LOCATION) :
                        new Message(myUsername, text, "Now", true);
                messageList.add(m);
                adapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
            } catch (Exception ignored) {
            }
        } else {
            Toast.makeText(this, "Not connected to any node AP", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAppLevelChunking(String fullBase64Data, String type) {
        int chunkSize = 60;
        appChunks = new ArrayList<>();
        for (int i = 0; i < fullBase64Data.length(); i += chunkSize) {
            appChunks.add(fullBase64Data.substring(i, Math.min(i + chunkSize, fullBase64Data.length())));
        }
        currentAppChunk = -1;
        currentTransferType = type;
        isSendingImage = true;

        showTransferDialog("Waiting for peer...", true);
        sendAppChunkControl("img_start", appChunks.size(), type, 0);
        transferHandler.postDelayed(transferTimeoutRunnable, 5000);
    }

    private void handleImageChunkProtocol(JSONObject data) {
        String type = data.optString("type");

        if ("img_start".equals(type)) {
            isSendingImage = false;
            incomingImageBuffer = new StringBuilder();
            expectedIncomingChunk = 0;
            incomingImageType = data.optString("text", "image");
            expectedIncomingTotal = data.optInt("total", 1);

            showTransferDialog("Receiving Image...", false);
            sendAppChunkControl("img_ack", expectedIncomingTotal, "", 0);

            transferHandler.removeCallbacks(transferTimeoutRunnable);
            transferHandler.postDelayed(transferTimeoutRunnable, 5000);

        } else if ("img_ack".equals(type)) {
            if (!isSendingImage) return;
            transferHandler.removeCallbacks(transferTimeoutRunnable);
            int reqChunk = data.optInt("chunk", 0);

            if (reqChunk < appChunks.size()) {
                if (transferDialog != null && transferDialog.isShowing())
                    transferDialog.setTitle("Sending Image...");
                currentAppChunk = reqChunk;
                sendAppChunkData(appChunks.get(reqChunk), reqChunk, appChunks.size());
                if (transferProgress != null)
                    transferProgress.setProgress((int) ((reqChunk * 100.0f) / appChunks.size()));
                transferHandler.postDelayed(transferTimeoutRunnable, 5000);

            } else if (reqChunk == appChunks.size()) {
                dismissTransferDialog();
                String fullData = String.join("", appChunks);
                dbHelper.insertMessage(peerUsername, myUsername, peerUsername, fullData, "Now", true, currentTransferType, true);
                Message m = new Message(myUsername, fullData, "Now", true, Message.TYPE_IMAGE);
                m.setDelivered(true);
                messageList.add(m);
                adapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
            }

        } else if ("img_chunk".equals(type)) {
            if (isSendingImage) return;
            transferHandler.removeCallbacks(transferTimeoutRunnable);
            int c = data.optInt("chunk", 0);
            int t = data.optInt("total", 1);

            if (c == expectedIncomingChunk) {
                incomingImageBuffer.append(data.optString("text", ""));
                expectedIncomingChunk++;
                if (transferProgress != null)
                    transferProgress.setProgress((int) ((expectedIncomingChunk * 100.0f) / t));

                sendAppChunkControl("img_ack", t, "", expectedIncomingChunk);

                if (expectedIncomingChunk == t) {
                    dismissTransferDialog();
                    Message m = new Message(data.optString("sender"), incomingImageBuffer.toString(), data.optString("timestamp", "Now"), false, Message.TYPE_IMAGE);
                    dbHelper.insertMessage(peerUsername, m.getSender(), peerUsername, m.getText(), m.getTimestamp(), false, incomingImageType, true);
                    messageList.add(m);
                    adapter.notifyItemInserted(messageList.size() - 1);
                    rvMessages.scrollToPosition(messageList.size() - 1);
                } else {
                    transferHandler.postDelayed(transferTimeoutRunnable, 5000);
                }
            } else {
                sendAppChunkControl("img_ack", t, "", expectedIncomingChunk);
                transferHandler.postDelayed(transferTimeoutRunnable, 5000);
            }

        } else if ("img_cancel".equals(type)) {
            dismissTransferDialog();
            Toast.makeText(this, "Transfer cancelled by peer", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTransferDialog(String title, boolean isSender) {
        runOnUiThread(() -> {
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(60, 40, 60, 40);

            transferProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            transferProgress.setMax(100);
            layout.addView(transferProgress);

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(layout)
                    .setCancelable(false)
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        sendAppChunkControl("img_cancel", 0, "", 0);
                        dismissTransferDialog();
                    });
            transferDialog = builder.create();
            transferDialog.show();
        });
    }

    private void dismissTransferDialog() {
        runOnUiThread(() -> {
            if (transferDialog != null && transferDialog.isShowing()) transferDialog.dismiss();
            transferHandler.removeCallbacks(transferTimeoutRunnable);
        });
    }

    private void sendAppChunkControl(String type, int total, String transferType, int reqChunk) {
        if (!isBound || pigeonService == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("event", "message");
            JSONObject data = new JSONObject();
            data.put("sender", myUsername);
            data.put("receiver", peerUsername);
            data.put("type", type);
            data.put("total", total);
            data.put("chunk", reqChunk);
            data.put("text", transferType);
            data.put("timestamp", "Now");
            payload.put("data", data);
            pigeonService.sendMessage(payload.toString());
        } catch (Exception ignored) {
        }
    }

    private void sendAppChunkData(String chunkData, int chunk, int total) {
        if (!isBound || pigeonService == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("event", "message");
            JSONObject data = new JSONObject();
            data.put("sender", myUsername);
            data.put("receiver", peerUsername);
            data.put("type", "img_chunk");
            data.put("chunk", chunk);
            data.put("total", total);
            data.put("text", chunkData);
            data.put("timestamp", "Now");
            payload.put("data", data);
            pigeonService.sendMessage(payload.toString());
        } catch (Exception ignored) {
        }
    }

    private void forwardPayloadToUser(String targetUser, String text, String type) {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "message");
                JSONObject data = new JSONObject();
                data.put("sender", myUsername);
                data.put("receiver", targetUser);
                data.put("text", text);
                data.put("timestamp", "Now");
                data.put("type", type);
                payload.put("data", data);
                pigeonService.sendMessage(payload.toString());
                dbHelper.insertMessage(targetUser, myUsername, targetUser, text, "Now", true, type, false);
            } catch (Exception ignored) {
            }
        }
    }

    private void forwardPayloadToGroup(String targetGroupId, String text, String type) {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "group_message");
                JSONObject data = new JSONObject();
                data.put("groupId", targetGroupId);
                data.put("text", text);
                data.put("type", type);
                data.put("sender", myUsername);
                payload.put("data", data);
                pigeonService.sendWssMessage(payload.toString());
            } catch (Exception ignored) {
            }
        }
    }

    private void showForwardDialog(Message msg) {
        currentForwardingMessage = msg;
        forwardTargets.clear();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_forward, null);
        EditText etSearch = view.findViewById(R.id.etForwardSearch);
        RecyclerView rvTargets = view.findViewById(R.id.rvForwardTargets);

        forwardAdapter = new ForwardTargetAdapter(target -> {
            String owner = (currentForwardingMessage.getSender() != null && !currentForwardingMessage.getSender().isEmpty()) ? currentForwardingMessage.getSender() : peerUsername;
            String header = "[Forwarded from " + owner + "]\n";
            String rawPayload = (currentForwardingMessage.getType() == Message.TYPE_IMAGE) ? currentForwardingMessage.getImageBase64() : currentForwardingMessage.getText();
            String type = (currentForwardingMessage.getType() == Message.TYPE_IMAGE) ? "image" : (currentForwardingMessage.getType() == Message.TYPE_LOCATION) ? "location" : "text";

            if (target.isGroup()) {
                forwardPayloadToGroup(target.getId(), header + rawPayload, type);
            } else {
                forwardPayloadToUser(target.getId(), header + rawPayload, type);
            }
            Toast.makeText(this, "Message forwarded successfully", Toast.LENGTH_SHORT).show();
            if (forwardDialog != null) forwardDialog.dismiss();
        });

        rvTargets.setLayoutManager(new LinearLayoutManager(this));
        rvTargets.setAdapter(forwardAdapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                forwardAdapter.filter(s.toString());
            }

            public void afterTextChanged(Editable s) {
            }
        });

        builder.setView(view);
        forwardDialog = builder.create();
        forwardDialog.show();
        requestConnectionsList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_info) {
            String blockOption = isBlockedByMe ? "Unblock User" : "Block User";
            String[] options = {"Delete local history", "Delete chat for both", "Download history as HTML", blockOption};
            new AlertDialog.Builder(this)
                    .setTitle(peerUsername)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            dbHelper.clearHistory(peerUsername);
                            refreshLocalMessages();
                            Toast.makeText(this, "Local chat history cleared.", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            dbHelper.clearHistory(peerUsername);
                            refreshLocalMessages();
                            if (isBound && pigeonService != null) {
                                try {
                                    JSONObject payload = new JSONObject();
                                    payload.put("event", "delete_chat_both");
                                    JSONObject data = new JSONObject();
                                    data.put("target", peerUsername);
                                    data.put("sender", myUsername);
                                    payload.put("data", data);
                                    pigeonService.sendMessage(payload.toString());
                                } catch (Exception ignored) {
                                }
                            }
                            Toast.makeText(this, "Requested remote node to delete secure history.", Toast.LENGTH_SHORT).show();
                        } else if (which == 2) {
                            downloadHtmlHistory();
                        } else if (which == 3) {
                            if (isBound && pigeonService != null) {
                                try {
                                    JSONObject payload = new JSONObject();
                                    payload.put("event", isBlockedByMe ? "unblock_user" : "block_user");
                                    JSONObject data = new JSONObject();
                                    data.put("target", peerUsername);
                                    data.put("sender", myUsername);
                                    payload.put("data", data);
                                    pigeonService.sendMessage(payload.toString());
                                    isBlockedByMe = !isBlockedByMe;
                                    Toast.makeText(this, isBlockedByMe ? "User blocked." : "User unblocked.", Toast.LENGTH_SHORT).show();
                                    finish();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void downloadHtmlHistory() {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;'><h2>Encrypted Chat History: ").append(peerUsername).append("</h2><hr>");
        for (Message m : messageList) {
            html.append("<p><b>").append(m.getSender()).append("</b> (").append(m.getTimestamp()).append("): ");
            if (m.getType() == Message.TYPE_IMAGE) {
                html.append("[Encrypted Image Data]");
            } else {
                html.append(m.getText());
            }
            html.append("</p>");
        }
        html.append("</body></html>");

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "Pigeon_History_" + peerUsername + ".html");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(html.toString().getBytes());
                    os.close();
                    Toast.makeText(this, "Saved HTML conversation to Downloads folder.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save history.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissTransferDialog();
        if (isBound) {
            if (pigeonService != null) pigeonService.unregisterCallback(callback);
            unbindService(connection);
            isBound = false;
        }
    }
}
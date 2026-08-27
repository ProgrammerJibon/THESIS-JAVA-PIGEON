package bd.jibon.apps.pigeon;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import java.util.ArrayList;
import java.util.List;

public class GroupActivity extends AppCompatActivity {
    private String groupId;
    private String groupName;
    private String myUsername;
    private PigeonService pigeonService;
    private boolean isBound = false;
    private boolean isMember = true;
    private boolean isAdmin = false;

    private List<String> groupUsers = new ArrayList<>();
    private List<String> groupAdmins = new ArrayList<>();

    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private List<Message> messageList;
    private PigeonDatabaseHelper dbHelper;
    private Menu toolbarMenu;
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
                    processAndSendImage(result.getData().getData());
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
                        reconnectDialog = new AlertDialog.Builder(GroupActivity.this)
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

                    if ("group_message".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        String inGroupId = data.optString("groupId", "");
                        if (groupId.equals(inGroupId)) {
                            String sender = data.getString("sender");
                            String type = data.optString("type", "text");

                            if (type.startsWith("img_")) {
                                if (!myUsername.equals(sender)) handleImageChunkProtocol(data);
                                return;
                            }

                            if (!myUsername.equals(sender)) {
                                String text = data.optString("text", "");
                                String timestamp = data.optString("timestamp", "Now");
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
                        }
                    } else if ("delete_group_message_both".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        if (groupId.equals(data.getString("groupId"))) {
                            dbHelper.deleteMessage(groupId, data.getString("timestamp"), data.getString("text"));
                            refreshLocalMessages();
                        }
                    } else if ("group_info_res".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        if (groupId.equals(data.optString("groupId", ""))) {
                            JSONArray usersArr = data.optJSONArray("users");
                            JSONArray adminsArr = data.optJSONArray("admins");
                            groupUsers.clear();
                            groupAdmins.clear();
                            if (usersArr != null) {
                                for (int i = 0; i < usersArr.length(); i++)
                                    groupUsers.add(usersArr.getString(i));
                            }
                            if (adminsArr != null) {
                                for (int i = 0; i < adminsArr.length(); i++)
                                    groupAdmins.add(adminsArr.getString(i));
                            }
                            isMember = groupUsers.contains(myUsername);
                            isAdmin = groupAdmins.contains(myUsername);
                            updateUIForMembership();
                        }
                    } else if ("connections_list".equals(event)) {
                        JSONArray array = root.getJSONArray("data");
                        for (int i = 0; i < array.length(); i++) {
                            forwardTargets.add(new ForwardTarget(array.getJSONObject(i).getString("username"), array.getJSONObject(i).getString("username"), false));
                        }
                        if (forwardAdapter != null) forwardAdapter.updateData(forwardTargets);
                    } else if ("groups_list".equals(event)) {
                        JSONArray array = root.getJSONArray("data");
                        for (int i = 0; i < array.length(); i++) {
                            forwardTargets.add(new ForwardTarget(array.getJSONObject(i).getString("name"), array.getJSONObject(i).getString("id"), true));
                        }
                        if (forwardAdapter != null) forwardAdapter.updateData(forwardTargets);
                    }
                } catch (Exception ignored) {
                }
            });
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            PigeonService.LocalBinder binder = (PigeonService.LocalBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(callback);
            requestGroupInfo();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    private EditText etInput;
    private Button btnSend;
    private ImageButton btnToggleActions;
    private LinearLayout layoutAttachments;
    private ImageButton btnAttachImage;
    private ImageButton btnAttachLocation;
    private Message currentForwardingMessage;

    private void requestGroupInfo() {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject reqPayload = new JSONObject();
                reqPayload.put("event", "get_group_info");
                JSONObject data = new JSONObject();
                data.put("groupId", groupId);
                reqPayload.put("data", data);
                pigeonService.sendMessage(reqPayload.toString());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group);

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        if (groupName == null) groupName = "Tactical Channel";

        myUsername = getSharedPreferences("PigeonPrefs", MODE_PRIVATE).getString("username", "OFFLINE_NODE");
        dbHelper = new PigeonDatabaseHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbarGroup);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(groupName);
            getSupportActionBar().setSubtitle("ID: " + (groupId != null ? groupId : "0x00"));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rvMessages = findViewById(R.id.rvGroupMessages);
        etInput = findViewById(R.id.etGroupMessageInput);
        btnSend = findViewById(R.id.btnGroupSendMessage);
        btnToggleActions = findViewById(R.id.btnGroupToggleActions);
        layoutAttachments = findViewById(R.id.layoutGroupAttachments);
        btnAttachImage = findViewById(R.id.btnGroupAttachImage);
        btnAttachLocation = findViewById(R.id.btnGroupAttachLocation);

        messageList = dbHelper.getMessages(groupId);
        adapter = new MessageAdapter(messageList, true);
        adapter.setListener(new MessageAdapter.MessageInteractionListener() {
            @Override
            public void onDeleteForMe(Message msg, int position) {
                String payload = (msg.getType() == Message.TYPE_IMAGE) ? msg.getImageBase64() : msg.getText();
                dbHelper.deleteMessage(groupId, msg.getTimestamp(), payload);
                messageList.remove(position);
                adapter.notifyItemRemoved(position);
            }

            @Override
            public void onDeleteForBoth(Message msg, int position) {
                String payload = (msg.getType() == Message.TYPE_IMAGE) ? msg.getImageBase64() : msg.getText();
                dbHelper.deleteMessage(groupId, msg.getTimestamp(), payload);
                messageList.remove(position);
                adapter.notifyItemRemoved(position);
                if (isBound && pigeonService != null) {
                    try {
                        JSONObject reqPayload = new JSONObject();
                        reqPayload.put("event", "delete_group_message_both");
                        JSONObject data = new JSONObject();
                        data.put("groupId", groupId);
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
                transmitGroupPayload(text, "text");
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

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void refreshLocalMessages() {
        messageList.clear();
        messageList.addAll(dbHelper.getMessages(groupId));
        adapter.notifyDataSetChanged();
    }

    private void updateUIForMembership() {
        if (!isMember) {
            etInput.setVisibility(View.GONE);
            btnSend.setVisibility(View.GONE);
            btnToggleActions.setVisibility(View.GONE);
            layoutAttachments.setVisibility(View.GONE);
            if (getSupportActionBar() != null) getSupportActionBar().setSubtitle("Not a member");
            if (toolbarMenu != null) {
                MenuItem infoItem = toolbarMenu.findItem(R.id.action_group_info);
                if (infoItem != null) infoItem.setVisible(false);
            }
        } else {
            etInput.setVisibility(View.VISIBLE);
            btnSend.setVisibility(View.VISIBLE);
            btnToggleActions.setVisibility(View.VISIBLE);
            if (getSupportActionBar() != null) getSupportActionBar().setSubtitle("ID: " + groupId);
            if (toolbarMenu != null) {
                MenuItem infoItem = toolbarMenu.findItem(R.id.action_group_info);
                if (infoItem != null) infoItem.setVisible(true);
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
                    transmitGroupPayload("GPS: " + location.getLatitude() + "," + location.getLongitude(), "location");
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
            if (width > 100 || height > 100) {
                float ratio = Math.min((float) 100 / width, (float) 100 / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
            }
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 33, outputStream);
            String base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
            startAppLevelChunking(base64Image, "image");
        } catch (Exception e) {
            Toast.makeText(this, "Image processing failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void transmitGroupPayload(String dataStr, String type) {
        if (isBound && pigeonService != null) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "group_message");
                JSONObject data = new JSONObject();
                data.put("groupId", groupId);
                data.put("text", dataStr);
                data.put("type", type);
                data.put("sender", myUsername);
                payload.put("data", data);
                pigeonService.sendWssMessage(payload.toString());
            } catch (Exception ignored) {
            }
        }
        dbHelper.insertMessage(groupId, myUsername, groupId, dataStr, "Now", true, type, false);
        Message m = ("image".equals(type)) ? new Message(myUsername, dataStr, "Now", true, Message.TYPE_IMAGE) :
                ("location".equals(type)) ? new Message(myUsername, dataStr, "Now", true, Message.TYPE_LOCATION) :
                new Message(myUsername, dataStr, "Now", true);
        messageList.add(m);
        adapter.notifyItemInserted(messageList.size() - 1);
        rvMessages.scrollToPosition(messageList.size() - 1);
    }

    // --- APP LEVEL CHUNKING LOGIC ---
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
                dbHelper.insertMessage(groupId, myUsername, groupId, fullData, "Now", true, currentTransferType, true);
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
                    dbHelper.insertMessage(groupId, m.getSender(), groupId, m.getText(), m.getTimestamp(), false, incomingImageType, true);
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
            Toast.makeText(this, "Transfer cancelled by sender", Toast.LENGTH_SHORT).show();
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
            payload.put("event", "group_message");
            JSONObject data = new JSONObject();
            data.put("sender", myUsername);
            data.put("groupId", groupId);
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
            payload.put("event", "group_message");
            JSONObject data = new JSONObject();
            data.put("sender", myUsername);
            data.put("groupId", groupId);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.group_toolbar_menu, menu);
        toolbarMenu = menu;
        updateUIForMembership();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_group_info) {
            showGroupOptionsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showGroupOptionsDialog() {
        String[] options = {"View Members", "Leave Group", "Purge Local History"};
        new AlertDialog.Builder(this)
                .setTitle("Group Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showMembersDialog();
                    else if (which == 1) leaveGroup();
                    else if (which == 2) {
                        dbHelper.clearHistory(groupId);
                        refreshLocalMessages();
                        Toast.makeText(this, "Local group history purged.", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void showMembersDialog() {
        List<String> combinedMembers = new ArrayList<>();
        for (String u : groupUsers) {
            if (groupAdmins.contains(u)) combinedMembers.add(u + " (Admin)");
            else combinedMembers.add(u);
        }
        new AlertDialog.Builder(this)
                .setTitle("Group Members")
                .setItems(combinedMembers.toArray(new String[0]), (dialog, which) -> {
                    if (isAdmin && !groupUsers.get(which).equals(myUsername)) {
                        showAdminActionsDialog(groupUsers.get(which));
                    }
                }).setPositiveButton("Close", null).show();
    }

    private void showAdminActionsDialog(String targetUser) {
        boolean targetIsAdmin = groupAdmins.contains(targetUser);
        String[] options = {"Remove User", targetIsAdmin ? "Remove Admin" : "Make Admin"};
        new AlertDialog.Builder(this)
                .setTitle("Admin Actions for " + targetUser)
                .setItems(options, (dialog, which) -> {
                    if (isBound && pigeonService != null) {
                        try {
                            JSONObject reqPayload = new JSONObject();
                            JSONObject data = new JSONObject();
                            data.put("groupId", groupId);
                            data.put("target", targetUser);
                            data.put("sender", myUsername);
                            reqPayload.put("event", (which == 0) ? "group_remove_user" : (targetIsAdmin ? "group_remove_admin" : "group_add_admin"));
                            reqPayload.put("data", data);
                            pigeonService.sendMessage(reqPayload.toString());
                            Toast.makeText(this, "Action requested on network.", Toast.LENGTH_SHORT).show();
                            requestGroupInfo();
                        } catch (Exception ignored) {
                        }
                    }
                }).show();
    }

    private void leaveGroup() {
        if (isBound && pigeonService != null) {
            try {
                JSONObject reqPayload = new JSONObject();
                reqPayload.put("event", "group_leave");
                JSONObject data = new JSONObject();
                data.put("groupId", groupId);
                data.put("sender", myUsername);
                reqPayload.put("data", data);
                pigeonService.sendMessage(reqPayload.toString());
                isMember = false;
                updateUIForMembership();
                Toast.makeText(this, "Left the group.", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        }
    }

    // --- FORWARD LOGIC ---
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
            String owner = (currentForwardingMessage.getSender() != null && !currentForwardingMessage.getSender().isEmpty()) ? currentForwardingMessage.getSender() : groupName;
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

        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                pigeonService.sendMessage(new JSONObject().put("event", "get_connections").put("data", new JSONObject().put("username", myUsername)).toString());
                pigeonService.sendMessage(new JSONObject().put("event", "get_groups").put("data", new JSONObject().put("username", myUsername)).toString());
            } catch (Exception ignored) {
            }
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
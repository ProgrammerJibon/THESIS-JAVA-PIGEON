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
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private String peerUsername;
    private String myUsername;
    private PigeonService pigeonService;
    private boolean isBound = false;
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
    private final PigeonService.PigeonCallback callback = new PigeonService.PigeonCallback() {
        @Override
        public void onConnectionStateChanged(boolean connected, String message) {
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
                        String receiver = data.getString("receiver");
                        String text = data.optString("text", "");
                        String timestamp = data.getString("timestamp");
                        String type = data.optString("type", "text");

                        if (peerUsername.equals(sender)) {
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
                    } else if ("send_error".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        String errorMsg = data.getString("message");
                        new AlertDialog.Builder(ChatActivity.this)
                                .setTitle("Transmission Blocked")
                                .setMessage(errorMsg)
                                .setPositiveButton("OK", null)
                                .show();
                    } else if ("msg_delivered".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        String receiver = data.getString("receiver");
                        if (peerUsername.equals(receiver)) {
                            Toast.makeText(ChatActivity.this, "Message delivered to " + receiver, Toast.LENGTH_SHORT).show();
                        }
                    } else if ("delete_message_both".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        String sender = data.getString("sender");
                        if (peerUsername.equals(sender)) {
                            String timestamp = data.getString("timestamp");
                            String text = data.getString("text");
                            dbHelper.deleteMessage(peerUsername, timestamp, text);
                            refreshLocalMessages();
                        }
                    } else if ("delete_chat".equals(event)) {
                        String peer = root.getJSONObject("data").getString("peer");
                        if (peerUsername.equals(peer)) {
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

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    processAndSendImage(imageUri);
                }
            }
    );
    private boolean isBlockedByMe = false;
    private boolean isBlockedByPeer = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            PigeonService.LocalBinder binder = (PigeonService.LocalBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(callback);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

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
                Toast.makeText(ChatActivity.this, "Forward selected. (Routing disabled in demo)", Toast.LENGTH_SHORT).show();
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
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
            layoutAttachments.setVisibility(View.GONE);
            btnToggleActions.setImageResource(R.drawable.ic_add);
        });

        if (isBlockedByMe || isBlockedByPeer) {
            etInput.setEnabled(false);
            btnSend.setEnabled(false);
            btnToggleActions.setEnabled(false);
            etInput.setHint("Messaging blocked");
        }

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void refreshLocalMessages() {
        messageList.clear();
        messageList.addAll(dbHelper.getMessages(peerUsername));
        adapter.notifyDataSetChanged();
    }

    private void fetchAndSendLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (location != null) {
                    String locStr = "GPS: " + location.getLatitude() + "," + location.getLongitude();
                    transmitPayload(locStr, "location");
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

            if (width > 150 || height > 150) {
                float ratio = Math.min((float) 150 / width, (float) 150 / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
            }
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);
            transmitPayload(base64Image, "image");
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
                Message m;
                if ("image".equals(type)) {
                    m = new Message(myUsername, text, "Now", true, Message.TYPE_IMAGE);
                } else if ("location".equals(type)) {
                    m = new Message(myUsername, text, "Now", true, Message.TYPE_LOCATION);
                } else {
                    m = new Message(myUsername, text, "Now", true);
                }
                messageList.add(m);
                adapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
            } catch (Exception ignored) {
            }
        } else {
            Toast.makeText(this, "Not connected to any node AP", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_info) {
            showInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showInfoDialog() {
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
                })
                .show();
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
        if (isBound) {
            if (pigeonService != null) {
                pigeonService.unregisterCallback(callback);
            }
            unbindService(connection);
            isBound = false;
        }
    }
}
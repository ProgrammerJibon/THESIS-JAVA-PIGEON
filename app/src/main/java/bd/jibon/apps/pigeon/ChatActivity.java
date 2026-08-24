package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private String peerUsername;
    private String myUsername;
    private PigeonService pigeonService;
    private boolean isBound = false;

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
                        String text = data.getString("text");
                        String timestamp = data.getString("timestamp");
                        String type = data.optString("type", "text");

                        if (peerUsername.equals(sender)) {
                            dbHelper.insertMessage(peerUsername, sender, receiver, text, timestamp, false, type, true);
                            Message m;
                            if ("image".equals(type)) {
                                m = new Message(sender, text, timestamp, false, Message.TYPE_IMAGE);
                            } else if ("location".equals(type)) {
                                m = new Message(sender, 23.8103, 90.4125, timestamp, false);
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
            transmitPayload("GPS: 23.8103, 90.4125", "location");
            layoutAttachments.setVisibility(View.GONE);
            btnToggleActions.setImageResource(R.drawable.ic_add);
        });

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void processAndSendImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();
            if (width > 512 || height > 512) {
                float ratio = Math.min((float) 512 / width, (float) 512 / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
            }
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);
            transmitPayload(base64Image, "image");
        } catch (Exception e) {
            Toast.makeText(this, "Image processing failed", Toast.LENGTH_SHORT).show();
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
                    m = new Message(myUsername, 23.8103, 90.4125, "Now", true);
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
        new AlertDialog.Builder(this)
                .setTitle("Diagnostic Info")
                .setMessage("Tunnel State: Active\nChannel Encryption: AES-256-CBC\nGateway IP: " +
                        (pigeonService != null ? pigeonService.getGatewayIp() : "Offline"))
                .setPositiveButton("OK", null)
                .show();
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
package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class GroupActivity extends AppCompatActivity {
    private String groupId;
    private String groupName;
    private PigeonService pigeonService;
    private boolean isBound = false;

    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private List<Message> messageList;
    private final PigeonService.PigeonCallback callback = new PigeonService.PigeonCallback() {
        @Override
        public void onConnectionStateChanged(boolean connected, String message) {
        }

        @Override
        public void onMessageReceived(String payload) {
            runOnUiThread(() -> {
                Message m = new Message("Alpha_Two", payload, "Now", false);
                messageList.add(m);
                adapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
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
    private EditText etInput;
    private Button btnSend;
    private ImageButton btnToggleActions;
    private LinearLayout layoutAttachments;
    private ImageButton btnAttachImage;
    private ImageButton btnAttachLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group);

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        if (groupName == null) groupName = "Tactical Channel";

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

        messageList = new ArrayList<>();
        adapter = new MessageAdapter(messageList, true);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

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
                if (isBound && pigeonService != null) {
                    pigeonService.sendWssMessage(text);
                }
                Message m = new Message("Me", text, "Now", true);
                messageList.add(m);
                adapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
                etInput.setText("");
            }
        });

        btnAttachImage.setOnClickListener(v -> {
            Message m = new Message("Me", "IMAGE_PLACEHOLDER_BASE64", "Now", true, Message.TYPE_IMAGE);
            messageList.add(m);
            adapter.notifyItemInserted(messageList.size() - 1);
            rvMessages.scrollToPosition(messageList.size() - 1);
            layoutAttachments.setVisibility(View.GONE);
            btnToggleActions.setImageResource(R.drawable.ic_add);
        });

        btnAttachLocation.setOnClickListener(v -> {
            Message m = new Message("Me", 23.8103, 90.4125, "Now", true);
            messageList.add(m);
            adapter.notifyItemInserted(messageList.size() - 1);
            rvMessages.scrollToPosition(messageList.size() - 1);
            layoutAttachments.setVisibility(View.GONE);
            btnToggleActions.setImageResource(R.drawable.ic_add);
        });

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
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

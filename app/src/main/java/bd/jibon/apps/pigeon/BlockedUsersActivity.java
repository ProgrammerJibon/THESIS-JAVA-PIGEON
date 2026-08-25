package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BlockedUsersActivity extends AppCompatActivity {
    private PigeonService pigeonService;
    private boolean isBound = false;
    private String myUsername;
    private RecyclerView rvBlockedUsers;
    private BlockedUserAdapter adapter;
    private List<BlockedUser> blockedList;

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
                    if ("block_list".equals(event)) {
                        JSONArray array = root.getJSONArray("data");
                        blockedList.clear();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            blockedList.add(new BlockedUser(obj.getString("username")));
                        }
                        adapter.notifyDataSetChanged();
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
            requestBlockList();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        myUsername = getSharedPreferences("PigeonPrefs", MODE_PRIVATE).getString("username", "OFFLINE_NODE");

        Toolbar toolbar = findViewById(R.id.toolbarBlocked);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Blocked Users");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        rvBlockedUsers = findViewById(R.id.rvBlockedUsers);
        blockedList = new ArrayList<>();
        adapter = new BlockedUserAdapter(blockedList, (user, position) -> showUnblockConfirmation(user, position));
        rvBlockedUsers.setLayoutManager(new LinearLayoutManager(this));
        rvBlockedUsers.setAdapter(adapter);

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void requestBlockList() {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "get_block_list");
                JSONObject data = new JSONObject();
                data.put("username", myUsername);
                payload.put("data", data);
                pigeonService.sendMessage(payload.toString());
            } catch (Exception ignored) {
            }
        }
    }

    private void showUnblockConfirmation(BlockedUser user, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Unblock User")
                .setMessage("Are you sure you want to unblock " + user.getUsername() + "?")
                .setPositiveButton("Unblock", (dialog, which) -> {
                    if (isBound && pigeonService != null) {
                        try {
                            JSONObject payload = new JSONObject();
                            payload.put("event", "unblock_user");
                            JSONObject data = new JSONObject();
                            data.put("target", user.getUsername());
                            data.put("sender", myUsername);
                            payload.put("data", data);
                            pigeonService.sendMessage(payload.toString());

                            blockedList.remove(position);
                            adapter.notifyItemRemoved(position);
                            Toast.makeText(this, user.getUsername() + " has been unblocked.", Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
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
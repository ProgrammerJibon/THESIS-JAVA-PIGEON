package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private DrawerLayout drawerLayout;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private PigeonService pigeonService;
    private boolean isBound = false;
    private AlertDialog reconnectDialog;

    private final PigeonService.PigeonCallback callback = new PigeonService.PigeonCallback() {
        @Override
        public void onConnectionStateChanged(boolean connected, String message) {
            runOnUiThread(() -> {
                if (!connected && !isFinishing()) {
                    if (reconnectDialog == null) {
                        reconnectDialog = new AlertDialog.Builder(MainActivity.this)
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
        public void onMessageReceived(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject root = new JSONObject(json);
                    String event = root.optString("event", "");
                    if ("connect_user_response".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        boolean success = data.getBoolean("success");
                        String username = data.getString("username");
                        if (success) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Connection Success")
                                    .setMessage("Successfully established a secure off-grid link with user: " + username)
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else {
                            String err = data.optString("message", "User not found on the local node.");
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Connection Failed")
                                    .setMessage(err)
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    } else if ("group_create_response".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        boolean success = data.getBoolean("success");
                        String groupId = data.getString("groupId");
                        String groupName = data.getString("groupName");
                        if (success) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Group Network Created")
                                    .setMessage("Group: " + groupName + "\nGroup ID: " + groupId + "\n\nShare this ID with other tactical nodes to join.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    } else if ("group_join_response".equals(event)) {
                        JSONObject data = root.getJSONObject("data");
                        boolean success = data.getBoolean("success");
                        String groupId = data.getString("groupId");
                        String groupName = data.optString("groupName", "");
                        if (success) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Joined Group Network")
                                    .setMessage("Successfully joined: " + groupName + " (#" + groupId + ")")
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Group Join Failed")
                                    .setMessage("Requested group ID was not found on this mesh node.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PigeonService.LocalBinder binder = (PigeonService.LocalBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(callback);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);

        viewPager.setAdapter(new MainPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.title_chats);
                    break;
                case 1:
                    tab.setText(R.string.title_groups);
                    break;
                case 2:
                    tab.setText(R.string.title_profile);
                    break;
            }
        }).attach();

        Intent intent = new Intent(this, PigeonService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });
    }

    private void showPlusMenu() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plus_menu, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.layoutConnectUser).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showConnectUserDialog();
        });

        view.findViewById(R.id.layoutJoinGroup).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showJoinGroupDialog();
        });

        view.findViewById(R.id.layoutCreateGroup).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showCreateGroupDialog();
        });

        bottomSheetDialog.show();
    }

    private void showConnectUserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect with New User");
        final EditText input = new EditText(this);
        input.setHint("Enter Username");
        builder.setView(input);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String username = input.getText().toString().trim();
            if (!username.isEmpty()) {
                sendWssEvent("connect_user", "username", username);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showJoinGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join a Group");
        final EditText input = new EditText(this);
        input.setHint("Enter 8-digit Group ID");
        builder.setView(input);

        builder.setPositiveButton("Join", (dialog, which) -> {
            String groupId = input.getText().toString().trim();
            if (groupId.length() == 8) {
                sendWssEvent("group_join", "groupId", groupId);
            } else {
                Toast.makeText(MainActivity.this, "Group ID must be exactly 8 digits", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showCreateGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create a Group");
        final EditText input = new EditText(this);
        input.setHint("Enter Group Name");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String groupName = input.getText().toString().trim();
            if (!groupName.isEmpty()) {
                sendWssEvent("group_create", "groupName", groupName);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void sendWssEvent(String eventName, String key, String value) {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", eventName);
                JSONObject data = new JSONObject();
                data.put(key, value);
                payload.put("data", data);
                pigeonService.sendMessage(payload.toString());
            } catch (Exception ignored) {
            }
        } else {
            Toast.makeText(this, "Not connected to any node AP", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_plus) {
            showPlusMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_chats) {
            viewPager.setCurrentItem(0);
        } else if (id == R.id.nav_groups) {
            viewPager.setCurrentItem(1);
        } else if (id == R.id.nav_profile) {
            viewPager.setCurrentItem(2);
        } else if (id == R.id.nav_blocked_users) {
            startActivity(new Intent(this, BlockedUsersActivity.class));
        } else if (id == R.id.nav_about_app) {
            startActivity(new Intent(this, AboutAppActivity.class));
        } else if (id == R.id.nav_about_device) {
            startActivity(new Intent(this, AboutDeviceActivity.class));
        } else if (id == R.id.nav_terms) {
            startActivity(new Intent(this, TermsActivity.class));
        } else if (id == R.id.nav_contact) {
            startActivity(new Intent(this, ContactActivity.class));
        } else if (id == R.id.nav_updates) {
            startActivity(new Intent(this, UpdatesActivity.class));
        } else if (id == R.id.nav_logout) {
            if (isBound) {
                pigeonService.disconnect();
            }
            getSharedPreferences("PigeonPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, ConnectionActivity.class));
            finish();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            if (pigeonService != null) {
                pigeonService.unregisterCallback(callback);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}
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

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, PigeonService.PigeonCallback {

    private DrawerLayout drawerLayout;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private PigeonService pigeonService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PigeonService.LocalBinder binder = (PigeonService.LocalBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(MainActivity.this);
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

    private void showPlusMenu() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plus_menu, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.btnConnectNewUser).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            promptConnectUser();
        });

        view.findViewById(R.id.btnJoinGroup).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            promptJoinGroup();
        });

        view.findViewById(R.id.btnCreateGroup).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            promptCreateGroup();
        });

        bottomSheetDialog.show();
    }

    private void promptConnectUser() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect with User");
        final EditText input = new EditText(this);
        input.setHint("Enter username");
        builder.setView(input);
        builder.setPositiveButton("Connect", (dialog, which) -> {
            String username = input.getText().toString().trim();
            if (!username.isEmpty()) {
                sendWssEvent("connect_user", new JSONObject() {{
                    try {
                        put("username", username);
                    } catch (JSONException ignored) {
                    }
                }});
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void promptJoinGroup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join a Group");
        final EditText input = new EditText(this);
        input.setHint("Enter 8-digit Group ID");
        builder.setView(input);
        builder.setPositiveButton("Join", (dialog, which) -> {
            String groupId = input.getText().toString().trim();
            if (!groupId.isEmpty()) {
                sendWssEvent("group_join", new JSONObject() {{
                    try {
                        put("groupId", groupId);
                    } catch (JSONException ignored) {
                    }
                }});
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void promptCreateGroup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create a Group");
        final EditText input = new EditText(this);
        input.setHint("Enter Group Name");
        builder.setView(input);
        builder.setPositiveButton("Create", (dialog, which) -> {
            String groupName = input.getText().toString().trim();
            if (!groupName.isEmpty()) {
                sendWssEvent("group_create", new JSONObject() {{
                    try {
                        put("groupName", groupName);
                    } catch (JSONException ignored) {
                    }
                }});
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendWssEvent(String event, JSONObject data) {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", event);
                payload.put("data", data);
                pigeonService.sendMessage(payload.toString());
            } catch (JSONException ignored) {
            }
        } else {
            Toast.makeText(this, "Not connected to PIGEON node", Toast.LENGTH_SHORT).show();
        }
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
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            if (pigeonService != null) {
                pigeonService.unregisterCallback(this);
            }
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
    }

    @Override
    public void onMessageReceived(String json) {
        runOnUiThread(() -> {
            try {
                JSONObject root = new JSONObject(json);
                String event = root.optString("event", "");
                if ("connect_user_response".equals(event)) {
                    JSONObject data = root.optJSONObject("data");
                    if (data != null && data.optBoolean("success", false)) {
                        String username = data.optString("username", "");
                        Toast.makeText(this, "Connected with user: " + username, Toast.LENGTH_SHORT).show();
                        pigeonService.sendMessage("{\"event\":\"get_chats\"}");
                    }
                } else if ("group_create_response".equals(event)) {
                    JSONObject data = root.optJSONObject("data");
                    if (data != null && data.optBoolean("success", false)) {
                        String name = data.optString("groupName", "");
                        String gid = data.optString("groupId", "");
                        Toast.makeText(this, "Group Created: " + name + " (ID: " + gid + ")", Toast.LENGTH_LONG).show();
                        pigeonService.sendMessage("{\"event\":\"get_groups\"}");
                    }
                } else if ("group_join_response".equals(event)) {
                    JSONObject data = root.optJSONObject("data");
                    if (data != null && data.optBoolean("success", false)) {
                        String name = data.optString("groupName", "");
                        String gid = data.optString("groupId", "");
                        Toast.makeText(this, "Joined Group: " + name + " (#" + gid + ")", Toast.LENGTH_LONG).show();
                        pigeonService.sendMessage("{\"event\":\"get_groups\"}");
                    }
                }
            } catch (JSONException ignored) {
            }
        });
    }
}

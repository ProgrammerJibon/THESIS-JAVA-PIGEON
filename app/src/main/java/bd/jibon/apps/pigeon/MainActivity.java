package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

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
            onServiceConnectedAction();
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

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_plus) {
                showPlusMenu();
                return true;
            }
            return false;
        });
    }

    private void onServiceConnectedAction() {
    }

    private void showPlusMenu() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plus_menu, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.btnConnectNewUser).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Toast.makeText(MainActivity.this, R.string.plus_connect_user, Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnJoinGroup).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Toast.makeText(MainActivity.this, R.string.plus_join_group, Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnCreateGroup).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Toast.makeText(MainActivity.this, R.string.plus_create_group, Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.show();
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
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}

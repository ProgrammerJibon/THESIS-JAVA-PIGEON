package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment implements PigeonService.PigeonCallback {

    private RecyclerView rvGroups;
    private View emptyView;
    private SwipeRefreshLayout swipeRefresh;
    private GroupAdapter adapter;
    private List<Group> groupList;
    private PigeonService pigeonService;
    private boolean isBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PigeonService.LocalBinder binder = (PigeonService.LocalBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(GroupsFragment.this);
            fetchGroups();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            pigeonService = null;
        }
    };
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);
        rvGroups = view.findViewById(R.id.rvGroups);
        emptyView = view.findViewById(R.id.layoutEmptyGroups);
        swipeRefresh = view.findViewById(R.id.swipeRefreshGroups);

        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        groupList = new ArrayList<>();
        adapter = new GroupAdapter(groupList);
        rvGroups.setAdapter(adapter);

        prefs = requireActivity().getSharedPreferences("PigeonPrefs", Context.MODE_PRIVATE);

        swipeRefresh.setOnRefreshListener(this::fetchGroups);

        Intent intent = new Intent(getContext(), PigeonService.class);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        loadLocalGroups();
        return view;
    }

    @Override
    public void onDestroyView() {
        if (isBound) {
            if (pigeonService != null) {
                pigeonService.unregisterCallback(this);
            }
            requireActivity().unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroyView();
    }

    private void fetchGroups() {
        swipeRefresh.setRefreshing(true);
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            pigeonService.sendMessage("{\"event\":\"get_groups\"}");
        } else {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void loadLocalGroups() {
        groupList.clear();
        String json = prefs.getString("active_groups_list", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String groupId = obj.optString("groupId", "");
                String groupName = obj.optString("groupName", "Tactical Channel");
                groupList.add(new Group(groupName, groupId, "No group updates", "Now", 0));
            }
        } catch (JSONException ignored) {
        }
        adapter.notifyDataSetChanged();
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (groupList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            rvGroups.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvGroups.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
        if (connected) {
            fetchGroups();
        }
    }

    @Override
    public void onMessageReceived(String json) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            try {
                JSONObject root = new JSONObject(json);
                String event = root.optString("event", "");
                if ("get_groups_response".equals(event) || "group_create_response".equals(event) || "group_join_response".equals(event)) {
                    swipeRefresh.setRefreshing(false);
                    loadLocalGroups();
                }
            } catch (JSONException ignored) {
            }
        });
    }
}

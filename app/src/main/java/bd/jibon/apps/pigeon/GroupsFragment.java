package bd.jibon.apps.pigeon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment {
    private RecyclerView rvGroups;
    private View emptyView;
    private SwipeRefreshLayout swipeRefresh;
    private GroupAdapter adapter;
    private List<Group> groupList;
    private PigeonService pigeonService;
    private boolean isBound = false;
    private String myUsername;
    private PigeonDatabaseHelper dbHelper;

    private final PigeonService.PigeonCallback callback = new PigeonService.PigeonCallback() {
        @Override
        public void onConnectionStateChanged(boolean connected, String message) {
        }

        @Override
        public void onMessageReceived(String payload) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                try {
                    JSONObject root = new JSONObject(payload);
                    String event = root.optString("event", "");
                    if ("groups_list".equals(event)) {
                        JSONArray array = root.getJSONArray("data");
                        groupList.clear();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            String id = obj.getString("id");
                            String name = obj.getString("name");
                            int activeCount = obj.getInt("activeCount");

                            String lastMsg = dbHelper.getLastMessageText(id);
                            if (lastMsg.isEmpty()) lastMsg = "No messages yet";
                            String time = dbHelper.getLastMessageTime(id);
                            if (time.isEmpty()) time = "Now";

                            groupList.add(new Group(name, id, lastMsg, time, activeCount));
                        }
                        adapter.notifyDataSetChanged();
                        checkEmptyState();
                        swipeRefresh.setRefreshing(false);
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
            refreshList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);

        rvGroups = view.findViewById(R.id.rvGroups);
        emptyView = view.findViewById(R.id.layoutEmptyGroups);
        swipeRefresh = view.findViewById(R.id.swipeRefreshGroups);

        dbHelper = new PigeonDatabaseHelper(getContext());
        myUsername = getContext().getSharedPreferences("PigeonPrefs", Context.MODE_PRIVATE).getString("username", "OFFLINE_NODE");

        groupList = new ArrayList<>();
        adapter = new GroupAdapter(groupList);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvGroups.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::refreshList);

        Intent intent = new Intent(getContext(), PigeonService.class);
        getContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        checkEmptyState();
        return view;
    }

    private void refreshList() {
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("event", "get_groups");
                JSONObject data = new JSONObject();
                data.put("username", myUsername);
                payload.put("data", data);
                pigeonService.sendMessage(payload.toString());
                swipeRefresh.setRefreshing(true);
            } catch (Exception ignored) {
                swipeRefresh.setRefreshing(false);
            }
        } else {
            swipeRefresh.setRefreshing(false);
        }
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
    public void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isBound) {
            if (pigeonService != null) {
                pigeonService.unregisterCallback(callback);
            }
            getContext().unbindService(serviceConnection);
            isBound = false;
        }
    }
}

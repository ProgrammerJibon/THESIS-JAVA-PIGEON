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

import java.util.ArrayList;
import java.util.List;

public class ChatsFragment extends Fragment implements PigeonService.PigeonCallback {

    private RecyclerView rvChats;
    private View emptyView;
    private SwipeRefreshLayout swipeRefresh;
    private ChatAdapter adapter;
    private List<Chat> chatList;
    private PigeonService pigeonService;
    private boolean isBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PigeonService.LocalBinder binder = (PigeonService.LocalBinder) service;
            pigeonService = binder.getService();
            isBound = true;
            pigeonService.registerCallback(ChatsFragment.this);
            fetchChats();
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
        View view = inflater.inflate(R.layout.fragment_chats, container, false);
        rvChats = view.findViewById(R.id.rvChats);
        emptyView = view.findViewById(R.id.layoutEmptyChats);
        swipeRefresh = view.findViewById(R.id.swipeRefreshChats);

        rvChats.setLayoutManager(new LinearLayoutManager(getContext()));
        chatList = new ArrayList<>();
        adapter = new ChatAdapter(chatList);
        rvChats.setAdapter(adapter);

        prefs = requireActivity().getSharedPreferences("PigeonPrefs", Context.MODE_PRIVATE);

        swipeRefresh.setOnRefreshListener(this::fetchChats);

        Intent intent = new Intent(getContext(), PigeonService.class);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        loadLocalChats();
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

    private void fetchChats() {
        swipeRefresh.setRefreshing(true);
        if (isBound && pigeonService != null && pigeonService.isConnected()) {
            pigeonService.sendMessage("{\"event\":\"get_chats\"}");
        } else {
            swipeRefresh.setRefreshing(false);
        }
    }

    private void loadLocalChats() {
        chatList.clear();
        String json = prefs.getString("active_chats_list", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String username = arr.getString(i);
                chatList.add(new Chat(username, "Secure offline channel", "Now", true));
            }
        } catch (JSONException ignored) {
        }
        adapter.notifyDataSetChanged();
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (chatList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            rvChats.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvChats.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String message) {
        if (connected) {
            fetchChats();
        }
    }

    @Override
    public void onMessageReceived(String json) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            try {
                org.json.JSONObject root = new org.json.JSONObject(json);
                String event = root.optString("event", "");
                if ("get_chats_response".equals(event) || "connect_user_response".equals(event)) {
                    swipeRefresh.setRefreshing(false);
                    loadLocalChats();
                }
            } catch (JSONException ignored) {
            }
        });
    }
}

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
import java.util.Collections;
import java.util.List;

public class ChatsFragment extends Fragment {
    private RecyclerView rvChats;
    private View emptyView;
    private SwipeRefreshLayout swipeRefresh;
    private ChatAdapter adapter;
    private List<Chat> chatList;
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
                    if ("connections_list".equals(event)) {
                        JSONArray array = root.getJSONArray("data");
                        chatList.clear();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            String username = obj.getString("username");
                            boolean active = obj.optBoolean("active", false);
                            boolean blockedByMe = obj.optBoolean("blockedByMe", false);
                            boolean blockedByPeer = obj.optBoolean("blockedByPeer", false);
                            
                            String lastMsg = dbHelper.getLastMessageText(username);
                            if (lastMsg.isEmpty()) lastMsg = "No messages yet";
                            String time = dbHelper.getLastMessageTime(username);
                            if (time.isEmpty()) time = "Now";

                            chatList.add(new Chat(username, lastMsg, time, active, blockedByMe, blockedByPeer));
                        }

                        Collections.sort(chatList, (c1, c2) -> {
                            int id1 = dbHelper.getLastMessageId(c1.getUsername());
                            int id2 = dbHelper.getLastMessageId(c2.getUsername());
                            return Integer.compare(id2, id1);
                        });
                        
                        adapter.notifyDataSetChanged();
                        checkEmptyState();
                        swipeRefresh.setRefreshing(false);
                    } else if ("delete_chat".equals(event)) {
                        String peer = root.getJSONObject("data").getString("peer");
                        dbHelper.clearHistory(peer);
                        refreshList();
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
            adapter.setPigeonService(pigeonService);
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
        View view = inflater.inflate(R.layout.fragment_chats, container, false);

        rvChats = view.findViewById(R.id.rvChats);
        emptyView = view.findViewById(R.id.layoutEmptyChats);
        swipeRefresh = view.findViewById(R.id.swipeRefreshChats);

        dbHelper = new PigeonDatabaseHelper(getContext());
        myUsername = getContext().getSharedPreferences("PigeonPrefs", Context.MODE_PRIVATE).getString("username", "OFFLINE_NODE");

        chatList = new ArrayList<>();
        adapter = new ChatAdapter(getContext(), chatList, myUsername);
        rvChats.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChats.setAdapter(adapter);

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
                payload.put("event", "get_connections");
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
        if (chatList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            rvChats.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvChats.setVisibility(View.VISIBLE);
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
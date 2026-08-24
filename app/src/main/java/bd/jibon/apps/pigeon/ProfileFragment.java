package bd.jibon.apps.pigeon;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ProfileFragment extends Fragment {

    private TextView tvProfileUsername;
    private TextView tvNodeAddress;
    private TextView tvTunnelsCount;
    private TextView tvGroupsCount;
    private RecyclerView rvMediaGrid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvProfileUsername = view.findViewById(R.id.tvProfileUsername);
        tvNodeAddress = view.findViewById(R.id.tvProfileToken);
        tvTunnelsCount = view.findViewById(R.id.tvTotalChats);
        tvGroupsCount = view.findViewById(R.id.tvTotalGroups);
        rvMediaGrid = view.findViewById(R.id.rvProfileMedia);

        rvMediaGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));

        loadProfileData();
        return view;
    }

    private void loadProfileData() {
        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("PigeonPrefs", Context.MODE_PRIVATE);
            String username = prefs.getString("username", "OFFLINE_NODE");
            String nodeId = prefs.getString("node_id", "A1-FF");
            tvProfileUsername.setText(username);
            tvNodeAddress.setText("node://" + nodeId.toLowerCase());
            tvTunnelsCount.setText("0");
            tvGroupsCount.setText("0");
        }
    }
}

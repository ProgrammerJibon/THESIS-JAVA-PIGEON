package bd.jibon.apps.pigeon;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatsFragment extends Fragment {

    private RecyclerView rvChats;
    private View emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats, container, false);
        rvChats = view.findViewById(R.id.rvChats);
        emptyView = view.findViewById(R.id.layoutEmptyChats);
        rvChats.setLayoutManager(new LinearLayoutManager(getContext()));
        checkEmptyState();
        return view;
    }

    private void checkEmptyState() {
        if (rvChats.getAdapter() == null || rvChats.getAdapter().getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            rvChats.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvChats.setVisibility(View.VISIBLE);
        }
    }
}

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

public class GroupsFragment extends Fragment {

    private RecyclerView rvGroups;
    private View emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);
        rvGroups = view.findViewById(R.id.rvGroups);
        emptyView = view.findViewById(R.id.layoutEmptyGroups);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        checkEmptyState();
        return view;
    }

    private void checkEmptyState() {
        if (rvGroups.getAdapter() == null || rvGroups.getAdapter().getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            rvGroups.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvGroups.setVisibility(View.VISIBLE);
        }
    }
}

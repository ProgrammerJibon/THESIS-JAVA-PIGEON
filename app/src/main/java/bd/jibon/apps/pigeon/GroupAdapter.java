package bd.jibon.apps.pigeon;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {
    private final List<Group> groups;

    public GroupAdapter(List<Group> groups) {
        this.groups = groups;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = groups.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupName;
        TextView tvGroupId;
        TextView tvLastMsg;
        TextView tvTime;

        GroupViewHolder(View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvGroupId = itemView.findViewById(R.id.tvGroupId);
            tvLastMsg = itemView.findViewById(R.id.tvGroupLastMsg);
            tvTime = itemView.findViewById(R.id.tvGroupDate);
        }

        void bind(Group group) {
            tvGroupName.setText(group.getGroupName());
            tvGroupId.setText(group.getGroupId());
            tvLastMsg.setText(group.getLastMessage());
            tvTime.setText(group.getTimestamp());

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), GroupActivity.class);
                intent.putExtra("groupId", group.getGroupId());
                intent.putExtra("groupName", group.getGroupName());
                itemView.getContext().startActivity(intent);
            });

            itemView.setOnLongClickListener(v -> {
                String[] options = {"View Group Authority", "Leave Group", "Purge Database History"};
                new AlertDialog.Builder(itemView.getContext())
                        .setTitle("Manage Group Network")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                new AlertDialog.Builder(itemView.getContext())
                                        .setTitle("Group Registry Authority")
                                        .setMessage("Group Name: " + group.getGroupName() + "\nID: " + group.getGroupId() + "\nAuthority Status: Decentralized Sync Active")
                                        .setPositiveButton("OK", null)
                                        .show();
                            } else if (which == 1) {
                                Toast.makeText(itemView.getContext(), "Left Group", Toast.LENGTH_SHORT).show();
                            } else if (which == 2) {
                                PigeonDatabaseHelper dbHelper = new PigeonDatabaseHelper(itemView.getContext());
                                dbHelper.clearHistory(group.getGroupId());
                                Toast.makeText(itemView.getContext(), "Local group database history purged", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
                return true;
            });
        }
    }
}
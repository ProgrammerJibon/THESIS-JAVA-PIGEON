package bd.jibon.apps.pigeon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BlockedUserAdapter extends RecyclerView.Adapter<BlockedUserAdapter.ViewHolder> {
    private final List<BlockedUser> blockedUsers;
    private final OnUnblockClickListener listener;

    public BlockedUserAdapter(List<BlockedUser> blockedUsers, OnUnblockClickListener listener) {
        this.blockedUsers = blockedUsers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BlockedUser user = blockedUsers.get(position);
        holder.tvUsername.setText(user.getUsername());
        holder.btnUnblock.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUnblockClick(user, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return blockedUsers.size();
    }

    public interface OnUnblockClickListener {
        void onUnblockClick(BlockedUser user, int position);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername;
        Button btnUnblock;

        ViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvBlockedUsername);
            btnUnblock = itemView.findViewById(R.id.btnUnblock);
        }
    }
}
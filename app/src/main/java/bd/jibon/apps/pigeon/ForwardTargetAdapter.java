package bd.jibon.apps.pigeon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ForwardTargetAdapter extends RecyclerView.Adapter<ForwardTargetAdapter.ViewHolder> {
    private List<ForwardTarget> fullList;
    private List<ForwardTarget> filteredList;
    private OnTargetSelectedListener listener;

    public ForwardTargetAdapter(OnTargetSelectedListener listener) {
        this.fullList = new ArrayList<>();
        this.filteredList = new ArrayList<>();
        this.listener = listener;
    }

    public void updateData(List<ForwardTarget> newTargets) {
        this.fullList.clear();
        this.fullList.addAll(newTargets);
        this.filteredList.clear();
        this.filteredList.addAll(newTargets);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (ForwardTarget target : fullList) {
                if (target.getName().toLowerCase().contains(lowerQuery) ||
                        target.getId().toLowerCase().contains(lowerQuery)) {
                    filteredList.add(target);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_forward_target, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ForwardTarget target = filteredList.get(position);
        holder.tvName.setText(target.getName());
        if (target.isGroup()) {
            holder.tvId.setVisibility(View.VISIBLE);
            holder.tvId.setText("Group ID: " + target.getId());
        } else {
            holder.tvId.setVisibility(View.GONE);
        }

        holder.btnSend.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSendClicked(target);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public interface OnTargetSelectedListener {
        void onSendClicked(ForwardTarget target);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvId;
        Button btnSend;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvTargetName);
            tvId = itemView.findViewById(R.id.tvTargetId);
            btnSend = itemView.findViewById(R.id.btnForwardSend);
        }
    }
}
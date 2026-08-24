package bd.jibon.apps.pigeon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECV = 2;

    private final List<Message> messages;
    private final boolean isGroup;

    public MessageAdapter(List<Message> messages, boolean isGroup) {
        this.messages = messages;
        this.isGroup = isGroup;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSent() ? VIEW_TYPE_SENT : VIEW_TYPE_RECV;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_send, parent, false);
            return new SentViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_recv, parent, false);
            return new RecvViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        if (getItemViewType(position) == VIEW_TYPE_SENT) {
            ((SentViewHolder) holder).bind(msg);
        } else {
            ((RecvViewHolder) holder).bind(msg, isGroup);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        TextView tvMeta;

        SentViewHolder(View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageSendText);
            tvMeta = itemView.findViewById(R.id.tvMessageSendMeta);
        }

        void bind(Message msg) {
            tvText.setText(msg.getText());
            tvMeta.setText(msg.getTimestamp() + " • Sent");
        }
    }

    static class RecvViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender;
        TextView tvText;
        TextView tvMeta;

        RecvViewHolder(View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSenderNickname);
            tvText = itemView.findViewById(R.id.tvMessageRecvText);
            tvMeta = itemView.findViewById(R.id.tvMessageRecvMeta);
        }

        void bind(Message msg, boolean isGroup) {
            if (isGroup) {
                tvSender.setVisibility(View.VISIBLE);
                tvSender.setText(msg.getSender());
            } else {
                tvSender.setVisibility(View.GONE);
            }
            tvText.setText(msg.getText());
            tvMeta.setText(msg.getTimestamp());
        }
    }
}

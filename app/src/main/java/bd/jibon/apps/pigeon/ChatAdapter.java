package bd.jibon.apps.pigeon;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<Chat> chats;

    public ChatAdapter(List<Chat> chats) {
        this.chats = chats;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        holder.bind(chat);
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername;
        TextView tvLastMsg;
        TextView tvTime;
        View viewActive;

        ChatViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvChatUsername);
            tvLastMsg = itemView.findViewById(R.id.tvChatLastMsg);
            tvTime = itemView.findViewById(R.id.tvChatDate);
            viewActive = itemView.findViewById(R.id.viewActiveIndicator);
        }

        void bind(Chat chat) {
            if (tvUsername != null) tvUsername.setText(chat.getUsername());
            if (tvLastMsg != null) tvLastMsg.setText(chat.getLastMessage());
            if (tvTime != null) tvTime.setText(chat.getTimestamp());
            if (viewActive != null) {
                viewActive.setVisibility(chat.isActive() ? View.VISIBLE : View.GONE);
            }

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), ChatActivity.class);
                intent.putExtra("username", chat.getUsername());
                itemView.getContext().startActivity(intent);
            });
        }
    }
}

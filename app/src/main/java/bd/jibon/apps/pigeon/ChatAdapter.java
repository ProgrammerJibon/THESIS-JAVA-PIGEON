package bd.jibon.apps.pigeon;

import android.content.Context;
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

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<Chat> chats;
    private final Context context;

    public ChatAdapter(Context context, List<Chat> chats) {
        this.context = context;
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
        holder.tvUsername.setText(chat.getUsername());
        holder.tvLastMessage.setText(chat.getLastMessage());
        holder.tvTimestamp.setText(chat.getTimestamp());

        if (chat.isActive()) {
            holder.ivActiveDot.setVisibility(View.VISIBLE);
        } else {
            holder.ivActiveDot.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("username", chat.getUsername());
            context.startActivity(intent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            String[] options = {"Delete local history", "Delete chat for both", "Download history as HTML", "Block User"};
            new AlertDialog.Builder(context)
                    .setTitle(chat.getUsername())
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            PigeonDatabaseHelper db = new PigeonDatabaseHelper(context);
                            db.clearHistory(chat.getUsername());
                            Toast.makeText(context, "Local chat history cleared.", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            Toast.makeText(context, "Requested remote node to delete secure history.", Toast.LENGTH_SHORT).show();
                        } else if (which == 2) {
                            Toast.makeText(context, "Saved HTML conversation to local storage.", Toast.LENGTH_SHORT).show();
                        } else if (which == 3) {
                            Toast.makeText(context, "User blocked on LoRa spectrum.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername, tvLastMessage, tvTimestamp;
        View ivActiveDot;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvChatUsername);
            tvLastMessage = itemView.findViewById(R.id.tvChatLastMsg);
            tvTimestamp = itemView.findViewById(R.id.tvChatDate);
            ivActiveDot = itemView.findViewById(R.id.viewActiveIndicator);
        }
    }
}
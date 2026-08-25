package bd.jibon.apps.pigeon;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private final List<Chat> chats;
    private final Context context;
    private final String myUsername;
    private PigeonService pigeonService;

    public ChatAdapter(Context context, List<Chat> chats, String myUsername) {
        this.context = context;
        this.chats = chats;
        this.myUsername = myUsername;
    }

    public void setPigeonService(PigeonService service) {
        this.pigeonService = service;
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

        if (chat.isBlockedByMe() || chat.isBlockedByPeer()) {
            holder.tvUsername.setTextColor(Color.RED);
        } else {
            holder.tvUsername.setTextColor(context.getResources().getColor(R.color.colorOnBackground, context.getTheme()));
        }

        if (chat.isActive()) {
            holder.ivActiveDot.setVisibility(View.VISIBLE);
        } else {
            holder.ivActiveDot.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("username", chat.getUsername());
            intent.putExtra("isBlockedByMe", chat.isBlockedByMe());
            intent.putExtra("isBlockedByPeer", chat.isBlockedByPeer());
            context.startActivity(intent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            String blockOption = chat.isBlockedByMe() ? "Unblock User" : "Block User";
            String[] options = {"Delete local history", "Delete chat for both", "Download history as HTML", blockOption};
            new AlertDialog.Builder(context)
                    .setTitle(chat.getUsername())
                    .setItems(options, (dialog, which) -> {
                        PigeonDatabaseHelper db = new PigeonDatabaseHelper(context);
                        if (which == 0) {
                            db.clearHistory(chat.getUsername());
                            Toast.makeText(context, "Local chat history cleared.", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            db.clearHistory(chat.getUsername());
                            if (pigeonService != null) {
                                try {
                                    JSONObject payload = new JSONObject();
                                    payload.put("event", "delete_chat_both");
                                    JSONObject data = new JSONObject();
                                    data.put("target", chat.getUsername());
                                    data.put("sender", myUsername);
                                    payload.put("data", data);
                                    pigeonService.sendMessage(payload.toString());
                                } catch (Exception ignored) {
                                }
                            }
                            Toast.makeText(context, "Requested remote node to delete secure history.", Toast.LENGTH_SHORT).show();
                        } else if (which == 2) {
                            downloadHtmlHistory(chat.getUsername(), db);
                        } else if (which == 3) {
                            if (pigeonService != null) {
                                try {
                                    JSONObject payload = new JSONObject();
                                    payload.put("event", chat.isBlockedByMe() ? "unblock_user" : "block_user");
                                    JSONObject data = new JSONObject();
                                    data.put("target", chat.getUsername());
                                    data.put("sender", myUsername);
                                    payload.put("data", data);
                                    pigeonService.sendMessage(payload.toString());
                                } catch (Exception ignored) {
                                }
                            }
                            Toast.makeText(context, chat.isBlockedByMe() ? "User unblocked." : "User blocked.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
            return true;
        });
    }

    private void downloadHtmlHistory(String peerUsername, PigeonDatabaseHelper db) {
        List<Message> msgs = db.getMessages(peerUsername);
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;'><h2>Encrypted Chat History: ").append(peerUsername).append("</h2><hr>");
        for (Message m : msgs) {
            html.append("<p><b>").append(m.getSender()).append("</b> (").append(m.getTimestamp()).append("): ");
            if (m.getType() == Message.TYPE_IMAGE) {
                html.append("[Encrypted Image Data]");
            } else {
                html.append(m.getText());
            }
            html.append("</p>");
        }
        html.append("</body></html>");

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "Pigeon_History_" + peerUsername + ".html");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/html");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream os = context.getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(html.toString().getBytes());
                    os.close();
                    Toast.makeText(context, "Saved HTML conversation to Downloads folder.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, "Failed to save history.", Toast.LENGTH_SHORT).show();
        }
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
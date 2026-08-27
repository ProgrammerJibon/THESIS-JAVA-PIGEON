package bd.jibon.apps.pigeon;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.io.OutputStream;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECV = 2;

    private final List<Message> messages;
    private final boolean isGroup;
    private MessageInteractionListener listener;

    private static void handleLongClick(Context context, Message msg, int position, MessageInteractionListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Message Options");

        String[] options;
        if (msg.getType() == Message.TYPE_IMAGE) {
            options = new String[]{"Delete for me", "Delete for both", "Forward", "Download Image"};
        } else {
            options = new String[]{"Delete for me", "Delete for both", "Forward"};
        }

        builder.setItems(options, (dialog, which) -> {
            if (which == 0 && listener != null) listener.onDeleteForMe(msg, position);
            else if (which == 1 && listener != null) listener.onDeleteForBoth(msg, position);
            else if (which == 2 && listener != null) listener.onForward(msg);
            else if (which == 3 && msg.getType() == Message.TYPE_IMAGE) {
                downloadImage(context, msg.getImageBase64());
            }
        });
        builder.show();
    }

    public MessageAdapter(List<Message> messages, boolean isGroup) {
        this.messages = messages;
        this.isGroup = isGroup;
    }

    private static void downloadImage(Context context, String base64) {
        try {
            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "Pigeon_Img_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream os = context.getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(decodedString);
                    os.close();
                    Toast.makeText(context, "Image saved to Downloads.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, "Failed to save image.", Toast.LENGTH_SHORT).show();
        }
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

    private static void setupLocationUI(LinearLayout layout, TextView tvHeader, TextView tvCoords, Button btnMap, Button btnCopy, Message msg, Context context, boolean isSent) {
        layout.setVisibility(View.VISIBLE);
        String rawText = msg.getText();
        String coords = (rawText != null) ? rawText.replace("GPS: ", "").trim() : "";

        if (isSent) {
            tvHeader.setText("You sent a location");
        } else {
            String sender = (msg.getSender() != null && !msg.getSender().isEmpty()) ? msg.getSender() : "User";
            tvHeader.setText(sender + " sent his location");
        }

        tvCoords.setText(coords);

        btnMap.setOnClickListener(v -> {
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + coords);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(mapIntent);
            } else {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + coords)));
            }
        });

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("Coordinates", coords);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Coordinates copied", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void setListener(MessageInteractionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        if (getItemViewType(position) == VIEW_TYPE_SENT) {
            ((SentViewHolder) holder).bind(msg, listener, position);
        } else {
            ((RecvViewHolder) holder).bind(msg, isGroup, listener, position);
        }
    }

    public interface MessageInteractionListener {
        void onDeleteForMe(Message msg, int position);
        void onDeleteForBoth(Message msg, int position);
        void onForward(Message msg);
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        TextView tvMeta;
        ImageView ivImage;
        LinearLayout layoutLocation;
        TextView tvLocHeader;
        TextView tvLocCoords;
        Button btnMap;
        Button btnCopy;

        SentViewHolder(View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageSendText);
            tvMeta = itemView.findViewById(R.id.tvMessageSendMeta);
            ivImage = itemView.findViewById(R.id.ivMessageSendImage);
            layoutLocation = itemView.findViewById(R.id.layoutLocationSend);
            tvLocHeader = itemView.findViewById(R.id.tvLocationHeaderSend);
            tvLocCoords = itemView.findViewById(R.id.tvLocationCoordsSend);
            btnMap = itemView.findViewById(R.id.btnMessageSendMap);
            btnCopy = itemView.findViewById(R.id.btnMessageSendCopy);
        }

        void bind(Message msg, MessageInteractionListener listener, int position) {
            // SHOW DELIVERED OR SENT
            tvMeta.setText(msg.getTimestamp() + (msg.isDelivered() ? " • Delivered" : " • Sent"));
            
            ivImage.setVisibility(View.GONE);
            tvText.setVisibility(View.GONE);
            layoutLocation.setVisibility(View.GONE);

            if (msg.getType() == Message.TYPE_IMAGE) {
                ivImage.setVisibility(View.VISIBLE);
                try {
                    String base64Str = msg.getImageBase64();
                    if (base64Str != null) {
                        byte[] decodedString = Base64.decode(base64Str, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        ivImage.setImageBitmap(decodedByte);
                    }
                } catch (Exception ignored) {
                }
            } else if (msg.getType() == Message.TYPE_LOCATION) {
                setupLocationUI(layoutLocation, tvLocHeader, tvLocCoords, btnMap, btnCopy, msg, itemView.getContext(), true);
            } else {
                tvText.setVisibility(View.VISIBLE);
                tvText.setText(msg.getText());
            }

            itemView.setOnLongClickListener(v -> {
                handleLongClick(itemView.getContext(), msg, position, listener);
                return true;
            });
        }
    }

    static class RecvViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender;
        TextView tvText;
        TextView tvMeta;
        ImageView ivImage;
        LinearLayout layoutLocation;
        TextView tvLocHeader;
        TextView tvLocCoords;
        Button btnMap;
        Button btnCopy;

        RecvViewHolder(View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSenderNickname);
            tvText = itemView.findViewById(R.id.tvMessageRecvText);
            tvMeta = itemView.findViewById(R.id.tvMessageRecvMeta);
            ivImage = itemView.findViewById(R.id.ivMessageRecvImage);
            layoutLocation = itemView.findViewById(R.id.layoutLocationRecv);
            tvLocHeader = itemView.findViewById(R.id.tvLocationHeaderRecv);
            tvLocCoords = itemView.findViewById(R.id.tvLocationCoordsRecv);
            btnMap = itemView.findViewById(R.id.btnMessageRecvMap);
            btnCopy = itemView.findViewById(R.id.btnMessageRecvCopy);
        }

        void bind(Message msg, boolean isGroup, MessageInteractionListener listener, int position) {
            if (isGroup) {
                tvSender.setVisibility(View.VISIBLE);
                tvSender.setText(msg.getSender());
            } else {
                tvSender.setVisibility(View.GONE);
            }
            tvMeta.setText(msg.getTimestamp());

            ivImage.setVisibility(View.GONE);
            tvText.setVisibility(View.GONE);
            layoutLocation.setVisibility(View.GONE);

            if (msg.getType() == Message.TYPE_IMAGE) {
                ivImage.setVisibility(View.VISIBLE);
                try {
                    String base64Str = msg.getImageBase64();
                    if (base64Str != null) {
                        byte[] decodedString = Base64.decode(base64Str, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        ivImage.setImageBitmap(decodedByte);
                    }
                } catch (Exception ignored) {
                }
            } else if (msg.getType() == Message.TYPE_LOCATION) {
                setupLocationUI(layoutLocation, tvLocHeader, tvLocCoords, btnMap, btnCopy, msg, itemView.getContext(), false);
            } else {
                tvText.setVisibility(View.VISIBLE);
                tvText.setText(msg.getText());
            }

            itemView.setOnLongClickListener(v -> {
                handleLongClick(itemView.getContext(), msg, position, listener);
                return true;
            });
        }
    }
}
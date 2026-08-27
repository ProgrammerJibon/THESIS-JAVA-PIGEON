package bd.jibon.apps.pigeon;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class PigeonDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "pigeon.db";
    private static final int DATABASE_VERSION = 1;

    public PigeonDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "session_id TEXT," +
                "sender TEXT," +
                "receiver TEXT," +
                "text TEXT," +
                "timestamp TEXT," +
                "is_self INTEGER," +
                "type TEXT," +
                "is_delivered INTEGER" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS messages");
        onCreate(db);
    }

    public void insertMessage(String sessionId, String sender, String receiver, String text, String timestamp, boolean isSelf, String type, boolean isDelivered) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("sender", sender);
        values.put("receiver", receiver);
        values.put("text", text);
        values.put("timestamp", timestamp);
        values.put("is_self", isSelf ? 1 : 0);
        values.put("type", type);
        values.put("is_delivered", isDelivered ? 1 : 0);
        db.insert("messages", null, values);
    }

    public List<Message> getMessages(String sessionId) {
        List<Message> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM messages WHERE session_id = ? ORDER BY id ASC", new String[]{sessionId});
        if (cursor.moveToFirst()) {
            do {
                String sender = cursor.getString(cursor.getColumnIndexOrThrow("sender"));
                String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                String timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"));
                boolean isSelf = cursor.getInt(cursor.getColumnIndexOrThrow("is_self")) == 1;
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                boolean isDelivered = cursor.getInt(cursor.getColumnIndexOrThrow("is_delivered")) == 1;

                Message m;
                if ("image".equals(type)) {
                    m = new Message(sender, text, timestamp, isSelf, Message.TYPE_IMAGE);
                } else if ("location".equals(type)) {
                    m = new Message(sender, text, timestamp, isSelf, Message.TYPE_LOCATION);
                } else {
                    m = new Message(sender, text, timestamp, isSelf);
                }
                m.setDelivered(isDelivered);
                list.add(m);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public void markAllMessagesDelivered(String sessionId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_delivered", 1);
        db.update("messages", values, "session_id = ? AND is_self = 1", new String[]{sessionId});
    }

    public String getLastMessageText(String sessionId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT text, type FROM messages WHERE session_id = ? ORDER BY id DESC LIMIT 1", new String[]{sessionId});
        String res = "";
        if (cursor.moveToFirst()) {
            String type = cursor.getString(1);
            if ("image".equals(type)) {
                res = "[Image]";
            } else if ("location".equals(type)) {
                res = "[Location] " + cursor.getString(0);
            } else {
                res = cursor.getString(0);
            }
        }
        cursor.close();
        return res;
    }

    public String getLastMessageTime(String sessionId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT timestamp FROM messages WHERE session_id = ? ORDER BY id DESC LIMIT 1", new String[]{sessionId});
        String res = "";
        if (cursor.moveToFirst()) {
            res = cursor.getString(0);
        }
        cursor.close();
        return res;
    }

    public int getLastMessageId(String sessionId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM messages WHERE session_id = ? ORDER BY id DESC LIMIT 1", new String[]{sessionId});
        int res = 0;
        if (cursor.moveToFirst()) {
            res = cursor.getInt(0);
        }
        cursor.close();
        return res;
    }

    public int getSessionCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(DISTINCT session_id) FROM messages", null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    public void clearHistory(String sessionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "session_id = ?", new String[]{sessionId});
    }

    public void deleteMessage(String sessionId, String timestamp, String text) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "session_id = ? AND timestamp = ? AND text = ?", new String[]{sessionId, timestamp, text});
    }
}
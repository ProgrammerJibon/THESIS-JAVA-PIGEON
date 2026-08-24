package bd.jibon.apps.pigeon;

public class Chat {
    private String username;
    private String lastMessage;
    private String timestamp;
    private boolean isActive;

    public Chat(String username, String lastMessage, String timestamp, boolean isActive) {
        this.username = username;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.isActive = isActive;
    }

    public String getUsername() {
        return username;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isActive() {
        return isActive;
    }
}

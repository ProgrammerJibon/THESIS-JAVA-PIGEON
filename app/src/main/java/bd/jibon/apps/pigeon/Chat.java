package bd.jibon.apps.pigeon;

public class Chat {
    private String username;
    private String lastMessage;
    private String timestamp;
    private boolean isActive;
    private boolean isBlockedByMe;
    private boolean isBlockedByPeer;

    public Chat(String username, String lastMessage, String timestamp, boolean isActive, boolean isBlockedByMe, boolean isBlockedByPeer) {
        this.username = username;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.isActive = isActive;
        this.isBlockedByMe = isBlockedByMe;
        this.isBlockedByPeer = isBlockedByPeer;
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

    public boolean isBlockedByMe() {
        return isBlockedByMe;
    }

    public boolean isBlockedByPeer() {
        return isBlockedByPeer;
    }
}
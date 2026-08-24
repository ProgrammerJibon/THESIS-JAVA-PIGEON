package bd.jibon.apps.pigeon;

public class Group {
    private String groupName;
    private String groupId;
    private String lastMessage;
    private String timestamp;
    private int activeCount;

    public Group(String groupName, String groupId, String lastMessage, String timestamp, int activeCount) {
        this.groupName = groupName;
        this.groupId = groupId;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.activeCount = activeCount;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getActiveCount() {
        return activeCount;
    }
}

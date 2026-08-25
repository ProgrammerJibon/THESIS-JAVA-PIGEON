package bd.jibon.apps.pigeon;

public class ForwardTarget {
    private String name;
    private String id;
    private boolean isGroup;

    public ForwardTarget(String name, String id, boolean isGroup) {
        this.name = name;
        this.id = id;
        this.isGroup = isGroup;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public boolean isGroup() {
        return isGroup;
    }
}
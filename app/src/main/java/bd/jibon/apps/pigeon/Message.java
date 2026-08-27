package bd.jibon.apps.pigeon;

public class Message {
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_LOCATION = 2;

    private String sender;
    private String text;
    private String timestamp;
    private boolean isSent;
    private int type;
    private String imageBase64;
    private double latitude;
    private double longitude;
    private boolean isDelivered;

    public Message(String sender, String text, String timestamp, boolean isSent) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.isSent = isSent;
        this.type = TYPE_TEXT;
    }

    public Message(String sender, String payload, String timestamp, boolean isSent, int type) {
        this.sender = sender;
        this.timestamp = timestamp;
        this.isSent = isSent;
        this.type = type;
        if (type == TYPE_IMAGE) {
            this.imageBase64 = payload;
        } else {
            this.text = payload;
        }
    }

    public Message(String sender, double latitude, double longitude, String timestamp, boolean isSent) {
        this.sender = sender;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.isSent = isSent;
        this.type = TYPE_LOCATION;
        this.text = "GPS: " + latitude + ", " + longitude;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isSent() {
        return isSent;
    }

    public int getType() {
        return type;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    public void setDelivered(boolean delivered) {
        isDelivered = delivered;
    }
}
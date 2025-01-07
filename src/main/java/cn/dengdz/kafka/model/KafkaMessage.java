package cn.dengdz.kafka.model;

public class KafkaMessage {
    private long offset;
    private String key;
    private String value;
    private long timestamp;
    private int partition;

    public KafkaMessage(long offset, String key, String value, long timestamp, int partition) {
        this.offset = offset;
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
        this.partition = partition;
    }

    // Getters and Setters
    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getPartition() {
        return partition;
    }

    public void setPartition(int partition) {
        this.partition = partition;
    }
} 
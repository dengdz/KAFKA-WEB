package cn.dengdz.kafka.model;

public class KafkaDataSource {
    private Long id;
    private String name;
    private String bootstrapServers;
    private boolean connected;
    private String coreSitePath;
    private String hbaseSitePath;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getCoreSitePath() {
        return coreSitePath;
    }

    public void setCoreSitePath(String coreSitePath) {
        this.coreSitePath = coreSitePath;
    }

    public String getHbaseSitePath() {
        return hbaseSitePath;
    }

    public void setHbaseSitePath(String hbaseSitePath) {
        this.hbaseSitePath = hbaseSitePath;
    }
} 
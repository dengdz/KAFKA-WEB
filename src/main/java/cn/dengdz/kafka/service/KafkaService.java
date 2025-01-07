package cn.dengdz.kafka.service;

import cn.dengdz.kafka.model.KafkaDataSource;
import cn.dengdz.kafka.model.KafkaMessage;
import java.util.List;
import java.util.Set;

public interface KafkaService {
    // 数据源管理
    List<KafkaDataSource> getAllDataSources();
    KafkaDataSource addDataSource(KafkaDataSource dataSource);
    void deleteDataSource(Long id);
    
    // Topic操作
    Set<String> getTopics(Long dataSourceId);
    
    // 消息操作
    List<KafkaMessage> getLatestMessages(Long dataSourceId, String topic, int limit);
    void sendMessage(Long dataSourceId, String topic, Integer partition, String key, String value);
    
    void testConnection(KafkaDataSource dataSource);
    
    void createTopic(Long dataSourceId, String topicName, int numPartitions, int replicationFactor);
    void deleteTopic(Long dataSourceId, String topicName);
} 
package cn.dengdz.kafka.service.impl;

import cn.dengdz.kafka.model.KafkaDataSource;
import cn.dengdz.kafka.model.KafkaMessage;
import cn.dengdz.kafka.service.KafkaService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

@Service
public class KafkaServiceImpl implements KafkaService {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaServiceImpl.class);
    
    private final Map<Long, KafkaDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<Long, KafkaProducer<String, String>> producers = new ConcurrentHashMap<>();
    private final Map<Long, KafkaConsumer<String, String>> consumers = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    public List<KafkaDataSource> getAllDataSources() {
        return new ArrayList<>(dataSources.values());
    }

    @Override
    public KafkaDataSource addDataSource(KafkaDataSource dataSource) {
        log.info("Adding new Kafka data source: {}", dataSource.getBootstrapServers());
        dataSource.setId(idGenerator.incrementAndGet());
        
        // 验证连接是否可用
        try {
            getOrCreateConsumer(dataSource);
            getOrCreateProducer(dataSource);
            dataSource.setConnected(true);
        } catch (Exception e) {
            log.error("Failed to connect to Kafka: {}", e.getMessage());
            throw new RuntimeException("无法连接到 Kafka 服务器，请检查地址是否正确");
        }
        
        dataSources.put(dataSource.getId(), dataSource);
        return dataSource;
    }

    @Override
    public void deleteDataSource(Long id) {
        log.info("Deleting Kafka data source with id: {}", id);
        KafkaDataSource removed = dataSources.remove(id);
        if (removed != null) {
            closeConnections(id);
        }
    }

    @Override
    public Set<String> getTopics(Long dataSourceId) {
        log.info("Getting topics for data source: {}", dataSourceId);
        KafkaDataSource dataSource = getDataSourceOrThrow(dataSourceId);
        KafkaConsumer<String, String> consumer = getOrCreateConsumer(dataSource);
        return new TreeSet<>(consumer.listTopics().keySet());
    }

    @Override
    public List<KafkaMessage> getLatestMessages(Long dataSourceId, String topic, int limit) {
        log.info("Getting latest {} messages from topic {} for data source {}", limit, topic, dataSourceId);
        KafkaDataSource dataSource = getDataSourceOrThrow(dataSourceId);
        KafkaConsumer<String, String> consumer = getOrCreateConsumer(dataSource);
        
        List<TopicPartition> partitions = consumer.partitionsFor(topic)
            .stream()
            .map(p -> new TopicPartition(topic, p.partition()))
            .collect(Collectors.toList());
        
        consumer.assign(partitions);
        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
        
        for (TopicPartition partition : partitions) {
            long endOffset = endOffsets.get(partition);
            long startOffset = Math.max(0, endOffset - limit);
            consumer.seek(partition, startOffset);
        }
        
        List<KafkaMessage> messages = new ArrayList<>();
        int remainingLimit = limit;
        
        while (remainingLimit > 0) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            if (records.isEmpty()) break;
            
            for (ConsumerRecord<String, String> record : records) {
                messages.add(new KafkaMessage(
                    record.offset(),
                    record.key(),
                    record.value(),
                    record.timestamp(),
                    record.partition()
                ));
                if (--remainingLimit <= 0) break;
            }
        }
        
        messages.sort((m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));
        
        return messages;
    }

    @Override
    public void sendMessage(Long dataSourceId, String topic, Integer partition, String key, String value) {
        log.info("Sending message to topic {} for data source {}", topic, dataSourceId);
        KafkaDataSource dataSource = getDataSourceOrThrow(dataSourceId);
        KafkaProducer<String, String> producer = getOrCreateProducer(dataSource);
        
        ProducerRecord<String, String> record;
        if (partition != null) {
            record = new ProducerRecord<>(topic, partition, key, value);
        } else {
            record = new ProducerRecord<>(topic, key, value);
        }
        producer.send(record);
        producer.flush();
    }

    @Override
    public void testConnection(KafkaDataSource dataSource) {
        try {
            // 使用现有的方法来测试连接
            KafkaProducer<String, String> producer = getOrCreateProducer(dataSource);
            KafkaConsumer<String, String> consumer = getOrCreateConsumer(dataSource);
            
            // 关闭连接
            producer.close();
            consumer.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Kafka: " + e.getMessage());
        }
    }

    @Override
    public void createTopic(Long dataSourceId, String topicName, 
            int numPartitions, int replicationFactor) {
        KafkaDataSource dataSource = getDataSourceOrThrow(dataSourceId);
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
            dataSource.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic newTopic = new NewTopic(topicName, numPartitions, 
                (short) replicationFactor);
            adminClient.createTopics(Collections.singleton(newTopic))
                .all().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create topic: " + e.getMessage());
        }
    }

    @Override
    public void deleteTopic(Long dataSourceId, String topicName) {
        KafkaDataSource dataSource = getDataSourceOrThrow(dataSourceId);
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
            dataSource.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(props)) {
            adminClient.deleteTopics(Collections.singleton(topicName))
                .all().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete topic: " + e.getMessage());
        }
    }

    private KafkaDataSource getDataSourceOrThrow(Long id) {
        KafkaDataSource dataSource = dataSources.get(id);
        if (dataSource == null) {
            throw new RuntimeException("Data source not found: " + id);
        }
        return dataSource;
    }

    private KafkaProducer<String, String> getOrCreateProducer(KafkaDataSource dataSource) {
        // 如果是测试连接，不要缓存生产者
        if (dataSource.getId() == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, dataSource.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringSerializer");
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringSerializer");
            return new KafkaProducer<>(props);
        }
        
        return producers.computeIfAbsent(dataSource.getId(), id -> {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, dataSource.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringSerializer");
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringSerializer");
            return new KafkaProducer<>(props);
        });
    }

    private KafkaConsumer<String, String> getOrCreateConsumer(KafkaDataSource dataSource) {
        // 如果是测试连接，不要缓存消费者
        if (dataSource.getId() == null) {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, dataSource.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-tool-test-group");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return new KafkaConsumer<>(props);
        }
        
        return consumers.computeIfAbsent(dataSource.getId(), id -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, dataSource.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-tool-group-" + id);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringDeserializer");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return new KafkaConsumer<>(props);
        });
    }

    private void closeConnections(Long dataSourceId) {
        KafkaProducer<String, String> producer = producers.remove(dataSourceId);
        if (producer != null) {
            producer.close();
        }
        
        KafkaConsumer<String, String> consumer = consumers.remove(dataSourceId);
        if (consumer != null) {
            consumer.close();
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up Kafka connections");
        producers.values().forEach(KafkaProducer::close);
        consumers.values().forEach(KafkaConsumer::close);
    }
} 
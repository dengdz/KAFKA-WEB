package cn.dengdz.kafka.controller;

import cn.dengdz.kafka.model.KafkaDataSource;
import cn.dengdz.kafka.model.KafkaMessage;
import cn.dengdz.kafka.service.KafkaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/kafka")
public class KafkaController {

    private static final Logger log = LoggerFactory.getLogger(KafkaController.class);

    private final KafkaService kafkaService;

    public KafkaController(KafkaService kafkaService) {
        this.kafkaService = kafkaService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("API error:", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMediaTypeError(HttpMediaTypeNotSupportedException e) {
        log.error("Media type error:", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", "不支持的内容类型，请使用 application/json");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.error("Invalid argument:", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @GetMapping("/datasource")
    public ResponseEntity<List<KafkaDataSource>> getAllDataSources() {
        log.info("Fetching all Kafka data sources");
        try {
            List<KafkaDataSource> dataSources = kafkaService.getAllDataSources();
            return ResponseEntity.ok(dataSources);
        } catch (Exception e) {
            log.error("Failed to fetch data sources", e);
            throw e;
        }
    }

    @PostMapping("/datasource")
    public ResponseEntity<KafkaDataSource> addDataSource(@RequestBody KafkaDataSource dataSource) {
        log.info("Adding new Kafka data source: {}", dataSource.getBootstrapServers());
        try {
            KafkaDataSource saved = kafkaService.addDataSource(dataSource);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Failed to add data source", e);
            throw e;
        }
    }

    @PostMapping("/datasource/test")
    public ResponseEntity<Void> testConnection(
            @RequestParam("name") String name,
            @RequestParam("bootstrapServers") String bootstrapServers,
            @RequestPart(value = "coreSite", required = false) MultipartFile coreSiteFile,
            @RequestPart(value = "hbaseSite", required = false) MultipartFile hbaseSiteFile) throws IOException {
        log.info("Testing connection for: {}", name);
        try {
            KafkaDataSource dataSource = new KafkaDataSource();
            dataSource.setName(name);
            dataSource.setBootstrapServers(bootstrapServers);
            
            // 处理配置文件
            if (coreSiteFile != null && !coreSiteFile.isEmpty()) {
                String coreSitePath = saveConfigFile(coreSiteFile, "core-site.xml");
                dataSource.setCoreSitePath(coreSitePath);
            }
            
            if (hbaseSiteFile != null && !hbaseSiteFile.isEmpty()) {
                String hbaseSitePath = saveConfigFile(hbaseSiteFile, "hbase-site.xml");
                dataSource.setHbaseSitePath(hbaseSitePath);
            }
            
            kafkaService.testConnection(dataSource);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Connection test failed", e);
            throw e;
        }
    }

    private String saveConfigFile(MultipartFile file, String fileName) throws IOException {
        String uploadDir = "config/";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String filePath = uploadDir + UUID.randomUUID() + "-" + fileName;
        File dest = new File(filePath);
        file.transferTo(dest);
        return filePath;
    }

    @DeleteMapping("/datasource/{id}")
    public ResponseEntity<Void> deleteDataSource(@PathVariable Long id) {
        log.info("Deleting Kafka data source with id: {}", id);
        try {
            kafkaService.deleteDataSource(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete data source", e);
            throw e;
        }
    }

    @GetMapping("/datasource/{id}/topics")
    public ResponseEntity<Set<String>> getTopics(@PathVariable Long id) {
        log.info("Fetching topics for data source id: {}", id);
        try {
            Set<String> topics = kafkaService.getTopics(id);
            return ResponseEntity.ok(topics);
        } catch (Exception e) {
            log.error("Failed to fetch topics", e);
            throw e;
        }
    }

    @GetMapping("/datasource/{id}/topics/{topic}/messages")
    public ResponseEntity<List<KafkaMessage>> getMessages(
            @PathVariable Long id,
            @PathVariable String topic,
            @RequestParam(defaultValue = "100") int limit) {
        log.info("Fetching messages for data source id: {}, topic: {}, limit: {}", id, topic, limit);
        try {
            List<KafkaMessage> messages = kafkaService.getLatestMessages(id, topic, limit);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Failed to fetch messages", e);
            throw e;
        }
    }

    static class KafkaMessageRequest {
        private Integer partition;
        private String key;
        private String value;

        // Getters and Setters
        public Integer getPartition() { return partition; }
        public void setPartition(Integer partition) { this.partition = partition; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    @PostMapping("/datasource/{id}/topics/{topic}/messages")
    public ResponseEntity<Void> sendMessage(
            @PathVariable Long id,
            @PathVariable String topic,
            @RequestBody KafkaMessageRequest message) {
        log.info("Sending message to data source id: {}, topic: {}", id, topic);
        try {
            if (message.getValue() == null || message.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("消息内容不能为空");
            }
            kafkaService.sendMessage(id, topic, message.getPartition(), message.getKey(), message.getValue());
            log.info("Successfully sent message");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to send message. Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    static class CreateTopicRequest {
        private String name;
        private int numPartitions;
        private int replicationFactor;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getNumPartitions() { return numPartitions; }
        public void setNumPartitions(int numPartitions) { this.numPartitions = numPartitions; }
        public int getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }
    }

    @PostMapping("/datasource/{id}/topics")
    public ResponseEntity<Void> createTopic(
            @PathVariable Long id,
            @RequestBody CreateTopicRequest request) {
        log.info("Creating topic {} for data source {}", request.getName(), id);
        try {
            kafkaService.createTopic(id, request.getName(), 
                request.getNumPartitions(), request.getReplicationFactor());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to create topic", e);
            throw e;
        }
    }

    @DeleteMapping("/datasource/{id}/topics/{topic}")
    public ResponseEntity<Void> deleteTopic(
            @PathVariable Long id,
            @PathVariable String topic) {
        log.info("Deleting topic {} from data source {}", topic, id);
        try {
            kafkaService.deleteTopic(id, topic);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete topic", e);
            throw e;
        }
    }
} 
package com.mycompany;

// Flink Core & Streaming
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.DataTypes; // For explicit schema definition
import org.apache.flink.configuration.Configuration; // Flink Configuration
import org.apache.flink.configuration.RestOptions;   // For Web UI port
import org.apache.flink.configuration.WebOptions;    // For enabling Web UI submission

// Flink Kafka Connector
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;

// Jackson JSON Parsing
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Java Utilities
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// No java.sql.Timestamp needed in this version of POJO

public class StreamingJob {

    // --- Configuration ---
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String KAFKA_TOPIC = "iot-events";
    private static final String KAFKA_GROUP_ID = "iceberg-flink-consumer-v3"; // Changed group ID slightly

    private static final String ICEBERG_CATALOG_NAME_FOR_FLINK_TABLE_API = "my_iceberg_catalog";
    private static final String JDBC_CATALOG_URI = "jdbc:postgresql://localhost:5432/metastore";
    private static final String JDBC_USER = "hive"; 
    private static final String JDBC_PASSWORD = "hivepassword"; 
    private static final String JDBC_WAREHOUSE_PATH = "s3a://iceberg-lake/warehouse";

    private static final String MINIO_ENDPOINT = "http://localhost:9000";
    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minio_secret_password";

    private static final String TARGET_TABLE_NAMESPACE = "default";
    private static final String TARGET_TABLE_NAME = "iot_events_iceberg";
    // --- End Configuration ---

    public static void main(String[] args) throws Exception {
        // Create a Flink configuration object to explicitly enable/configure the Web UI
        Configuration flinkLocalConf = new Configuration();
        flinkLocalConf.setBoolean(WebOptions.SUBMIT_ENABLE, true); 
        flinkLocalConf.setString(RestOptions.BIND_PORT, "8081-8090"); 

        // Get the Flink execution environment WITH the Web UI explicitly enabled
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(flinkLocalConf);

        // === Enable Checkpointing ===
        env.enableCheckpointing(5000); // Checkpoint every 5000 milliseconds (5 seconds)
        // Optional: For more robust checkpointing settings:
        // org.apache.flink.streaming.api.environment.CheckpointConfig checkpointConfig = env.getCheckpointConfig();
        // checkpointConfig.setCheckpointingMode(org.apache.flink.streaming.api.CheckpointingMode.EXACTLY_ONCE);
        // checkpointConfig.setMinPauseBetweenCheckpoints(1000); 
        // checkpointConfig.setCheckpointTimeout(60000); 
        // checkpointConfig.setMaxConcurrentCheckpoints(1);
        // checkpointConfig.setExternalizedCheckpoints(org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // checkpointConfig.setTolerableCheckpointFailureNumber(3);


        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // Set Hadoop/S3a configurations for Flink globally
        org.apache.flink.configuration.Configuration flinkJobConf = tableEnv.getConfig().getConfiguration();
        flinkJobConf.setString("fs.s3a.endpoint", MINIO_ENDPOINT);
        flinkJobConf.setString("fs.s3a.access.key", MINIO_ACCESS_KEY);
        flinkJobConf.setString("fs.s3a.secret.key", MINIO_SECRET_KEY);
        flinkJobConf.setString("fs.s3a.path.style.access", "true");
        flinkJobConf.setString("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");

        // --- Kafka Source ---
        KafkaSource<String> source = KafkaSource.<String>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(KAFKA_TOPIC)
            .setGroupId(KAFKA_GROUP_ID)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
        DataStream<String> kafkaStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        // --- Transformation (JSON Parsing to POJO) ---
        final ObjectMapper mapper = new ObjectMapper();
        DataStream<IotEventPojo> pojoStream = kafkaStream.map(new MapFunction<String, IotEventPojo>() {
            @Override
            public IotEventPojo map(String jsonString) throws Exception {
                 try {
                    JsonNode node = mapper.readTree(jsonString);
                    IotEventPojo event = new IotEventPojo();
                    event.device_id = node.get("device_id").asText();
                    LocalDateTime ldt = LocalDateTime.parse(node.get("timestamp").asText(), DateTimeFormatter.ISO_DATE_TIME);
                    event.event_ts = ldt; 
                    event.temperature = node.get("temperature").asDouble();
                    event.humidity = node.get("humidity").asDouble();
                    event.record_id = node.get("record_id").asLong();
                    return event;
                } catch (Exception e) {
                    System.err.println("Failed to parse JSON: " + jsonString + " - Error: " + e.getMessage());
                    return null;
                }
            }
        }).filter(data -> data != null);

        // === Debug Print Statement (Alternative) ===
        // This map is just for printing and doesn't change the main pojoStream passed to fromDataStream
        pojoStream.map(new MapFunction<IotEventPojo, IotEventPojo>() {
            @Override
            public IotEventPojo map(IotEventPojo value) throws Exception {
                System.out.println(String.format("DEBUG POJO: ID=%s, TS=%s, Temp=%.1f, RecordID=%d",
                                     value.device_id,
                                     value.event_ts.toString(),
                                     value.temperature,
                                     value.record_id));
                return value; // Pass the original POJO through
            }
        }).setParallelism(1); 
        // === End Debug Print Statement ===

        // Convert DataStream<POJO> to Flink Table WITH EXPLICIT SCHEMA
        Table inputTable = tableEnv.fromDataStream(
            pojoStream, // Use the original pojoStream here, not the one from the debug print map
            org.apache.flink.table.api.Schema.newBuilder()
                .column("device_id", DataTypes.STRING())
                .column("event_ts", DataTypes.TIMESTAMP(6)) 
                .column("temperature", DataTypes.DOUBLE())
                .column("humidity", DataTypes.DOUBLE())
                .column("record_id", DataTypes.BIGINT())
                .build()
        );

        // --- Iceberg Sink using Table API ---
        String targetIcebergTablePath = String.format("`%s`.`%s`.`%s`",
            ICEBERG_CATALOG_NAME_FOR_FLINK_TABLE_API,
            TARGET_TABLE_NAMESPACE,
            TARGET_TABLE_NAME
        );

        // Create and Register the Iceberg JDBC Catalog
        String createCatalogDdl = String.format(
            "CREATE CATALOG %s WITH (" +
            "  'type'='iceberg'," +
            "  'catalog-impl'='org.apache.iceberg.jdbc.JdbcCatalog'," +
            "  'uri'='%s'," +
            "  'jdbc.user'='%s'," +
            "  'jdbc.password'='%s'," +
            "  'warehouse'='%s'," +
            "  'io-impl'='org.apache.iceberg.aws.s3.S3FileIO'," +
            "  's3.endpoint'='%s'," +
            "  's3.access-key-id'='%s'," +
            "  's3.secret-access-key'='%s'," +
            "  's3.path-style-access'='true'" +
            ")",
            ICEBERG_CATALOG_NAME_FOR_FLINK_TABLE_API,
            JDBC_CATALOG_URI,
            JDBC_USER,
            JDBC_PASSWORD,
            JDBC_WAREHOUSE_PATH,
            MINIO_ENDPOINT,
            MINIO_ACCESS_KEY,
            MINIO_SECRET_KEY
        );
        tableEnv.executeSql(createCatalogDdl);
        System.out.println("Iceberg Catalog registered with Flink TableEnvironment: " + ICEBERG_CATALOG_NAME_FOR_FLINK_TABLE_API);

        // Create the Iceberg table using Flink DDL if it doesn't exist
        String createTableDdl = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            "  device_id STRING," +
            "  event_ts TIMESTAMP(6)," +
            "  temperature DOUBLE," +
            "  humidity DOUBLE," +
            "  record_id BIGINT" +
            ") WITH ('format-version'='2')", 
            targetIcebergTablePath
        );
        tableEnv.executeSql(createTableDdl);
        System.out.println("Target Iceberg table ensured: " + targetIcebergTablePath);
        
        // Insert data from the Flink Table into the Iceberg table
        inputTable.executeInsert(targetIcebergTablePath).await();

        // env.execute("Kafka to Iceberg FOSS Project"); // Likely not needed
    }

    // POJO class for our IoT events
    public static class IotEventPojo {
        public String device_id;
        public LocalDateTime event_ts; 
        public Double temperature;
        public Double humidity;
        public Long record_id;

        public IotEventPojo() {}
    }
}
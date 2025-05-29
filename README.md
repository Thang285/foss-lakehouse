# FOSS Streaming Data Lakehouse with Iceberg, Flink, Kafka & MinIO

This project demonstrates an end-to-end streaming data pipeline built entirely with open-source software. It simulates IoT device data, ingests it via Kafka, processes it in real-time with Apache Flink, stores it in an Apache Iceberg table format on MinIO (S3-compatible storage) using a JDBC Catalog (PostgreSQL backend), and allows querying via Trino.

**Date:** May 2025
**Location:** Ho Chi Minh City, Vietnam

## Project Goal

To build a functional, local streaming data lakehouse, showcasing the integration and capabilities of:
- Apache Kafka for message queuing.
- Apache Flink for stream processing.
- Apache Iceberg as the open table format for reliability and performance on a data lake.
- MinIO for S3-compatible object storage.
- PostgreSQL as a backend for Iceberg's JDBC Catalog.
- Trino (formerly PrestoSQL) for SQL querying on the Iceberg tables.

## Core Technologies Used

* **Apache Iceberg:** Open table format for huge analytic datasets.
* **Apache Flink:** Stream processing framework.
* **Apache Kafka:** Distributed event streaming platform.
* **MinIO:** High-performance, S3-compatible object storage.
* **PostgreSQL:** Used as the metastore for Iceberg's JDBC Catalog.
* **Trino:** Distributed SQL query engine.
* **Docker & Docker Compose:** For containerizing and managing the services locally.
* **Java:** For the Flink application.
* **Python:** For the Kafka data producer.
* **Maven:** For building the Flink Java project.

## Project Architecture
## Prerequisites

* Docker
* Docker Compose
* Java (JDK 11 used for this project)
* Maven
* Python 3 (with `kafka-python` library)
* Git (for cloning, if applicable)
* Trino CLI (for querying)

## Setup Instructions

1.  **Clone the Repository (if applicable):**
    ```bash
    git clone [https://github.com/](https://github.com/)[your-github-username]/[your-repo-name].git
    cd [your-repo-name]
    ```

2.  **Configure Environment (Important!):**
    * The project uses default credentials for MinIO (`minioadmin`/`minio_secret_password`) and PostgreSQL (`hive`/`hivepassword`) within the Docker Compose setup and Java code. **For any real deployment or public sharing, these MUST be changed and managed securely (e.g., via environment variables or secrets management).**
    * Review `docker-compose.yml`, `StreamingJob.java` (configuration constants), and Trino configuration files (`trino_conf/`) for any host-specific paths or ports if you deviate from the provided setup.

3.  **Build the Flink Job:**
    Navigate to the Flink job's Maven project directory (e.g., `iceberg-flink-job/`) and build it:
    ```bash
    cd iceberg-flink-job
    mvn clean package
    cd .. 
    ```

4.  **Start Docker Services:**
    From the root project directory (where `docker-compose.yml` is located):
    ```bash
    docker-compose up -d
    ```
    This will start:
    * PostgreSQL (for Iceberg JDBC Catalog)
    * MinIO (S3-compatible storage)
    * Zookeeper
    * Kafka
    * Trino

5.  **Create MinIO Bucket:**
    * Access the MinIO console at `http://localhost:9001`.
    * Log in with `minioadmin` / `minio_secret_password`.
    * Create a bucket named `iceberg-lake`.

6.  **Start the Kafka Producer:**
    In a new terminal, from the root project directory:
    ```bash
    python producer.py
    ```
    This script will start sending simulated IoT data to the `iot-events` Kafka topic.

7.  **Run the Flink Streaming Job:**
    In another new terminal, from the `iceberg-flink-job` directory:
    ```bash
    mvn compile exec:java
    ```
    This will start the Flink job. It will:
    * Register the Iceberg JDBC catalog with Flink's TableEnvironment.
    * Create the Iceberg table `my_iceberg_catalog.default.iot_events_iceberg` if it doesn't exist.
    * Consume data from Kafka, parse it, and sink it into the Iceberg table.
    * Enable checkpointing every 5 seconds.
    * (Optionally) Print debug messages for processed POJOs.

8.  **Monitor (Optional but Recommended):**
    * **Flink Web UI:** Access `http://localhost:8081` to see the Flink dashboard, running jobs, and checkpoint status.
    * **MinIO Console:** Check `http://localhost:9001` to see data files appearing in the `iceberg-lake/warehouse/default/iot_events_iceberg/data/` directory.
    * **Docker Logs:** Use `docker-compose logs -f <service_name>` (e.g., `kafka`, `flink_job_manager` if you dockerize Flink, `trino`).

## Querying with Trino

1.  Ensure your Trino container is running (`docker-compose ps`).
2.  Download the Trino CLI JAR from [trino.io/download.html](https://trino.io/download.html) and place it somewhere convenient.
3.  Connect to Trino (from the directory where you have `trino-cli.jar`):
    ```bash
    java -jar trino-cli.jar --server http://localhost:8080 --catalog iceberg --schema "default"
    ```
    (Enter any username when prompted, e.g., `testuser`)

4.  Run SQL queries:
    ```sql
    SHOW TABLES;
    -- Expected: iot_events_iceberg

    DESCRIBE iot_events_iceberg;

    SELECT * FROM iot_events_iceberg LIMIT 10;

    SELECT device_id, AVG(temperature) 
    FROM iot_events_iceberg 
    GROUP BY device_id;

    SELECT COUNT(*) FROM iot_events_iceberg;
    ```

## Project Structure 

*(Note: The `hive_conf` directory for a standalone Hive Metastore is not used in the current version that uses Flink Table API to register the JDBC Catalog directly).*

## To Do / Future Enhancements

* Implement more complex Flink transformations or windowed aggregations.
* Add schema evolution examples for the Iceberg table.
* Showcase Iceberg time travel and rollback.
* Integrate with an open-source BI tool like Apache Superset.
* Dockerize the Flink application itself for easier deployment.
* Implement proper secret management.

## Troubleshooting Common Issues

* **`No FileSystem for scheme: s3a` (Flink/Trino):** Ensure `core-site.xml` is on the classpath for Flink (in `src/main/resources`) and that Trino's `iceberg.properties` and Flink's global/catalog configurations correctly point to MinIO with `s3a://` paths and include all necessary `fs.s3a.*` or `s3.*` properties. Ensure `hadoop-aws` and `aws-sdk-v2` JARs are correctly in Flink's/Trino's classpath (Maven handles this for Flink; Trino Iceberg connector bundles them).
* **Connection Refused (Kafka/Postgres/MinIO/Trino):** Check `docker-compose ps` to ensure all containers are running. Verify port mappings in `docker-compose.yml` and that you're using `localhost:<exposed_port>` from your host machine and `service_name:<internal_port>` for container-to-container communication.
* **Data Type Mismatches (Flink):** Ensure the schema defined in Flink `DataTypes` for `fromDataStream` matches the POJO and the target Iceberg table DDL created by Flink SQL.
* **Permissions (MinIO):** Ensure the MinIO credentials used have read/write access to the `iceberg-lake` bucket and its contents.

---

This README provides a good overview. Remember to replace placeholders and add any specific details about your project or learning journey!
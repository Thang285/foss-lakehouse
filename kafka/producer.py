import time
import json
import random
from kafka import KafkaProducer
import datetime

# Kafka configuration
bootstrap_servers = 'localhost:9092' # We use localhost:9092 because we run this script OUTSIDE Docker
topic_name = 'iot-events'

# Create Kafka Producer
producer = KafkaProducer(
    bootstrap_servers=bootstrap_servers,
    value_serializer=lambda v: json.dumps(v).encode('utf-8') # Serialize JSON to bytes
)

print(f"Starting to send data to Kafka topic: {topic_name}...")

device_counter = 0
try:
    while True:
        device_id = f"sensor_{random.randint(1, 5)}"
        timestamp = datetime.datetime.now().isoformat()
        temperature = round(random.uniform(15.0, 35.0), 2)
        humidity = round(random.uniform(40.0, 80.0), 2)

        message = {
            "device_id": device_id,
            "timestamp": timestamp,
            "temperature": temperature,
            "humidity": humidity,
            "record_id": device_counter
        }

        producer.send(topic_name, value=message)
        print(f"Sent: {message}")

        device_counter += 1
        time.sleep(1)  
 
except KeyboardInterrupt:
    print("\nStopping producer...")
finally:
    producer.flush()  
    producer.close()
    print("Producer closed.")
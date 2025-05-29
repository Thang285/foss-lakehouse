from kafka.admin import KafkaAdminClient, NewTopic
from kafka.errors import TopicAlreadyExistsError

bootstrap_servers = 'localhost:9092'
topic_name = 'iot-events'
partitions = 1
replication_factor = 1

try:
    admin_client = KafkaAdminClient(bootstrap_servers=bootstrap_servers)

    topic = NewTopic(name=topic_name, 
                     num_partitions=partitions, 
                     replication_factor=replication_factor)

    print(f"Attempting to create topic: {topic_name}...")
    admin_client.create_topics(new_topics=[topic], validate_only=False)
    print("Topic created successfully (or already existed).")

except TopicAlreadyExistsError:
    print(f"Topic '{topic_name}' already exists.")
except Exception as e:
    print(f"An error occurred: {e}")
finally:
    admin_client.close()
FROM confluentinc/cp-kafka-connect:7.4.1

# Install JDBC Sink/Source connector and Debezium PostgreSQL CDC Source connector
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:10.7.6 \
 && confluent-hub install --no-prompt debezium/debezium-connector-postgresql:2.4.2

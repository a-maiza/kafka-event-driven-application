package com.example.order.config;

import com.example.common.TopicNames;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("dev")
public class KafkaTopicConfig {

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(TopicNames.ORDERS)
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name(TopicNames.PAYMENTS)
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic inventoryTopic() {
        return TopicBuilder.name(TopicNames.INVENTORY)
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic orderStatusTopic() {
        return TopicBuilder.name(TopicNames.ORDER_STATUS)
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(TopicNames.DEAD_LETTER)
                .partitions(3)
                .replicas(3)
                .build();
    }
}

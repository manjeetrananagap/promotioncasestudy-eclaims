package com.nagarro.eclaims.notification.config;

import com.nagarro.eclaims.events.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * Kafka consumer configuration for Notification Service.
 *
 * Uses JsonDeserializer with trusted package to deserialise shared-events DTOs.
 * A single ConsumerFactory works for all event types because Jackson maps by field name.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>(Object.class);
        deserializer.addTrustedPackages("com.nagarro.eclaims.events");
        deserializer.setUseTypeHeaders(false);   // No __TypeId__ header — use target class

        return new DefaultKafkaConsumerFactory<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,           "notification-service-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false  // Manual commit via Spring
            ),
            new StringDeserializer(),
            deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Process one message at a time per partition — prevents out-of-order issues
        factory.setConcurrency(3);
        return factory;
    }
}

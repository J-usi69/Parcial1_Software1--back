package com.workflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Configuración de MongoDB.
 * Habilita auditoría para @CreatedDate y @LastModifiedDate y soporte de transacciones.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
    
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
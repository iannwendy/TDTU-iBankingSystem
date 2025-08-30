package com.ibanking.tuition.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    // Spring Boot already provides transaction management
    // Custom configuration can be added here if needed in the future
}

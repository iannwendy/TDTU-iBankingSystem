package com.ibanking.tuition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TuitionApplication {
    public static void main(String[] args) {
        SpringApplication.run(TuitionApplication.class, args);
    }
}



package com.healthcare.interop.mapping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MappingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MappingServiceApplication.class, args);
    }
}

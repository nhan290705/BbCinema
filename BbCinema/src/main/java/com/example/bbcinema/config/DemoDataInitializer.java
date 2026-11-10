package com.example.bbcinema.config;

import com.example.bbcinema.service.DemoDataService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "bbcinema.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer {
    @Bean
    public ApplicationRunner initializeDemoData(DemoDataService demoData) {
        return args -> demoData.seed();
    }
}

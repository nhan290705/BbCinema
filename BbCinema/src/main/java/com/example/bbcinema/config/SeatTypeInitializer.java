package com.example.bbcinema.config;

import com.example.bbcinema.model.cinema.SeatType;
import com.example.bbcinema.repository.SeatTypeRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class SeatTypeInitializer {
    @Bean
    public ApplicationRunner initializeSeatTypes(SeatTypeRepository types) {
        return args -> {
            for (String name : new String[]{"STANDARD", "VIP", "COUPLE"}) {
                if (types.findByTypeNameIgnoreCase(name).isEmpty()) {
                    SeatType type = new SeatType();
                    type.setTypeName(name);
                    type.setSeatSpan(name.equals("COUPLE") ? 2 : 1);
                    type.setDefaultSurcharge(BigDecimal.ZERO);
                    types.save(type);
                }
            }
        };
    }
}

package com.example.bbcinema.model.cinema;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "provinces")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Province {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "province_name", nullable = false, length = 100)
    private String provinceName;

    @Column(unique = true, length = 20)
    private String code;
}
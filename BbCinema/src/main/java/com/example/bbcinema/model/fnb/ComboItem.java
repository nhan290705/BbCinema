package com.example.bbcinema.model.fnb;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "combo_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComboItem {
    @EmbeddedId
    private ComboItemId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("comboProductId")
    @JoinColumn(name = "combo_product_id")
    private Product comboProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("childProductId")
    @JoinColumn(name = "child_product_id")
    private Product childProduct;

    @Column(nullable = false)
    private Integer quantity;
}
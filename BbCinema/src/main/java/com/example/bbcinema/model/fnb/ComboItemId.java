package com.example.bbcinema.model.fnb;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ComboItemId implements Serializable {
    private Long comboProductId;
    private Long childProductId;
}

package iuh.fit.cartservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductVariantResponse(
        UUID id,
        BigDecimal price,
        Integer stockQuantity,
        Boolean isActive
) {
}

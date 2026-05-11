package iuh.fit.cartservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AddCartItemRequest(
        UUID productVariantId,
        Integer quantity,
        BigDecimal unitPrice
) {
}

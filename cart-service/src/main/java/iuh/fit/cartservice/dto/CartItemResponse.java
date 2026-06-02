package iuh.fit.cartservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productVariantId,
        Integer quantity,
        BigDecimal unitPrice,
        LocalDateTime addedAt
) {
}

package iuh.fit.cartservice.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        UUID customerId,
        LocalDateTime updatedAt,
        List<CartItemResponse> items
) {
}

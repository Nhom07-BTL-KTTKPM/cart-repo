package iuh.fit.cartservice.service;

import iuh.fit.cartservice.dto.CartResponse;

public record CartOperationResult(
        CartResponse cart,
        boolean created
) {
}

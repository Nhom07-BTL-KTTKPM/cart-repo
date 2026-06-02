package iuh.fit.cartservice.controller;

import iuh.fit.cartservice.dto.AddCartItemRequest;
import iuh.fit.cartservice.dto.CartResponse;
import iuh.fit.cartservice.dto.CreateCartRequest;
import iuh.fit.cartservice.dto.UpdateCartItemRequest;
import iuh.fit.cartservice.service.CartOperationResult;
import iuh.fit.cartservice.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<CartResponse> createCart(@RequestBody CreateCartRequest request) {
        CartOperationResult result = cartService.createOrGetCart(request.customerId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.cart());
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable String customerId) {
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<CartResponse> getCartByAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(cartService.getCartByAccountId(accountId));
    }

    @PostMapping("/{customerId}/items")
    public ResponseEntity<CartResponse> addItem(
            @PathVariable String customerId,
            @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.addItem(customerId, request));
    }

    @PutMapping("/{customerId}/items/{itemId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable String customerId,
            @PathVariable String itemId,
            @RequestBody UpdateCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.updateItemQuantity(customerId, itemId, request));
    }

    @DeleteMapping("/{customerId}/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable String customerId,
            @PathVariable String itemId
    ) {
        return ResponseEntity.ok(cartService.removeItem(customerId, itemId));
    }

    @DeleteMapping("/{customerId}/items/bulk")
    public ResponseEntity<CartResponse> removeItems(
            @PathVariable String customerId,
            @RequestParam("itemIds") java.util.List<String> itemIds
    ) {
        return ResponseEntity.ok(cartService.removeItems(customerId, itemIds));
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> clearCart(@PathVariable String customerId) {
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{customerId}/backup")
    public ResponseEntity<CartResponse> backupCart(@PathVariable String customerId) {
        return ResponseEntity.ok(cartService.backupCartFromCache(customerId));
    }
}

package iuh.fit.cartservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.cartservice.dto.CartItemResponse;
import iuh.fit.cartservice.dto.CartResponse;
import iuh.fit.cartservice.entity.CartEntity;
import iuh.fit.cartservice.entity.CartItemEntity;
import iuh.fit.cartservice.repo.CartRepository;
import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartService {

    private static final String CART_KEY_PREFIX = "cart:";

    private final CartRepository cartRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CartService(CartRepository cartRepository,
                       StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper) {
        this.cartRepository = cartRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CartOperationResult createOrGetCart(String customerId) {
        UUID customerUuid = parseCustomerId(customerId);
        String redisKey = buildCartKey(customerUuid);

        CartResponse cached = readFromCache(redisKey);
        if (cached != null) {
            return new CartOperationResult(cached, false);
        }

        Optional<CartEntity> existing = cartRepository.findByCustomerId(customerUuid);
        if (existing.isPresent()) {
            CartResponse response = mapToResponse(existing.get());
            writeToCache(redisKey, response);
            return new CartOperationResult(response, false);
        }

        CartResponse created = new CartResponse(
                UUID.randomUUID(),
                customerUuid,
                LocalDateTime.now(),
                Collections.emptyList()
        );

        CartEntity entity = new CartEntity();
        entity.setId(created.id());
        entity.setCustomerId(created.customerId());
        entity.setUpdatedAt(created.updatedAt());
        cartRepository.save(entity);

        writeToCache(redisKey, created);
        return new CartOperationResult(created, true);
    }

    public CartResponse getCart(String customerId) {
        UUID customerUuid = parseCustomerId(customerId);
        String redisKey = buildCartKey(customerUuid);

        CartResponse cached = readFromCache(redisKey);
        if (cached != null) {
            return cached;
        }

        CartEntity entity = cartRepository.findByCustomerId(customerUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found"));

        CartResponse response = mapToResponse(entity);
        writeToCache(redisKey, response);
        return response;
    }

    private UUID parseCustomerId(String customerId) {
        try {
            return UUID.fromString(customerId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid customerId");
        }
    }

    private String buildCartKey(UUID customerId) {
        return CART_KEY_PREFIX + customerId;
    }

    private CartResponse readFromCache(String redisKey) {
        String payload = redisTemplate.opsForValue().get(redisKey);
        if (payload == null || payload.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(payload, CartResponse.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to read cart cache");
        }
    }

    private void writeToCache(String redisKey, CartResponse cart) {
        try {
            String payload = objectMapper.writeValueAsString(cart);
            redisTemplate.opsForValue().set(redisKey, payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to write cart cache");
        }
    }

    private CartResponse mapToResponse(CartEntity entity) {
        List<CartItemResponse> items = entity.getItems() == null
                ? Collections.emptyList()
                : entity.getItems().stream().map(this::mapItem).toList();

        return new CartResponse(
                entity.getId(),
                entity.getCustomerId(),
                entity.getUpdatedAt(),
                items
        );
    }

    private CartItemResponse mapItem(CartItemEntity item) {
        return new CartItemResponse(
                item.getId(),
                item.getProductVariantId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getAddedAt()
        );
    }
}

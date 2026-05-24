package iuh.fit.cartservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.cartservice.client.CatalogServiceClient;
import iuh.fit.cartservice.client.UserServiceClient;
import iuh.fit.cartservice.dto.AddCartItemRequest;
import iuh.fit.cartservice.dto.CartItemResponse;
import iuh.fit.cartservice.dto.CartResponse;
import iuh.fit.cartservice.dto.CustomerResponse;
import iuh.fit.cartservice.dto.ProductVariantResponse;
import iuh.fit.cartservice.dto.UpdateCartItemRequest;
import iuh.fit.cartservice.entity.CartEntity;
import iuh.fit.cartservice.entity.CartItemEntity;
import iuh.fit.cartservice.repo.CartRepository;
import iuh.fit.shared.api.ApiResponse;
import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.math.BigDecimal;
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
    private final UserServiceClient userServiceClient;
    private final CatalogServiceClient catalogServiceClient;

    public CartService(CartRepository cartRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            UserServiceClient userServiceClient,
            CatalogServiceClient catalogServiceClient) {
        this.cartRepository = cartRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userServiceClient = userServiceClient;
        this.catalogServiceClient = catalogServiceClient;
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

        CartEntity entity = new CartEntity();
        entity.setCustomerId(customerUuid);
        entity.setUpdatedAt(LocalDateTime.now());
        CartEntity saved = cartRepository.save(entity);

        CartResponse created = mapToResponse(saved);
        writeToCache(redisKey, created);
        return new CartOperationResult(created, true);
    }

    @Transactional
    public CartResponse addItem(String customerId, AddCartItemRequest request) {
        UUID customerUuid = parseCustomerId(customerId);
        validateAddItemRequest(request);

        ProductVariantResponse variant = catalogServiceClient.getVariantById(request.productVariantId());
        if (variant == null || variant.id() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Product variant not found");
        }
        if (Boolean.FALSE.equals(variant.isActive())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Product variant is inactive");
        }
        if (variant.stockQuantity() != null && request.quantity() > variant.stockQuantity()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Insufficient stock for product variant");
        }
        if (variant.price() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Product variant price missing");
        }
        BigDecimal unitPrice = variant.price();

        CartEntity cart = cartRepository.findByCustomerId(customerUuid)
                .orElseGet(() -> createCartEntity(customerUuid));

        CartItemEntity existing = findItemByVariant(cart, request.productVariantId());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.quantity());
            existing.setUnitPrice(unitPrice);
        } else {
            CartItemEntity item = new CartItemEntity();
            item.setCart(cart);
            item.setProductVariantId(request.productVariantId());
            item.setQuantity(request.quantity());
            item.setUnitPrice(unitPrice);
            item.setAddedAt(LocalDateTime.now());
            cart.getItems().add(item);
        }

        cart.setUpdatedAt(LocalDateTime.now());
        CartEntity saved = cartRepository.save(cart);
        CartResponse response = mapToResponse(saved);
        writeToCache(buildCartKey(customerUuid), response);
        return response;
    }

    @Transactional
    public CartResponse updateItemQuantity(String customerId, String itemId, UpdateCartItemRequest request) {
        UUID customerUuid = parseCustomerId(customerId);
        UUID itemUuid = parseUuid(itemId, "Invalid cart item id");
        validateUpdateItemRequest(request);

        CartEntity cart = cartRepository.findByCustomerId(customerUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found"));

        CartItemEntity item = findItemById(cart, itemUuid);
        if (item == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item not found");
        }

        if (request.quantity() == 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(request.quantity());
        }

        cart.setUpdatedAt(LocalDateTime.now());
        CartEntity saved = cartRepository.save(cart);
        CartResponse response = mapToResponse(saved);
        writeToCache(buildCartKey(customerUuid), response);
        return response;
    }

    @Transactional
    public CartResponse removeItem(String customerId, String itemId) {
        UUID customerUuid = parseCustomerId(customerId);
        UUID itemUuid = parseUuid(itemId, "Invalid cart item id");

        CartEntity cart = cartRepository.findByCustomerId(customerUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found"));

        CartItemEntity item = findItemById(cart, itemUuid);
        if (item == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item not found");
        }

        cart.getItems().remove(item);
        cart.setUpdatedAt(LocalDateTime.now());
        CartEntity saved = cartRepository.save(cart);
        CartResponse response = mapToResponse(saved);
        writeToCache(buildCartKey(customerUuid), response);
        return response;
    }

    @Transactional
    public CartResponse removeItems(String customerId, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return getCart(customerId);
        }
        
        UUID customerUuid = parseCustomerId(customerId);
        List<UUID> itemUuids = itemIds.stream()
            .map(id -> parseUuid(id, "Invalid cart item id: " + id))
            .toList();

        CartEntity cart = cartRepository.findByCustomerId(customerUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found"));

        if (cart.getItems() != null) {
            cart.getItems().removeIf(item -> itemUuids.contains(item.getId()));
        }

        cart.setUpdatedAt(LocalDateTime.now());
        CartEntity saved = cartRepository.save(cart);
        CartResponse response = mapToResponse(saved);
        writeToCache(buildCartKey(customerUuid), response);
        return response;
    }

    public CartResponse getCart(String customerId) {
        try {
            UUID customerUuid = parseCustomerId(customerId);
            String redisKey = buildCartKey(customerUuid);

            CartResponse cached = readFromCache(redisKey);
            if (cached != null) {
                return cached;
            }

            CartEntity entity = cartRepository.findByCustomerId(customerUuid)
                    .orElseGet(() -> createCartEntity(customerUuid));

            CartResponse response = mapToResponse(entity);
            writeToCache(redisKey, response);
            return response;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DEBUG ERROR: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }

    public CartResponse getCartByAccountId(String accountId) {
        try {
            ApiResponse<CustomerResponse> apiResponse;
            try {
                apiResponse = userServiceClient.getCustomerByAccountId(accountId);
            } catch (feign.FeignException.NotFound e) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found for account");
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "DEBUG FEIGN ERROR: " + ex.getClass().getName() + " - " + ex.getMessage());
            }

            if (apiResponse == null || apiResponse.data() == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Customer not found for account");
            }

            CustomerResponse customer = apiResponse.data();
            return getCart(customer.id().toString());
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DEBUG ACCOUNT ERROR: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }

    @Transactional
    public CartResponse backupCartFromCache(String customerId) {
        UUID customerUuid = parseCustomerId(customerId);
        String redisKey = buildCartKey(customerUuid);

        CartResponse cached = readFromCache(redisKey);
        if (cached == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found in cache");
        }

        CartEntity cart = cartRepository.findByCustomerId(customerUuid)
                .orElseGet(() -> createCartEntity(customerUuid));

        cart.setUpdatedAt(cached.updatedAt());
        syncCartItems(cart, cached.items());

        CartEntity saved = cartRepository.save(cart);
        CartResponse response = mapToResponse(saved);
        writeToCache(redisKey, response);
        return response;
    }

    @Transactional
    public void clearCart(String customerId) {
        UUID customerUuid = parseCustomerId(customerId);
        String redisKey = buildCartKey(customerUuid);
        boolean hadCache = Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        boolean hadDb = cartRepository.findByCustomerId(customerUuid)
                .map(cart -> {
                    cartRepository.delete(cart);
                    return true;
                })
                .orElse(false);
        redisTemplate.delete(redisKey);

        if (!hadCache && !hadDb) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart not found");
        }
    }

    private UUID parseCustomerId(String customerId) {
        return parseUuid(customerId, "Invalid customerId");
    }

    private UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
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
                items);
    }

    private CartItemResponse mapItem(CartItemEntity item) {
        return new CartItemResponse(
                item.getId(),
                item.getProductVariantId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getAddedAt());
    }

    private CartEntity createCartEntity(UUID customerId) {
        CartEntity cart = new CartEntity();
        cart.setCustomerId(customerId);
        cart.setUpdatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    private CartItemEntity findItemByVariant(CartEntity cart, UUID productVariantId) {
        if (cart.getItems() == null) {
            return null;
        }

        return cart.getItems().stream()
                .filter(item -> productVariantId.equals(item.getProductVariantId()))
                .findFirst()
                .orElse(null);
    }

    private CartItemEntity findItemById(CartEntity cart, UUID itemId) {
        if (cart.getItems() == null) {
            return null;
        }

        return cart.getItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    private void syncCartItems(CartEntity cart, List<CartItemResponse> items) {
        List<CartItemEntity> target = cart.getItems();
        target.clear();

        if (items == null || items.isEmpty()) {
            return;
        }

        for (CartItemResponse item : items) {
            CartItemEntity entity = new CartItemEntity();
            entity.setId(item.id());
            entity.setCart(cart);
            entity.setProductVariantId(item.productVariantId());
            entity.setQuantity(item.quantity());
            entity.setUnitPrice(item.unitPrice());
            entity.setAddedAt(item.addedAt());
            target.add(entity);
        }
    }

    private void validateAddItemRequest(AddCartItemRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        }
        if (request.productVariantId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "productVariantId is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "quantity must be greater than 0");
        }
    }

    private void validateUpdateItemRequest(UpdateCartItemRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        }
        if (request.quantity() == null || request.quantity() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "quantity must be zero or greater");
        }
    }
}

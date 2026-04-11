package com.example.Vkus.service;

import com.example.Vkus.entity.Cart;
import com.example.Vkus.entity.CartItem;
import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.CartComboRepository;
import com.example.Vkus.repository.CartItemRepository;
import com.example.Vkus.repository.CartRepository;
import com.example.Vkus.repository.ProductRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class CartStockValidationService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartComboRepository cartComboRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbc;

    public CartStockValidationService(CartRepository cartRepository,
                                      CartItemRepository cartItemRepository,
                                      CartComboRepository cartComboRepository,
                                      ProductRepository productRepository,
                                      JdbcTemplate jdbc) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartComboRepository = cartComboRepository;
        this.productRepository = productRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public void validateCanAddProduct(Long userId, Long buffetId, Long productId, int addQty) {
        if (addQty <= 0) return;

        Map<Long, Integer> reserved = loadReservedQtyByProduct(userId, buffetId);
        int totalNeeded = reserved.getOrDefault(productId, 0) + addQty;

        ensureEnough(buffetId, productId, totalNeeded);
    }

    @Transactional(readOnly = true)
    public void validateCanSetProductQty(Long userId, Long buffetId, Long productId, int newQty) {
        if (newQty <= 0) return;

        Map<Long, Integer> reserved = loadReservedQtyByProduct(userId, buffetId);
        int currentSimpleQty = loadCurrentSimpleQty(userId, buffetId, productId);
        int reservedWithoutCurrentSimple = Math.max(0, reserved.getOrDefault(productId, 0) - currentSimpleQty);
        int totalNeeded = reservedWithoutCurrentSimple + newQty;

        ensureEnough(buffetId, productId, totalNeeded);
    }

    @Transactional(readOnly = true)
    public void validateCanAddCombo(Long userId, Long buffetId, Map<Long, Integer> addNeedByProduct) {
        if (addNeedByProduct == null || addNeedByProduct.isEmpty()) return;

        Map<Long, Integer> reserved = loadReservedQtyByProduct(userId, buffetId);

        for (var entry : addNeedByProduct.entrySet()) {
            Long productId = entry.getKey();
            int addQty = entry.getValue() == null ? 0 : entry.getValue();
            if (addQty <= 0) continue;

            int totalNeeded = reserved.getOrDefault(productId, 0) + addQty;
            ensureEnough(buffetId, productId, totalNeeded);
        }
    }

    private int loadCurrentSimpleQty(Long userId, Long buffetId, Long productId) {
        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId).orElse(null);
        if (cart == null) return 0;

        return cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .map(CartItem::getQty)
                .orElse(0);
    }

    private Map<Long, Integer> loadReservedQtyByProduct(Long userId, Long buffetId) {
        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId).orElse(null);
        Map<Long, Integer> reserved = new HashMap<>();
        if (cart == null) return reserved;

        for (CartItem item : cartItemRepository.findAllByCartIdWithProduct(cart.getId())) {
            reserved.merge(item.getProduct().getId(), item.getQty(), Integer::sum);
        }

        for (var combo : cartComboRepository.findAllByCartIdFull(cart.getId())) {
            int comboQty = combo.getQty() == null ? 1 : combo.getQty();

            for (var item : combo.getItems()) {
                int itemQty = item.getQty() == null ? 1 : item.getQty();
                reserved.merge(item.getProduct().getId(), itemQty * comboQty, Integer::sum);
            }
        }

        return reserved;
    }

    private void ensureEnough(Long buffetId, Long productId, int totalNeeded) {
        int available = loadAvailableQtyFromBatches(buffetId, productId);
        if (available >= totalNeeded) return;

        String productName = productRepository.findById(productId)
                .map(Product::getName)
                .orElse("Товар");

        throw new IllegalStateException(
                "Недостаточно товара \"" + productName + "\" на складе. Доступно: " +
                        available + ", нужно с учетом корзины: " + totalNeeded
        );
    }

    private int loadAvailableQtyFromBatches(Long buffetId, Long productId) {
        Integer total = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(qty_available), 0)
                FROM inventory_batches
                WHERE buffet_id = ?
                  AND product_id = ?
                  AND status = 'active'
                  AND qty_available > 0
                """,
                Integer.class,
                buffetId,
                productId
        );
        return total == null ? 0 : total;
    }
}
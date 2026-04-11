package com.example.Vkus.service;

import com.example.Vkus.entity.Cart;
import com.example.Vkus.entity.CartItem;
import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.CartItemRepository;
import com.example.Vkus.repository.CartRepository;
import com.example.Vkus.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CartStockValidationService cartStockValidationService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       CartStockValidationService cartStockValidationService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.cartStockValidationService = cartStockValidationService;
    }

    @Transactional
    public void add(Long userId, Long buffetId, Long productId, int qty) {
        if (qty <= 0) qty = 1;

        cartStockValidationService.validateCanAddProduct(userId, buffetId, productId, qty);

        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setUserId(userId);
                    c.setBuffetId(buffetId);
                    return cartRepository.save(c);
                });

        CartItem item = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElse(null);

        if (item == null) {
            Product p = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            CartItem ci = new CartItem();
            ci.setCart(cart);
            ci.setProduct(p);
            ci.setQty(qty);
            cartItemRepository.save(ci);
        } else {
            item.setQty(item.getQty() + qty);
        }
    }

    @Transactional(readOnly = true)
    public List<CartItem> getItems(Long userId, Long buffetId) {
        Cart cart = cart(userId, buffetId);
        if (cart == null) return List.of();
        return cartItemRepository.findAllByCartIdWithProduct(cart.getId());
    }

    @Transactional
    public void setQty(Long userId, Long buffetId, Long productId, int qty) {
        Cart cart = cartOrThrow(userId, buffetId);
        if (qty <= 0) {
            cartItemRepository.deleteByCart_IdAndProduct_Id(cart.getId(), productId);
            return;
        }

        cartStockValidationService.validateCanSetProductQty(userId, buffetId, productId, qty);

        CartItem item = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));
        item.setQty(qty);
    }

    @Transactional
    public void remove(Long userId, Long buffetId, Long productId) {
        Cart cart = cartOrThrow(userId, buffetId);
        cartItemRepository.deleteByCart_IdAndProduct_Id(cart.getId(), productId);
    }

    @Transactional
    public void clear(Long userId, Long buffetId) {
        Cart cart = cartOrThrow(userId, buffetId);
        cartItemRepository.deleteByCart_Id(cart.getId());
    }

    private Cart cartOrThrow(Long userId, Long buffetId) {
        return cartRepository.findByUserIdAndBuffetId(userId, buffetId)
                .orElseThrow(() -> new IllegalStateException("Cart not found for user/buffet"));
    }

    private Cart cart(Long userId, Long buffetId) {
        return cartRepository.findByUserIdAndBuffetId(userId, buffetId).orElse(null);
    }
}
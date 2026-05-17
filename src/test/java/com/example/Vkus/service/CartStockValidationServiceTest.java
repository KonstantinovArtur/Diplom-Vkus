package com.example.Vkus.service;

import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.CartComboRepository;
import com.example.Vkus.repository.CartItemRepository;
import com.example.Vkus.repository.CartRepository;
import com.example.Vkus.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CartStockValidationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long BUFFET_ID = 10L;
    private static final Long PRODUCT_ID = 100L;

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private CartComboRepository cartComboRepository;
    private ProductRepository productRepository;
    private JdbcTemplate jdbc;

    private CartStockValidationService service;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        cartComboRepository = mock(CartComboRepository.class);
        productRepository = mock(ProductRepository.class);
        jdbc = mock(JdbcTemplate.class);

        service = new CartStockValidationService(
                cartRepository,
                cartItemRepository,
                cartComboRepository,
                productRepository,
                jdbc
        );
    }

    @Test
    void validateCanAddProduct_whenQtyIsZero_doesNothing() {
        assertDoesNotThrow(() ->
                service.validateCanAddProduct(USER_ID, BUFFET_ID, PRODUCT_ID, 0)
        );

        verifyNoInteractions(
                cartRepository,
                cartItemRepository,
                cartComboRepository,
                productRepository,
                jdbc
        );
    }

    @Test
    void validateCanAddProduct_whenEnoughStock_doesNotThrowException() {
        when(cartRepository.findByUserIdAndBuffetId(USER_ID, BUFFET_ID))
                .thenReturn(Optional.empty());

        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(BUFFET_ID),
                eq(PRODUCT_ID)
        )).thenReturn(5);

        assertDoesNotThrow(() ->
                service.validateCanAddProduct(USER_ID, BUFFET_ID, PRODUCT_ID, 2)
        );

        verify(productRepository, never()).findById(anyLong());
    }

    @Test
    void validateCanAddProduct_whenNotEnoughStock_throwsReadableException() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Багет");

        when(cartRepository.findByUserIdAndBuffetId(USER_ID, BUFFET_ID))
                .thenReturn(Optional.empty());

        when(jdbc.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(BUFFET_ID),
                eq(PRODUCT_ID)
        )).thenReturn(1);

        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.validateCanAddProduct(USER_ID, BUFFET_ID, PRODUCT_ID, 3)
        );

        assertTrue(ex.getMessage().contains("Недостаточно товара"));
        assertTrue(ex.getMessage().contains("Багет"));
        assertTrue(ex.getMessage().contains("Доступно: 1"));
    }
}
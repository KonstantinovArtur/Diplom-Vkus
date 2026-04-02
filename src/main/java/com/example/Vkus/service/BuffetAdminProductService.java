package com.example.Vkus.service;

import com.example.Vkus.entity.Buffet;
import com.example.Vkus.entity.InventoryItem;
import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.repository.InventoryItemRepository;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.web.dto.ProductUpsertDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BuffetAdminProductService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ProductRepository productRepository;
    private final BuffetRepository buffetRepository;

    public BuffetAdminProductService(InventoryItemRepository inventoryItemRepository,
                                     ProductRepository productRepository,
                                     BuffetRepository buffetRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.productRepository = productRepository;
        this.buffetRepository = buffetRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> listForBuffet(Long buffetId, Long categoryId, String search) {
        if (search != null) {
            search = search.trim();
            if (search.isBlank()) search = null;
        }
        return inventoryItemRepository.findForBuffetFiltered(buffetId, categoryId, search);
    }

    @Transactional(readOnly = true)
    public InventoryItem getInventoryItemOrThrow(Long buffetId, Long productId) {
        return inventoryItemRepository.findByBuffet_IdAndProduct_Id(buffetId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не принадлежит вашему буфету"));
    }

    @Transactional
    public Long createAndAddToBuffet(Long buffetId, ProductUpsertDto dto) {
        Buffet buffet = buffetRepository.findById(buffetId)
                .orElseThrow(() -> new IllegalStateException("Буфет не найден"));

        Product p = new Product();
        // category ставишь сам через categoryRepository в другом сервисе или сюда добавь categoryRepository
        // p.setCategory(...)
        p.setName(dto.getName().trim());
        p.setDescription(dto.getDescription());
        p.setBasePrice(dto.getBasePrice());
        p.setIsActive(dto.isActive());

        // картинку применяешь отдельным методом (MultipartFile) — у тебя это уже было
        // applyImageIfPresent(p, dto.getImage());

        productRepository.save(p);

        InventoryItem ii = new InventoryItem();
        ii.setBuffet(buffet);
        ii.setProduct(p);
        ii.setQuantity(0);

        inventoryItemRepository.save(ii);

        return p.getId();
    }

    @Transactional
    public void updateProductOnlyIfOwned(Long buffetId, Long productId, ProductUpsertDto dto) {
        // 1) проверка принадлежности
        getInventoryItemOrThrow(buffetId, productId);

        // 2) обновление товара
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));

        // p.setCategory(...) если разрешаешь менять категорию — проверяй
        p.setName(dto.getName().trim());
        p.setDescription(dto.getDescription());
        p.setBasePrice(dto.getBasePrice());
        p.setIsActive(dto.isActive());

        // applyImageIfPresent(p, dto.getImage());

        productRepository.save(p);
    }

    @Transactional
    public void removeFromBuffet(Long buffetId, Long productId) {
        // удаляем связь буфет-товар, а не сам товар
        inventoryItemRepository.deleteByBuffet_IdAndProduct_Id(buffetId, productId);
    }
}

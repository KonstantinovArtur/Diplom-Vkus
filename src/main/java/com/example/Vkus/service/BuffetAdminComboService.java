package com.example.Vkus.service;

import com.example.Vkus.entity.Buffet;
import com.example.Vkus.entity.ComboSlot;
import com.example.Vkus.entity.ComboSlotProduct;
import com.example.Vkus.entity.ComboSlotProductId;
import com.example.Vkus.entity.ComboTemplate;
import com.example.Vkus.entity.Product;
import com.example.Vkus.entity.User;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.repository.ComboSlotProductRepository;
import com.example.Vkus.repository.ComboSlotRepository;
import com.example.Vkus.repository.ComboTemplateRepository;
import com.example.Vkus.repository.InventoryItemRepository;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.web.dto.ComboSlotForm;
import com.example.Vkus.web.dto.ComboSlotProductForm;
import com.example.Vkus.web.dto.ComboTemplateForm;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BuffetAdminComboService {

    public record Ctx(Long userId, Long buffetId) {}

    private final CurrentUserService currentUserService;
    private final ComboTemplateRepository templateRepo;
    private final ComboSlotRepository slotRepo;
    private final ComboSlotProductRepository slotProductRepo;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductRepository productRepository;
    private final BuffetRepository buffetRepository;
    private final UserRepository userRepository;

    public BuffetAdminComboService(CurrentUserService currentUserService,
                                   ComboTemplateRepository templateRepo,
                                   ComboSlotRepository slotRepo,
                                   ComboSlotProductRepository slotProductRepo,
                                   InventoryItemRepository inventoryItemRepository,
                                   ProductRepository productRepository,
                                   BuffetRepository buffetRepository,
                                   UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.templateRepo = templateRepo;
        this.slotRepo = slotRepo;
        this.slotProductRepo = slotProductRepo;
        this.inventoryItemRepository = inventoryItemRepository;
        this.productRepository = productRepository;
        this.buffetRepository = buffetRepository;
        this.userRepository = userRepository;
    }

    public Ctx requireCtx(Authentication auth) {
        var u = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        return new Ctx(u.getId(), buffetId);
    }

    public List<ComboTemplate> listTemplates(Long buffetId) {
        return templateRepo.findByBuffet_IdOrderByIdDesc(buffetId);
    }

    public ComboTemplate requireOwnedTemplate(Long templateId, Long buffetId) {
        return templateRepo.findByIdAndBuffet_Id(templateId, buffetId)
                .orElseThrow(() -> new IllegalStateException("Комбо не найдено или не принадлежит вашему буфету"));
    }

    public ComboSlot requireOwnedSlot(Long slotId, Long buffetId) {
        return slotRepo.findByIdAndComboTemplate_Buffet_Id(slotId, buffetId)
                .orElseThrow(() -> new IllegalStateException("Слот не найден или не принадлежит вашему буфету"));
    }

    public List<Product> productsInBuffet(Long buffetId) {
        return inventoryItemRepository.findProductsInBuffet(buffetId);
    }

    public ComboTemplateForm toForm(ComboTemplate t) {
        ComboTemplateForm f = new ComboTemplateForm();
        f.setId(t.getId());
        f.setName(t.getName());
        f.setBasePrice(t.getBasePrice());
        f.setIsActive(t.getIsActive());
        return f;
    }

    public void saveTemplate(ComboTemplateForm form,
                             BindingResult br,
                             Long buffetId,
                             Long userId) {

        if (br.hasErrors()) {
            return;
        }

        if (form.getBasePrice() == null) {
            form.setBasePrice(BigDecimal.ZERO);
        }

        ComboTemplate t;
        if (form.getId() == null) {
            t = new ComboTemplate();

            Buffet buffet = buffetRepository.findById(buffetId)
                    .orElseThrow(() -> new IllegalStateException("Буфет не найден"));

            User creator = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

            t.setBuffet(buffet);
            t.setCreatedBy(creator);
        } else {
            t = requireOwnedTemplate(form.getId(), buffetId);
        }

        t.setName(form.getName().trim());
        t.setBasePrice(form.getBasePrice());
        t.setIsActive(Boolean.TRUE.equals(form.getIsActive()));

        templateRepo.save(t);
        form.setId(t.getId());
    }

    public void deactivateTemplate(Long id, Long buffetId) {
        ComboTemplate t = requireOwnedTemplate(id, buffetId);
        t.setIsActive(false);
        templateRepo.save(t);
    }

    public void deleteTemplateHard(Long id, Long buffetId) {
        ComboTemplate t = requireOwnedTemplate(id, buffetId);

        if (templateRepo.existsInOrders(id)) {
            throw new IllegalStateException("Нельзя удалить комбо, так как его уже приобрёл хотя бы один пользователь");
        }

        templateRepo.delete(t);
    }

    public void addSlot(Long templateId, ComboSlotForm form, BindingResult br, Long buffetId) {
        if (br.hasErrors()) {
            return;
        }

        ComboTemplate t = requireOwnedTemplate(templateId, buffetId);

        ComboSlot s = new ComboSlot();
        s.setComboTemplate(t);
        s.setName(form.getName().trim());

        // всегда фиксируем 1, даже если кто-то вручную подменит значение в форме
        s.setRequiredQty(1);

        s.setSortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder());

        slotRepo.save(s);
    }

    public void deleteSlot(Long slotId, Long buffetId) {
        ComboSlot s = requireOwnedSlot(slotId, buffetId);

        if (slotRepo.existsInOrders(slotId)) {
            throw new IllegalStateException("Нельзя удалить слот, так как комбо с этим слотом уже было приобретено");
        }

        slotRepo.delete(s);
    }

    public void addSlotProduct(Long slotId, ComboSlotProductForm form, BindingResult br, Long buffetId) {
        if (br.hasErrors()) {
            return;
        }

        ComboSlot slot = requireOwnedSlot(slotId, buffetId);

        boolean allowed = productsInBuffet(buffetId).stream()
                .anyMatch(p -> p.getId().equals(form.getProductId()));
        if (!allowed) {
            br.rejectValue("productId", "product.not.in.buffet", "Товар не входит в ассортимент данного буфета");
            return;
        }

        Product p = productRepository.findById(form.getProductId())
                .orElseThrow(() -> new IllegalStateException("Товар не найден"));

        ComboSlotProductId id = new ComboSlotProductId(slot.getId(), p.getId());
        if (slotProductRepo.existsById(id)) {
            br.rejectValue("productId", "product.already.in.slot", "Этот товар уже добавлен в слот");
            return;
        }

        ComboSlotProduct sp = new ComboSlotProduct();
        sp.setId(id);
        sp.setSlot(slot);
        sp.setProduct(p);
        sp.setExtraPrice(form.getExtraPrice());

        slotProductRepo.save(sp);
    }

    public void removeSlotProduct(Long slotId, Long productId, Long buffetId) {
        ComboSlot slot = requireOwnedSlot(slotId, buffetId);
        ComboSlotProductId id = new ComboSlotProductId(slot.getId(), productId);
        slotProductRepo.deleteById(id);
    }
}
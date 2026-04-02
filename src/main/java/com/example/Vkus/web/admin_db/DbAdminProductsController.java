package com.example.Vkus.web.admin_db;

import com.example.Vkus.entity.Category;
import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.CategoryRepository;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.web.dto.ProductForm;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin-db/products")
public class DbAdminProductsController {

    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final List<String> ALLOWED_MIME = List.of("image/png", "image/jpeg", "image/webp");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService audit;

    public DbAdminProductsController(ProductRepository productRepository,
                                     CategoryRepository categoryRepository,
                                     AuditLogService audit) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "edit", required = false) Long editId) {

        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("categories", categoryRepository.findAllOrdered());

        ProductForm form = new ProductForm();

        if (editId != null) {
            Product p = productRepository.findById(editId).orElse(null);
            if (p != null) {
                form.setId(p.getId());
                form.setCategoryId(p.getCategory().getId());
                form.setName(p.getName());
                form.setDescription(p.getDescription());
                form.setBasePrice(p.getBasePrice());
                form.setShelfLifeDays(p.getShelfLifeDays());
                form.setIsActive(p.getIsActive());
                model.addAttribute("editProduct", p);
            }
        }

        model.addAttribute("form", form);
        model.addAttribute("editId", editId);
        return "admin-db/products";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") ProductForm form,
                       BindingResult br,
                       @RequestParam(value = "image", required = false) MultipartFile image,
                       @RequestParam(value = "removeImage", required = false) Boolean removeImage,
                       Model model,
                       RedirectAttributes ra) {

        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("categories", categoryRepository.findAllOrdered());
        model.addAttribute("editId", form.getId());

        Category cat = null;
        if (form.getCategoryId() != null) {
            cat = categoryRepository.findById(form.getCategoryId()).orElse(null);
        }
        if (cat == null) {
            br.rejectValue("categoryId", "category.invalid", "Категория не найдена");
        }

        if (cat != null && form.getName() != null) {
            boolean existsByName = (form.getId() == null)
                    ? productRepository.existsByCategory_IdAndNameIgnoreCase(cat.getId(), form.getName().trim())
                    : productRepository.existsByCategory_IdAndNameIgnoreCaseAndIdNot(cat.getId(), form.getName().trim(), form.getId());

            if (existsByName) {
                br.rejectValue("name", "name.dup", "В этой категории уже есть товар с таким названием");
            }
        }

        if (image != null && !image.isEmpty()) {
            if (image.getSize() > MAX_IMAGE_BYTES) {
                br.reject("image.tooLarge", "Картинка слишком большая (макс 2MB)");
            }
            String mime = image.getContentType();
            if (mime == null || !ALLOWED_MIME.contains(mime)) {
                br.reject("image.badType", "Разрешены только PNG/JPEG/WebP");
            }
        }

        Product p = null;
        Map<String, Object> before = null;

        if (form.getId() != null) {
            p = productRepository.findById(form.getId()).orElse(null);
            if (p == null) {
                br.reject("notFound", "Товар не найден");
            } else {
                model.addAttribute("editProduct", p);
                before = snapshotProduct(p);
            }
        }

        if (br.hasErrors()) {
            return "admin-db/products";
        }

        boolean isCreate = (p == null);
        if (p == null) {
            p = new Product();
        }

        p.setCategory(cat);
        p.setName(form.getName().trim());
        p.setDescription(form.getDescription());
        p.setBasePrice(form.getBasePrice());
        p.setShelfLifeDays(form.getShelfLifeDays());
        p.setIsActive(form.getIsActive() != null ? form.getIsActive() : true);

        if (Boolean.TRUE.equals(removeImage)) {
            p.setImageData(null);
            p.setImageMime(null);
            p.setImageUpdatedAt(LocalDateTime.now());
        }

        if (image != null && !image.isEmpty()) {
            try {
                p.setImageData(image.getBytes());
                p.setImageMime(image.getContentType());
                p.setImageUpdatedAt(LocalDateTime.now());
            } catch (IOException e) {
                br.reject("image.io", "Не удалось прочитать файл картинки");
                return "admin-db/products";
            }
        }

        productRepository.save(p);

        if (isCreate && (p.getProductCode() == null || p.getProductCode().isBlank())) {
            p.setProductCode(String.format("P%03d", p.getId()));
            productRepository.save(p);
        }

        Map<String, Object> after = snapshotProduct(p);

        if (isCreate) {
            audit.log("PRODUCT_CREATE", "product", p.getId(), Map.of(
                    "after", after
            ));
        } else {
            audit.log("PRODUCT_UPDATE", "product", p.getId(), Map.of(
                    "before", before,
                    "after", after
            ));
        }

        ra.addFlashAttribute("ok", isCreate ? "Товар создан" : "Товар обновлён");
        return "redirect:/admin-db/products";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id, RedirectAttributes ra) {
        Product p = productRepository.findById(id).orElse(null);
        if (p == null) {
            ra.addFlashAttribute("ok", "Товар уже удалён");
            return "redirect:/admin-db/products";
        }

        Map<String, Object> before = snapshotProduct(p);
        productRepository.deleteById(id);

        audit.log("PRODUCT_DELETE", "product", id, Map.of(
                "before", before
        ));

        ra.addFlashAttribute("ok", "Товар удалён");
        return "redirect:/admin-db/products";
    }

    @GetMapping("/{id}/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        Product p = productRepository.findById(id).orElse(null);
        if (p == null || p.getImageData() == null || p.getImageData().length == 0) {
            return ResponseEntity.notFound().build();
        }

        MediaType mt = (p.getImageMime() != null)
                ? MediaType.parseMediaType(p.getImageMime())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mt)
                .cacheControl(CacheControl.noCache())
                .body(p.getImageData());
    }

    private Map<String, Object> snapshotProduct(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("categoryId", p.getCategory() != null ? p.getCategory().getId() : null);
        m.put("categoryName", p.getCategory() != null ? p.getCategory().getName() : null);
        m.put("productCode", p.getProductCode());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("basePrice", p.getBasePrice());
        m.put("shelfLifeDays", p.getShelfLifeDays());
        m.put("isActive", p.getIsActive());
        m.put("hasImage", p.getImageData() != null && p.getImageData().length > 0);
        m.put("imageMime", p.getImageMime());
        m.put("imageUpdatedAt", p.getImageUpdatedAt());
        return m;
    }
}
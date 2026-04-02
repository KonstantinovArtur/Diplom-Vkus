package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.repository.CategoryRepository;
import com.example.Vkus.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin-buffet/products")
public class AdminBuffetAllProductsController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public AdminBuffetAllProductsController(ProductRepository productRepository,
                                            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String q,
                       Model model) {

        String search = (q == null ? null : q.trim());
        if (search != null && search.isBlank()) search = null;

        var products = (categoryId == null && search == null)
                ? productRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                : (categoryId != null && search == null)
                ? productRepository.findAllByCategory_IdOrderByNameAsc(categoryId)
                : (categoryId == null)
                ? productRepository.findAllByNameContainingIgnoreCaseOrderByNameAsc(search)
                : productRepository.findAllByCategory_IdAndNameContainingIgnoreCaseOrderByNameAsc(categoryId, search);

        model.addAttribute("items", products);
        model.addAttribute("categories", categoryRepository.findAllOrdered()); // у тебя уже есть
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("q", search == null ? "" : search);

        return "admin-buffet/products/list";
    }
}

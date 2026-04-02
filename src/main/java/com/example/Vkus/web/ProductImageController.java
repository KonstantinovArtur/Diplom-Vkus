package com.example.Vkus.web;

import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.ProductRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ProductImageController {

    private final ProductRepository productRepository;

    public ProductImageController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/products/{id}/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        Product p = productRepository.findById(id).orElse(null);
        if (p == null || p.getImageData() == null || p.getImageData().length == 0) {
            return ResponseEntity.notFound().build();
        }

        MediaType mt = (p.getImageMime() != null && !p.getImageMime().isBlank())
                ? MediaType.parseMediaType(p.getImageMime())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mt)
                .cacheControl(CacheControl.noCache())
                .body(p.getImageData());
    }
}

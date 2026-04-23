package com.example.Vkus.web;

import com.example.Vkus.entity.ComboTemplate;
import com.example.Vkus.repository.ComboTemplateRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ComboImageController {

    private final ComboTemplateRepository comboTemplateRepository;

    public ComboImageController(ComboTemplateRepository comboTemplateRepository) {
        this.comboTemplateRepository = comboTemplateRepository;
    }

    @GetMapping("/combos/{id}/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        ComboTemplate combo = comboTemplateRepository.findById(id).orElse(null);
        if (combo == null || combo.getImageData() == null || combo.getImageData().length == 0) {
            return ResponseEntity.notFound().build();
        }

        MediaType mt = (combo.getImageMime() != null && !combo.getImageMime().isBlank())
                ? MediaType.parseMediaType(combo.getImageMime())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mt)
                .cacheControl(CacheControl.noCache())
                .body(combo.getImageData());
    }
}
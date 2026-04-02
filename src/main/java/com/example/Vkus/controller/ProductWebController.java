package com.example.Vkus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProductWebController {

    @GetMapping("/products")
    public String products() {
        return "products";
    }

}

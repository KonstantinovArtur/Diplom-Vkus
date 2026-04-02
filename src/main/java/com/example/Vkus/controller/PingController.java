package com.example.Vkus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PingController {

    @GetMapping("/ping-buffet")
    @ResponseBody
    public String ping() {
        System.out.println("=== PING HIT ===");
        return "OK";
    }
}

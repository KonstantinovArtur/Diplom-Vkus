package com.example.Vkus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VkusApplication {
	public static void main(String[] args) {
		SpringApplication.run(VkusApplication.class, args);

	}

}


package com.example.Vkus.service;

import com.example.Vkus.repository.WarehouseUserRepository;
import com.example.Vkus.web.dto.WarehouseUserOption;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WarehouseUserLookupService {

    private final WarehouseUserRepository repo;

    public WarehouseUserLookupService(WarehouseUserRepository repo) {
        this.repo = repo;
    }

    public List<WarehouseUserOption> getWarehouseUsers() {
        return repo.findActiveWarehouseUsersRaw().stream()
                .map(r -> new WarehouseUserOption(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        (String) r[2]
                ))
                .toList();
    }
}

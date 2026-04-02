package com.example.Vkus.service;

import com.example.Vkus.repository.BuffetLookupRepository;
import com.example.Vkus.web.dto.BuffetOption;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BuffetLookupService {

    private final BuffetLookupRepository repo;

    public BuffetLookupService(BuffetLookupRepository repo) {
        this.repo = repo;
    }

    public List<BuffetOption> getActiveBuffets() {
        return repo.findActiveBuffetsRaw().stream()
                .map(r -> new BuffetOption(((Number) r[0]).longValue(), (String) r[1]))
                .toList();
    }
}

package com.example.Vkus.service;

import com.example.Vkus.web.dto.SellerProductStockRow;
import com.example.Vkus.repository.SellerStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
@Service
public class SellerStockService {

    private final SellerStockRepository repo;

    public SellerStockService(SellerStockRepository repo) {
        this.repo = repo;
    }

    public List<SellerProductStockRow> getStocks(Long buffetId) {
        return repo.findStocksRaw(buffetId).stream()
                .map(r -> new SellerProductStockRow(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        (BigDecimal) r[2],
                        r[3] == null ? 0L : ((Number) r[3]).longValue()
                ))
                .toList();
    }
}

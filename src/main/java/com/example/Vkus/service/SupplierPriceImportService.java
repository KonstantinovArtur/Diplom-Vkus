package com.example.Vkus.service;

import com.example.Vkus.entity.Product;
import com.example.Vkus.entity.Supplier;
import com.example.Vkus.entity.SupplierPrice;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.repository.SupplierPriceRepository;
import com.example.Vkus.repository.SupplierRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SupplierPriceImportService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SupplierPriceRepository supplierPriceRepository;

    public SupplierPriceImportService(SupplierRepository supplierRepository,
                                      ProductRepository productRepository,
                                      SupplierPriceRepository supplierPriceRepository) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.supplierPriceRepository = supplierPriceRepository;
    }

    @Transactional
    public ImportResult importPriceList(Long supplierId, MultipartFile file) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalStateException("Поставщик не найден"));

        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Файл не выбран");
        }

        int insertedOrUpdated = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String productCode = readString(row.getCell(0));
                String productName = readString(row.getCell(1));
                BigDecimal price = readDecimal(row.getCell(2));

                if (productCode == null || productCode.isBlank()) {
                    errors.add("Строка " + (i + 1) + ": пустой код товара");
                    continue;
                }

                if (price == null) {
                    errors.add("Строка " + (i + 1) + ": не указана цена");
                    continue;
                }

                Product product = productRepository.findByProductCodeIgnoreCase(productCode.trim())
                        .orElse(null);

                if (product == null) {
                    errors.add("Строка " + (i + 1) + ": товар с кодом " + productCode + " не найден");
                    continue;
                }

                SupplierPrice supplierPrice = supplierPriceRepository
                        .findBySupplier_IdAndProduct_Id(supplierId, product.getId())
                        .orElseGet(SupplierPrice::new);

                supplierPrice.setSupplier(supplier);
                supplierPrice.setProduct(product);
                supplierPrice.setPrice(price);
                supplierPrice.setLoadedAt(LocalDateTime.now());
                supplierPrice.setSourceFilename(file.getOriginalFilename());

                supplierPriceRepository.save(supplierPrice);
                insertedOrUpdated++;
            }

        } catch (Exception e) {
            throw new IllegalStateException("Ошибка чтения Excel: " + e.getMessage(), e);
        }

        return new ImportResult(insertedOrUpdated, errors);
    }

    private String readString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> null;
        };
    }

    private BigDecimal readDecimal(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> new BigDecimal(cell.getStringCellValue().replace(",", ".").trim());
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    public record ImportResult(int savedCount, List<String> errors) {}
}
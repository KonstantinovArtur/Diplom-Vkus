package com.example.Vkus.service;

import com.example.Vkus.web.dto.DailySalesRow;
import com.example.Vkus.web.dto.TopProductRow;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class BuffetStatsExcelExporter {

    public byte[] export(BuffetStatsService.StatsResult r) {
        try (Workbook wb = new XSSFWorkbook()) {

            // Итоги
            Sheet summary = wb.createSheet("Итоги");
            int row = 0;
            summary.createRow(row++).createCell(0).setCellValue("Период");
            summary.getRow(0).createCell(1).setCellValue(r.from() + " — " + r.to());

            summary.createRow(row++).createCell(0).setCellValue("Количество заказов");
            summary.getRow(1).createCell(1).setCellValue(r.ordersTotal());

            summary.createRow(row++).createCell(0).setCellValue("Выручка");
            summary.getRow(2).createCell(1).setCellValue(r.revenueTotal().doubleValue());

            // Продажи по дням
            Sheet daily = wb.createSheet("Продажи по дням");
            Row h1 = daily.createRow(0);
            h1.createCell(0).setCellValue("Дата");
            h1.createCell(1).setCellValue("Заказы");
            h1.createCell(2).setCellValue("Выручка");

            int i = 1;
            for (DailySalesRow d : r.daily()) {
                Row rr = daily.createRow(i++);
                rr.createCell(0).setCellValue(d.getDay().toString());
                rr.createCell(1).setCellValue(d.getOrdersCount());
                rr.createCell(2).setCellValue(d.getRevenue().doubleValue());
            }

            // Топ товаров
            Sheet top = wb.createSheet("Топ товаров");
            Row h2 = top.createRow(0);
            h2.createCell(0).setCellValue("Товар");
            h2.createCell(1).setCellValue("Кол-во");
            h2.createCell(2).setCellValue("Выручка");

            i = 1;
            for (TopProductRow t : r.topProducts()) {
                Row rr = top.createRow(i++);
                rr.createCell(0).setCellValue(t.getProductName());
                rr.createCell(1).setCellValue(t.getQty());
                rr.createCell(2).setCellValue(t.getRevenue().doubleValue());
            }

            for (int c = 0; c < 3; c++) {
                summary.autoSizeColumn(c);
                daily.autoSizeColumn(c);
                top.autoSizeColumn(c);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel export failed", e);
        }
    }
}
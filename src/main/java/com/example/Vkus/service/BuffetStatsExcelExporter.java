package com.example.Vkus.service;

import com.example.Vkus.web.dto.DailySalesRow;
import com.example.Vkus.web.dto.TopProductRow;
import com.example.Vkus.web.dto.WriteoffProductRow;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class BuffetStatsExcelExporter {

    public byte[] export(BuffetStatsService.StatsResult r) {
        try (Workbook wb = new XSSFWorkbook()) {

            Sheet summary = wb.createSheet("Итоги");
            int row = 0;

            summary.createRow(row++).createCell(0).setCellValue("Период");
            summary.getRow(0).createCell(1).setCellValue(r.from() + " — " + r.to());

            summary.createRow(row++).createCell(0).setCellValue("Количество заказов");
            summary.getRow(1).createCell(1).setCellValue(r.ordersTotal());

            summary.createRow(row++).createCell(0).setCellValue("Выручка");
            summary.getRow(2).createCell(1).setCellValue(r.revenueTotal().doubleValue());

            summary.createRow(row++).createCell(0).setCellValue("Затраты");
            summary.getRow(3).createCell(1).setCellValue(r.costsTotal().doubleValue());

            summary.createRow(row++).createCell(0).setCellValue("Прибыль");
            summary.getRow(4).createCell(1).setCellValue(r.profitTotal().doubleValue());

            Sheet daily = wb.createSheet("Продажи по дням");
            Row dailyHead = daily.createRow(0);
            dailyHead.createCell(0).setCellValue("Дата");
            dailyHead.createCell(1).setCellValue("Заказы");
            dailyHead.createCell(2).setCellValue("Выручка");

            int i = 1;

            for (DailySalesRow d : r.daily()) {
                Row rr = daily.createRow(i++);
                rr.createCell(0).setCellValue(d.getDay() != null ? d.getDay().toString() : "");
                rr.createCell(1).setCellValue(d.getOrdersCount() != null ? d.getOrdersCount() : 0);
                rr.createCell(2).setCellValue(d.getRevenue() != null ? d.getRevenue().doubleValue() : 0);
            }

            Sheet top = wb.createSheet("Топ товаров");
            Row topHead = top.createRow(0);
            topHead.createCell(0).setCellValue("Товар");
            topHead.createCell(1).setCellValue("Кол-во");
            topHead.createCell(2).setCellValue("Выручка");

            i = 1;

            for (TopProductRow t : r.topProducts()) {
                Row rr = top.createRow(i++);
                rr.createCell(0).setCellValue(t.getProductName() != null ? t.getProductName() : "");
                rr.createCell(1).setCellValue(t.getQty() != null ? t.getQty() : 0);
                rr.createCell(2).setCellValue(t.getRevenue() != null ? t.getRevenue().doubleValue() : 0);
            }

            Sheet writeoffs = wb.createSheet("Списания");
            Row writeoffsHead = writeoffs.createRow(0);
            writeoffsHead.createCell(0).setCellValue("Товар");
            writeoffsHead.createCell(1).setCellValue("Списано, шт.");

            i = 1;

            for (WriteoffProductRow w : r.writeoffProducts()) {
                Row rr = writeoffs.createRow(i++);
                rr.createCell(0).setCellValue(w.getProductName() != null ? w.getProductName() : "");
                rr.createCell(1).setCellValue(w.getWriteoffQty() != null ? w.getWriteoffQty() : 0);
            }

            for (int c = 0; c < 3; c++) {
                summary.autoSizeColumn(c);
                daily.autoSizeColumn(c);
                top.autoSizeColumn(c);
            }

            for (int c = 0; c < 2; c++) {
                writeoffs.autoSizeColumn(c);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel export failed", e);
        }
    }
}
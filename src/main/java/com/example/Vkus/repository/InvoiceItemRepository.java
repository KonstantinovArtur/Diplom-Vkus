package com.example.Vkus.repository;

import com.example.Vkus.entity.InvoiceItem;
import com.example.Vkus.web.dto.DailyProfitabilityRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoice_IdOrderByIdAsc(Long invoiceId);

    @Query(value = """
        with days as (
            select generate_series(cast(:from as date), cast(:to as date), interval '1 day')::date as day
        ),
        revenue_by_day as (
            select
                o.order_date as day,
                coalesce(sum(o.final_amount), 0) as revenue
            from orders o
            where o.buffet_id = :buffetId
              and o.order_date between :from and :to
              and o.status = 'issued'
            group by o.order_date
        ),
        costs_by_day as (
            select
                i.invoice_date as day,
                coalesce(sum(
                    coalesce(ii.unit_price, 0) *
                    coalesce(ii.qty_received, ii.qty, 0)
                ), 0) as costs
            from invoices i
            join invoice_items ii on ii.invoice_id = i.id
            where i.buffet_id = :buffetId
              and i.invoice_date between :from and :to
              and i.status = 'posted'
            group by i.invoice_date
        )
        select
            d.day as day,
            coalesce(r.revenue, 0) as revenue,
            coalesce(c.costs, 0) as costs,
            coalesce(r.revenue, 0) - coalesce(c.costs, 0) as profit
        from days d
        left join revenue_by_day r on r.day = d.day
        left join costs_by_day c on c.day = d.day
        order by d.day
        """, nativeQuery = true)
    List<DailyProfitabilityRow> findDailyProfitability(
            @Param("buffetId") Long buffetId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
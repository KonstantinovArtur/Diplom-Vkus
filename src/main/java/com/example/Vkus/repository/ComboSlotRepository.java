package com.example.Vkus.repository;

import com.example.Vkus.entity.ComboSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ComboSlotRepository extends JpaRepository<ComboSlot, Long> {

    List<ComboSlot> findByComboTemplate_IdOrderBySortOrderAscIdAsc(Long templateId);

    Optional<ComboSlot> findByIdAndComboTemplate_Buffet_Id(Long slotId, Long buffetId);

    @Query(value = """
        select exists(
            select 1
            from order_combo_items oci
            where oci.combo_slot_id = :slotId
        )
        """, nativeQuery = true)
    boolean existsInOrders(Long slotId);
}
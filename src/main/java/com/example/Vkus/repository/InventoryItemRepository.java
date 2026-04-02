package com.example.Vkus.repository;

import com.example.Vkus.entity.InventoryItem;
import com.example.Vkus.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    @Query("""
        select ii.product from InventoryItem ii
        join ii.product p
        where ii.buffet.id = :buffetId
        order by p.name asc
    """)
    List<Product> findProductsInBuffet(Long buffetId);

    Optional<InventoryItem> findByBuffetIdAndProductId(Long buffetId, Long productId);

    @Query("""
        select ii
        from InventoryItem ii
        join fetch ii.product p
        join fetch p.category c
        left join fetch c.parent cp
        where ii.buffet.id = :buffetId
        order by coalesce(cp.name, ''), c.name, p.name
    """)

    List<InventoryItem> findAllForBuffetWithProduct(@Param("buffetId") Long buffetId);

    @Query("""
        select ii
        from InventoryItem ii
        join fetch ii.product p
        join fetch p.category c
        left join fetch c.parent cp
        where ii.buffet.id = :buffetId
          and (:categoryId is null or c.id = :categoryId)
          and (:q is null or lower(p.name) like lower(concat('%', :q, '%')))
        order by coalesce(cp.name, ''), c.name, p.name
    """)
    List<InventoryItem> findForBuffetFiltered(
            @Param("buffetId") Long buffetId,
            @Param("categoryId") Long categoryId,
            @Param("q") String q
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select ii
    from InventoryItem ii
    where ii.buffet.id = :buffetId and ii.product.id = :productId
""")
    Optional<InventoryItem> findForUpdate(@Param("buffetId") Long buffetId,
                                          @Param("productId") Long productId);

    Optional<InventoryItem> findByBuffet_IdAndProduct_Id(Long buffetId, Long productId);

    boolean existsByBuffet_IdAndProduct_Id(Long buffetId, Long productId);

    void deleteByBuffet_IdAndProduct_Id(Long buffetId, Long productId);

    @Query(value = """
    SELECT ii.product_id, COALESCE(ii.quantity, 0) AS qty
    FROM inventory_items ii
    WHERE ii.buffet_id = :buffetId
      AND ii.product_id IN (:productIds)
""", nativeQuery = true)
    List<Object[]> findQtyByProductIds(@Param("buffetId") Long buffetId,
                                       @Param("productIds") List<Long> productIds);
    @Query("""
    select i.quantity
    from InventoryItem i
    where i.buffet.id = :buffetId
      and i.product.id = :productId
""")
    Integer findQuantityByBuffetIdAndProductId(@Param("buffetId") Long buffetId,
                                               @Param("productId") Long productId);
}

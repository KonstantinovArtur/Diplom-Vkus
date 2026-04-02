package com.example.Vkus.repository;

import com.example.Vkus.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByIsActiveTrueOrderByNameAsc();
    // список для таблицы (чтобы parent подтянулся без N+1)
    @Query("""
        select c
        from Category c
        left join fetch c.parent p
        order by coalesce(p.name, ''), c.name
    """)
    List<Category> findAllWithParentOrdered();

    // для выпадающего списка родителей
    @Query("""
        select c
        from Category c
        order by c.name
    """)
    List<Category> findAllOrdered();

    // UNIQUE(parent_id, name) — случаи:
    boolean existsByParent_IdAndNameIgnoreCase(Long parentId, String name);
    boolean existsByParentIsNullAndNameIgnoreCase(String name);

    boolean existsByParent_IdAndNameIgnoreCaseAndIdNot(Long parentId, String name, Long id);
    boolean existsByParentIsNullAndNameIgnoreCaseAndIdNot(String name, Long id);

    // (опционально) проверка циклов: "кто является потомками X"
    @Query(value = """
        with recursive sub as (
          select id, parent_id from categories where id = :rootId
          union all
          select c.id, c.parent_id
          from categories c
          join sub s on c.parent_id = s.id
        )
        select id from sub
    """, nativeQuery = true)
    List<Long> subtreeIds(Long rootId);
}

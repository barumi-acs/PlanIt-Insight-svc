package com.planit.analytics.repository;

import com.planit.analytics.entity.CategoryList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface CategoryListRepository extends JpaRepository<CategoryList, Long> {
    
    Optional<CategoryList> findByListId(Long listId);

    // User-svc에서 동기화 시 upsert
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO category_list (list_id, name, color_hex, description) " +
            "VALUES (:listId, :name, :colorHex, :description) " +
            "ON DUPLICATE KEY UPDATE name = VALUES(name), color_hex = VALUES(color_hex), description = VALUES(description)",
            nativeQuery = true)
    void upsert(@Param("listId") Long listId,
                @Param("name") String name,
                @Param("colorHex") String colorHex,
                @Param("description") String description);
}

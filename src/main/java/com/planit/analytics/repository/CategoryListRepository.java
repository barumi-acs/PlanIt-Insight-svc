package com.planit.analytics.repository;

import com.planit.analytics.entity.CategoryList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryListRepository extends JpaRepository<CategoryList, Long> {
    
    Optional<CategoryList> findByListId(Long listId);
}

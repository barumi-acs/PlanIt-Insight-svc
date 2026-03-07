package com.planit.analytics.repository;

import com.planit.analytics.entity.Goals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoalsRepository extends JpaRepository<Goals, Long> {
    
    Optional<Goals> findByGoalsId(Long goalsId);
}

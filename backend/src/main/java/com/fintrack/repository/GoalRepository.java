package com.fintrack.repository;

import com.fintrack.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdOrderByStartDate(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}

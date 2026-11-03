package com.fintrack.repository;

import com.fintrack.model.Income;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByUserIdOrderByDateDesc(Long userId);

    Optional<Income> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}

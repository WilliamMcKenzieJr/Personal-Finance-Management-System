package com.fintrack.repository;

import com.fintrack.model.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByUserIdOrderByLabel(Long userId);

    Optional<Bill> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}

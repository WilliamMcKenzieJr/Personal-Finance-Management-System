package com.fintrack.repository;

import com.fintrack.model.BankStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankStatementRepository extends JpaRepository<BankStatement, Long> {

    List<BankStatement> findByUserIdOrderByUploadedAtDesc(Long userId);

    void deleteByUserId(Long userId);
}

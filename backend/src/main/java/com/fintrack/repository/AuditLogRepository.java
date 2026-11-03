package com.fintrack.repository;

import com.fintrack.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop500ByOrderByCreatedAtDescIdDesc();

    List<AuditLog> findTop500ByTargetUsernameContainingIgnoreCaseOrderByCreatedAtDescIdDesc(String username);

    List<AuditLog> findByTargetUserIdOrderByCreatedAtDescIdDesc(Long targetUserId);
}

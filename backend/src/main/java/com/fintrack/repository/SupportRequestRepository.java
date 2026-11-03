package com.fintrack.repository;

import com.fintrack.model.Enums.SupportStatus;
import com.fintrack.model.SupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findByStatusOrderByCreatedAt(SupportStatus status);

    List<SupportRequest> findTop200ByOrderByCreatedAtDesc();

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);
}

package com.fintrack.repository;

import com.fintrack.model.Enums.RequestStatus;
import com.fintrack.model.ProfileChangeRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {

    List<ProfileChangeRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    List<ProfileChangeRequest> findByStatusOrderByCreatedAt(RequestStatus status);

    @EntityGraph(attributePaths = "user")
    List<ProfileChangeRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByUserIdAndStatus(Long userId, RequestStatus status);

    void deleteByUserId(Long userId);
}

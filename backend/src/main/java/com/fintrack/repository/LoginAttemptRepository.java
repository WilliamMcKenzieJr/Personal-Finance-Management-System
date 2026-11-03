package com.fintrack.repository;

import com.fintrack.model.Enums.LoginOutcome;
import com.fintrack.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    List<LoginAttempt> findTop500ByOrderByCreatedAtDescIdDesc();

    List<LoginAttempt> findTop500ByUsernameEnteredContainingIgnoreCaseOrderByCreatedAtDescIdDesc(String username);

    List<LoginAttempt> findTop500ByOutcomeOrderByCreatedAtDescIdDesc(LoginOutcome outcome);

    List<LoginAttempt> findTop500ByUsernameEnteredContainingIgnoreCaseAndOutcomeOrderByCreatedAtDescIdDesc(
            String username, LoginOutcome outcome);
}

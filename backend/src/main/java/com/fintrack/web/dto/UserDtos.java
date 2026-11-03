package com.fintrack.web.dto;

import com.fintrack.model.AuditLog;
import com.fintrack.model.Enums.AuditAction;
import com.fintrack.model.Enums.LoginOutcome;
import com.fintrack.model.Enums.RequestStatus;
import com.fintrack.model.Enums.Role;
import com.fintrack.model.Enums.SupportStatus;
import com.fintrack.model.LoginAttempt;
import com.fintrack.model.ProfileChangeRequest;
import com.fintrack.model.User;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Requests and responses for accounts, profiles and administration. */
public final class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9_.-]+",
                    message = "may only contain letters, numbers, dots, dashes and underscores") String username,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    /** {@code lockStatus} is NONE, TEMPORARY (10-minute lock running) or LOCKED (needs an administrator). */
    public record UserResponse(Long id, String username, String email, String firstName, String lastName,
                               Role role, BigDecimal savingsPercent, Instant createdAt, String lockStatus,
                               Instant lockedUntil) {
        public static UserResponse from(User u) {
            boolean temporary = u.getLockedUntil() != null && Instant.now().isBefore(u.getLockedUntil());
            String lockStatus = u.isAccountLocked() ? "LOCKED" : temporary ? "TEMPORARY" : "NONE";
            return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFirstName(), u.getLastName(),
                    u.getRole(), u.getSavingsPercent(), u.getCreatedAt(), lockStatus,
                    temporary ? u.getLockedUntil() : null);
        }
    }

    public record SupportRequestCreate(@NotBlank @Size(max = 50) String username,
                                       @NotBlank @Email @Size(max = 120) String email,
                                       @NotBlank @Size(max = 1000) String message) {
    }

    public record SupportRequestResponse(Long id, Instant createdAt, String username, String email, String message,
                                         String ipAddress, SupportStatus status, String resolvedBy,
                                         Instant resolvedAt, Long matchingUserId, String matchingUserLockStatus) {
    }

    public record AuditLogResponse(Long id, Instant createdAt, String actorUsername, Long targetUserId,
                                   String targetUsername, AuditAction action, String details) {
        public static AuditLogResponse from(AuditLog a) {
            return new AuditLogResponse(a.getId(), a.getCreatedAt(), a.getActorUsername(), a.getTargetUserId(),
                    a.getTargetUsername(), a.getAction(), a.getDetails());
        }
    }

    public record LoginAttemptResponse(Long id, Instant createdAt, String usernameEntered, Long userId,
                                       String ipAddress, String userAgent, LoginOutcome outcome, String detail) {
        public static LoginAttemptResponse from(LoginAttempt a) {
            return new LoginAttemptResponse(a.getId(), a.getCreatedAt(), a.getUsernameEntered(), a.getUserId(),
                    a.getIpAddress(), a.getUserAgent(), a.getOutcome(), a.getDetail());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }

    public record SavingsRequest(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percent) {
    }

    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    /** A member's requested profile changes; leave a field blank to keep it as is. */
    public record ChangeRequestCreate(@Size(max = 60) String firstName,
                                      @Size(max = 60) String lastName,
                                      @Email @Size(max = 120) String email,
                                      @Size(max = 500) String note) {
    }

    public record ChangeRequestResponse(Long id, Long userId, String username, String currentFirstName,
                                        String currentLastName, String currentEmail, String firstName,
                                        String lastName, String email, String note, RequestStatus status,
                                        Instant createdAt, Instant reviewedAt, String reviewedBy,
                                        String reviewNote) {
        public static ChangeRequestResponse from(ProfileChangeRequest r) {
            User u = r.getUser();
            return new ChangeRequestResponse(r.getId(), u.getId(), u.getUsername(), u.getFirstName(),
                    u.getLastName(), u.getEmail(), r.getFirstName(), r.getLastName(), r.getEmail(), r.getNote(),
                    r.getStatus(), r.getCreatedAt(), r.getReviewedAt(), r.getReviewedBy(), r.getReviewNote());
        }
    }

    public record ReviewRequest(@Size(max = 500) String note) {
    }

    public record AdminUserUpdate(@NotBlank @Size(max = 60) String firstName,
                                  @NotBlank @Size(max = 60) String lastName,
                                  @NotBlank @Email @Size(max = 120) String email) {
    }

    public record RoleUpdate(@NotNull Role role) {
    }
}

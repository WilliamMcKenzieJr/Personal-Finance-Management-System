package com.fintrack.service;

import com.fintrack.model.Enums.*;
import com.fintrack.model.ProfileChangeRequest;
import com.fintrack.model.SupportRequest;
import com.fintrack.model.User;
import com.fintrack.repository.*;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.UserDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Profile administration. Administrators manage members; the owner manages members and
 * administrators and decides who is an administrator. Nobody here can see financial records.
 * Every change is written to the audit log.
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final ProfileChangeRequestRepository changeRequests;
    private final BillRepository bills;
    private final IncomeRepository incomes;
    private final ExpenseRepository expenses;
    private final GoalRepository goals;
    private final NotificationRepository notificationRepo;
    private final BankStatementRepository statements;
    private final AuditLogRepository auditLog;
    private final LoginAttemptRepository loginAttempts;
    private final SupportRequestRepository supportRequests;
    private final NotificationService notifications;
    private final AuditService audit;

    public AdminService(UserRepository users, ProfileChangeRequestRepository changeRequests, BillRepository bills,
                        IncomeRepository incomes, ExpenseRepository expenses, GoalRepository goals,
                        NotificationRepository notificationRepo, BankStatementRepository statements,
                        AuditLogRepository auditLog, LoginAttemptRepository loginAttempts,
                        SupportRequestRepository supportRequests, NotificationService notifications,
                        AuditService audit) {
        this.users = users;
        this.changeRequests = changeRequests;
        this.bills = bills;
        this.incomes = incomes;
        this.expenses = expenses;
        this.goals = goals;
        this.notificationRepo = notificationRepo;
        this.statements = statements;
        this.auditLog = auditLog;
        this.loginAttempts = loginAttempts;
        this.supportRequests = supportRequests;
        this.notifications = notifications;
        this.audit = audit;
    }

    public List<UserResponse> listUsers() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(User::getRole).reversed().thenComparing(User::getUsername))
                .map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse updateUser(User actor, Long id, AdminUserUpdate r) {
        User target = findUser(id);
        requireCanManage(actor, target);
        String email = r.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(target.getEmail()) && users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Another account already uses that email.");
        }
        AuditService.Changes changes = new AuditService.Changes()
                .add("first name", target.getFirstName(), r.firstName().trim())
                .add("last name", target.getLastName(), r.lastName().trim())
                .add("email", target.getEmail(), email);
        target.setFirstName(r.firstName().trim());
        target.setLastName(r.lastName().trim());
        target.setEmail(email);
        if (!changes.isEmpty()) {
            audit.record(actor, target, AuditAction.PROFILE_UPDATED, changes.toString());
        }
        return UserResponse.from(target);
    }

    /** Deletes the account and everything that belongs to it. Its audit and sign-in history is kept. */
    @Transactional
    public void deleteUser(User actor, Long id) {
        User target = findUser(id);
        if (target.getId().equals(actor.getId())) {
            throw ApiException.badRequest("You can't delete your own account here.");
        }
        requireCanManage(actor, target);
        audit.record(actor, target, AuditAction.ACCOUNT_DELETED,
                target.getFirstName() + " " + target.getLastName() + " <" + target.getEmail() + ">, role "
                        + target.getRole());
        expenses.deleteByUserId(id);
        incomes.deleteByUserId(id);
        bills.deleteByUserId(id);
        goals.deleteByUserId(id);
        notificationRepo.deleteByUserId(id);
        statements.deleteByUserId(id);
        changeRequests.deleteByUserId(id);
        users.delete(target);
    }

    /**
     * Locks an account until it is unlocked. Admins can lock members; the owner can lock members
     * and admins. The owner's account can't be locked, since nobody could unlock it.
     */
    @Transactional
    public UserResponse lock(User actor, Long id, String reason) {
        User target = findUser(id);
        if (target.getId().equals(actor.getId())) {
            throw ApiException.badRequest("You can't lock your own account.");
        }
        if (target.getRole() == Role.OWNER) {
            throw ApiException.forbidden("The owner's account can't be locked.");
        }
        requireCanManage(actor, target);
        if (target.isAccountLocked()) {
            throw ApiException.badRequest("This account is already locked.");
        }
        target.setAccountLocked(true);
        target.setLockedUntil(null);
        String note = AccountService.blankToNull(reason);
        audit.record(actor, target, AuditAction.ACCOUNT_LOCKED,
                "Locked by " + actor.getUsername() + (note == null ? "" : ": " + note));
        return UserResponse.from(target);
    }

    /** Lifts a 10-minute lock or a full account lock. */
    @Transactional
    public UserResponse unlock(User actor, Long id) {
        User target = findUser(id);
        requireCanManage(actor, target);
        boolean temporary = target.getLockedUntil() != null && Instant.now().isBefore(target.getLockedUntil());
        if (!target.isAccountLocked() && !temporary) {
            throw ApiException.badRequest("This account isn't locked.");
        }
        String what = target.isAccountLocked() ? "account lock" : "temporary lock";
        target.clearLoginFailures();
        audit.record(actor, target, AuditAction.ACCOUNT_UNLOCKED, "Removed " + what);
        return UserResponse.from(target);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> listRequests(RequestStatus status) {
        List<ProfileChangeRequest> list = status == null
                ? changeRequests.findAllByOrderByCreatedAtDesc()
                : changeRequests.findByStatusOrderByCreatedAt(status);
        return list.stream().map(ChangeRequestResponse::from).toList();
    }

    @Transactional
    public ChangeRequestResponse review(User actor, Long requestId, boolean approve, String note) {
        ProfileChangeRequest request = changeRequests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request"));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw ApiException.badRequest("This request has already been reviewed.");
        }
        User target = request.getUser();
        requireCanManage(actor, target);
        String changes = AccountService.requestedChanges(target, request).toString();
        if (approve) {
            if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(target.getEmail())
                    && users.existsByEmailIgnoreCase(request.getEmail())) {
                throw ApiException.conflict("Another account already uses the requested email.");
            }
            if (request.getFirstName() != null) target.setFirstName(request.getFirstName());
            if (request.getLastName() != null) target.setLastName(request.getLastName());
            if (request.getEmail() != null) target.setEmail(request.getEmail());
        }
        request.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        request.setReviewedAt(Instant.now());
        request.setReviewedBy(actor.getUsername());
        request.setReviewNote(AccountService.blankToNull(note));
        audit.record(actor, target,
                approve ? AuditAction.CHANGE_REQUEST_APPROVED : AuditAction.CHANGE_REQUEST_REJECTED,
                changes + (request.getReviewNote() == null ? "" : " (note: " + request.getReviewNote() + ")"));
        notifications.notify(target.getId(), NotificationType.PROFILE, "profile-request-" + request.getId(),
                "Your profile change request was " + (approve ? "approved" : "rejected")
                        + (request.getReviewNote() == null ? "." : ": " + request.getReviewNote()));
        return ChangeRequestResponse.from(request);
    }

    /** Owner only: promote a member to administrator, or return an administrator to member. */
    @Transactional
    public UserResponse setRole(User actor, Long id, Role role) {
        if (actor.getRole() != Role.OWNER) {
            throw ApiException.forbidden("Only the owner can change roles.");
        }
        if (role == Role.OWNER) {
            throw ApiException.badRequest("There can only be one owner.");
        }
        User target = findUser(id);
        if (target.getRole() == Role.OWNER) {
            throw ApiException.badRequest("The owner's role can't be changed.");
        }
        if (target.getRole() != role) {
            audit.record(actor, target, AuditAction.ROLE_CHANGED, "role: " + target.getRole() + " → " + role);
            target.setRole(role);
        }
        return UserResponse.from(target);
    }

    public List<AuditLogResponse> auditLog(String username) {
        var entries = username == null || username.isBlank()
                ? auditLog.findTop500ByOrderByCreatedAtDescIdDesc()
                : auditLog.findTop500ByTargetUsernameContainingIgnoreCaseOrderByCreatedAtDescIdDesc(username.trim());
        return entries.stream().map(AuditLogResponse::from).toList();
    }

    public List<LoginAttemptResponse> loginAttempts(String username, LoginOutcome outcome) {
        boolean byName = username != null && !username.isBlank();
        var entries = byName && outcome != null
                ? loginAttempts.findTop500ByUsernameEnteredContainingIgnoreCaseAndOutcomeOrderByCreatedAtDescIdDesc(
                        username.trim(), outcome)
                : byName ? loginAttempts.findTop500ByUsernameEnteredContainingIgnoreCaseOrderByCreatedAtDescIdDesc(
                        username.trim())
                : outcome != null ? loginAttempts.findTop500ByOutcomeOrderByCreatedAtDescIdDesc(outcome)
                : loginAttempts.findTop500ByOrderByCreatedAtDescIdDesc();
        return entries.stream().map(LoginAttemptResponse::from).toList();
    }

    public List<SupportRequestResponse> supportRequests(SupportStatus status) {
        List<SupportRequest> list = status == null
                ? supportRequests.findTop200ByOrderByCreatedAtDesc()
                : supportRequests.findByStatusOrderByCreatedAt(status);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SupportRequestResponse resolveSupport(User actor, Long id) {
        SupportRequest request = supportRequests.findById(id).orElseThrow(() -> ApiException.notFound("Request"));
        request.setStatus(SupportStatus.RESOLVED);
        request.setResolvedBy(actor.getUsername());
        request.setResolvedAt(Instant.now());
        return toResponse(request);
    }

    /** Shows which account a support request is about, and whether it's locked, so admins can act on it. */
    private SupportRequestResponse toResponse(SupportRequest r) {
        var match = users.findByUsernameIgnoreCase(r.getUsername());
        return new SupportRequestResponse(r.getId(), r.getCreatedAt(), r.getUsername(), r.getEmail(), r.getMessage(),
                r.getIpAddress(), r.getStatus(), r.getResolvedBy(), r.getResolvedAt(),
                match.map(User::getId).orElse(null),
                match.map(u -> UserResponse.from(u).lockStatus()).orElse(null));
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    /** Admins may manage members; the owner may manage members and admins. */
    private static void requireCanManage(User actor, User target) {
        boolean allowed = switch (actor.getRole()) {
            case OWNER -> target.getRole() != Role.OWNER || target.getId().equals(actor.getId());
            case ADMIN -> target.getRole() == Role.MEMBER;
            case MEMBER -> false;
        };
        if (!allowed) {
            throw ApiException.forbidden("You don't have permission to manage this account.");
        }
    }
}

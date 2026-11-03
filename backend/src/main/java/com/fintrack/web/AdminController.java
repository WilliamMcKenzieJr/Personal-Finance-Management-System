package com.fintrack.web;

import com.fintrack.model.Enums.LoginOutcome;
import com.fintrack.model.Enums.RequestStatus;
import com.fintrack.model.Enums.SupportStatus;
import com.fintrack.security.CurrentUser;
import com.fintrack.service.AdminService;
import com.fintrack.web.dto.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Profile administration. /api/admin needs ADMIN or OWNER; /api/owner needs OWNER (see SecurityConfig). */
@RestController
@RequestMapping("/api")
public class AdminController {

    private final AdminService admin;
    private final CurrentUser currentUser;

    public AdminController(AdminService admin, CurrentUser currentUser) {
        this.admin = admin;
        this.currentUser = currentUser;
    }

    @GetMapping("/admin/users")
    public List<UserResponse> users() {
        return admin.listUsers();
    }

    @PutMapping("/admin/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserUpdate request) {
        return admin.updateUser(currentUser.get(), id, request);
    }

    @DeleteMapping("/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        admin.deleteUser(currentUser.get(), id);
    }

    @PostMapping("/admin/users/{id}/lock")
    public UserResponse lock(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest body) {
        return admin.lock(currentUser.get(), id, body == null ? null : body.note());
    }

    @PostMapping("/admin/users/{id}/unlock")
    public UserResponse unlock(@PathVariable Long id) {
        return admin.unlock(currentUser.get(), id);
    }

    @GetMapping("/admin/audit-log")
    public List<AuditLogResponse> auditLog(@RequestParam(required = false) String username) {
        return admin.auditLog(username);
    }

    @GetMapping("/admin/login-attempts")
    public List<LoginAttemptResponse> loginAttempts(@RequestParam(required = false) String username,
                                                    @RequestParam(required = false) LoginOutcome outcome) {
        return admin.loginAttempts(username, outcome);
    }

    @GetMapping("/admin/support-requests")
    public List<SupportRequestResponse> supportRequests(@RequestParam(required = false) SupportStatus status) {
        return admin.supportRequests(status);
    }

    @PostMapping("/admin/support-requests/{id}/resolve")
    public SupportRequestResponse resolveSupport(@PathVariable Long id) {
        return admin.resolveSupport(currentUser.get(), id);
    }

    @GetMapping("/admin/change-requests")
    public List<ChangeRequestResponse> requests(@RequestParam(required = false) RequestStatus status) {
        return admin.listRequests(status);
    }

    @PostMapping("/admin/change-requests/{id}/approve")
    public ChangeRequestResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest body) {
        return admin.review(currentUser.get(), id, true, body == null ? null : body.note());
    }

    @PostMapping("/admin/change-requests/{id}/reject")
    public ChangeRequestResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest body) {
        return admin.review(currentUser.get(), id, false, body == null ? null : body.note());
    }

    @PutMapping("/owner/users/{id}/role")
    public UserResponse setRole(@PathVariable Long id, @Valid @RequestBody RoleUpdate request) {
        return admin.setRole(currentUser.get(), id, request.role());
    }
}

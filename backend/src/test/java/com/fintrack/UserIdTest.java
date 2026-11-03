package com.fintrack;

import com.fintrack.service.AccountService;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.UserDtos.RegisterRequest;
import com.fintrack.web.dto.UserDtos.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every user has an id that is never null and never shared, enforced by the database itself. */
@SpringBootTest
class UserIdTest {

    @Autowired
    AccountService accounts;

    @Autowired
    JdbcTemplate jdbc;

    private UserResponse register(String name) {
        return accounts.register(new RegisterRequest(name, name + "@test.local", "password123", "First", "Last")).user();
    }

    @Test
    void everyRegisteredUserGetsADistinctNonNullId() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            Long id = register("idtest" + i).id();
            assertThat(id).isNotNull();
            assertThat(ids.add(id)).as("id %s is unique", id).isTrue();
        }
    }

    @Test
    void idColumnIsNotNullablePrimaryKey() {
        String nullable = jdbc.queryForObject(
                "select IS_NULLABLE from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME = 'APP_USERS' and COLUMN_NAME = 'ID'",
                String.class);
        assertThat(nullable).isEqualTo("NO");
    }

    @Test
    void databaseRejectsDuplicateId() {
        Long existing = register("dupid").id();
        assertThatThrownBy(() -> insertRaw(existing, "dupid2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNullId() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into app_users (id, username, email, password_hash, first_name, last_name, role,
                                       savings_percent, created_at, failed_login_attempts, temporary_lock_used,
                                       account_locked)
                values (null, 'nullid', 'nullid@test.local', 'x', 'F', 'L', 'MEMBER', 0, current_timestamp,
                        0, false, false)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateUsernameIsRejected() {
        register("sameuser");
        assertThatThrownBy(() -> accounts.register(
                new RegisterRequest("SameUser", "other@test.local", "password123", "F", "L")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("username");
    }

    private void insertRaw(Long id, String username) {
        jdbc.update("""
                insert into app_users (id, username, email, password_hash, first_name, last_name, role,
                                       savings_percent, created_at, failed_login_attempts, temporary_lock_used,
                                       account_locked)
                values (?, ?, ?, 'x', 'F', 'L', 'MEMBER', 0, current_timestamp, 0, false, false)
                """, id, username, username + "@test.local");
    }
}

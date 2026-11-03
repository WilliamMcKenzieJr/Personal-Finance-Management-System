package com.fintrack.service;

import com.fintrack.model.AuditLog;
import com.fintrack.model.Enums.AuditAction;
import com.fintrack.model.User;
import com.fintrack.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Records every change to a user's profile or account. */
@Service
public class AuditService {

    private final AuditLogRepository log;

    public AuditService(AuditLogRepository log) {
        this.log = log;
    }

    /** @param actor who made the change, or null for an automatic change */
    public void record(User actor, User target, AuditAction action, String details) {
        AuditLog entry = new AuditLog();
        if (actor != null) {
            entry.setActorId(actor.getId());
            entry.setActorUsername(actor.getUsername());
        }
        entry.setTargetUserId(target.getId());
        entry.setTargetUsername(target.getUsername());
        entry.setAction(action);
        entry.setDetails(details == null || details.length() <= 1000 ? details : details.substring(0, 1000));
        log.save(entry);
    }

    /** Builds "field: old → new; ..." for the fields that actually changed. */
    public static final class Changes {
        private final List<String> parts = new ArrayList<>();

        public Changes add(String field, Object before, Object after) {
            if (!Objects.equals(before, after)) {
                parts.add(field + ": " + before + " → " + after);
            }
            return this;
        }

        public boolean isEmpty() {
            return parts.isEmpty();
        }

        @Override
        public String toString() {
            return String.join("; ", parts);
        }
    }
}

package com.fintrack.service;

import com.fintrack.model.Bill;
import com.fintrack.model.Enums.NotificationType;
import com.fintrack.model.Goal;
import com.fintrack.model.Notification;
import com.fintrack.model.User;
import com.fintrack.repository.BillRepository;
import com.fintrack.repository.GoalRepository;
import com.fintrack.repository.NotificationRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * In-app notifications: bill reminders, savings warnings, reached goals and profile request
 * outcomes. Checks run daily on a schedule and again whenever a member opens the app.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final BillRepository bills;
    private final GoalRepository goals;
    private final UserRepository users;
    private final int billDueDays;

    public NotificationService(NotificationRepository notifications, BillRepository bills, GoalRepository goals,
                               UserRepository users, @Value("${app.notifications.bill-due-days}") int billDueDays) {
        this.notifications = notifications;
        this.bills = bills;
        this.goals = goals;
        this.users = users;
        this.billDueDays = billDueDays;
    }

    /** Creates a notification unless one with the same key was already sent to this user. */
    @Transactional
    public void notify(Long userId, NotificationType type, String key, String message) {
        if (notifications.existsByUserIdAndDedupeKey(userId, key)) {
            return;
        }
        Notification n = new Notification();
        n.setUser(users.getReferenceById(userId));
        n.setType(type);
        n.setDedupeKey(key);
        n.setMessage(message);
        notifications.save(n);
    }

    @Scheduled(cron = "${app.notifications.cron}")
    public void runDailyChecks() {
        for (User user : users.findAll()) {
            try {
                checkUser(user.getId());
            } catch (RuntimeException e) {
                log.error("Notification check failed for user {}", user.getId(), e);
            }
        }
    }

    public void checkUser(Long userId) {
        checkBills(userId);
        checkGoals(userId);
    }

    public void checkBills(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(billDueDays);
        for (Bill bill : bills.findByUserIdOrderByLabel(userId)) {
            LocalDate due = bill.nextDueDate();
            if (due.isAfter(horizon)) {
                continue;
            }
            boolean overdue = due.isBefore(today);
            String when = overdue ? "was due on " + due + " and is overdue"
                    : due.equals(today) ? "is due today" : "is due on " + due;
            notify(userId, NotificationType.BILL_DUE,
                    "bill-" + bill.getId() + "-" + due + (overdue ? "-late" : "-soon"),
                    "Your " + bill.getLabel() + " bill (" + Money.format(bill.getAmountDue()) + ") " + when + ".");
        }
    }

    public void checkGoals(Long userId) {
        LocalDate today = LocalDate.now();
        for (Goal goal : goals.findByUserIdOrderByStartDate(userId)) {
            if (goal.isReached(today)) {
                notify(userId, NotificationType.GOAL_REACHED,
                        "goal-" + goal.getId() + "-" + goal.getTargetAmount().toPlainString(),
                        "You've saved " + Money.format(goal.getTargetAmount()) + " for \"" + goal.getName()
                                + "\". It's available to purchase!");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long userId) {
        return notifications.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from).toList();
    }

    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.setRead(true);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllRead(userId);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Notification n = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        notifications.delete(n);
    }
}

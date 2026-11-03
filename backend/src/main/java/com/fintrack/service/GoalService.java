package com.fintrack.service;

import com.fintrack.model.Goal;
import com.fintrack.repository.GoalRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.GoalRequest;
import com.fintrack.web.dto.FinanceDtos.GoalResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class GoalService {

    private final GoalRepository goals;
    private final UserRepository users;
    private final NotificationService notifications;

    public GoalService(GoalRepository goals, UserRepository users, NotificationService notifications) {
        this.goals = goals;
        this.users = users;
        this.notifications = notifications;
    }

    public List<GoalResponse> list(Long userId) {
        LocalDate today = LocalDate.now();
        return goals.findByUserIdOrderByStartDate(userId).stream().map(g -> GoalResponse.from(g, today)).toList();
    }

    @Transactional
    public GoalResponse create(Long userId, GoalRequest request) {
        Goal goal = new Goal();
        goal.setUser(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
        apply(goal, request);
        goals.save(goal);
        return saved(userId, goal);
    }

    @Transactional
    public GoalResponse update(Long userId, Long id, GoalRequest request) {
        Goal goal = find(userId, id);
        apply(goal, request);
        return saved(userId, goal);
    }

    /** Adds a one-off amount to the goal on top of the monthly plan. */
    @Transactional
    public GoalResponse contribute(Long userId, Long id, BigDecimal amount) {
        Goal goal = find(userId, id);
        goal.setExtraSaved(goal.getExtraSaved().add(amount));
        return saved(userId, goal);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        goals.delete(find(userId, id));
    }

    private GoalResponse saved(Long userId, Goal goal) {
        goals.flush();
        notifications.checkGoals(userId);
        return GoalResponse.from(goal, LocalDate.now());
    }

    private Goal find(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Goal"));
    }

    private void apply(Goal goal, GoalRequest r) {
        goal.setName(r.name().trim());
        goal.setTargetAmount(r.targetAmount());
        goal.setMonthlyContribution(r.monthlyContribution());
        if (r.startDate() != null) {
            goal.setStartDate(r.startDate());
        } else if (goal.getStartDate() == null) {
            goal.setStartDate(LocalDate.now());
        }
    }
}

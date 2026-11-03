package com.fintrack.service;

import com.fintrack.model.Income;
import com.fintrack.repository.IncomeRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.IncomeRequest;
import com.fintrack.web.dto.FinanceDtos.IncomeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IncomeService {

    private final IncomeRepository incomes;
    private final UserRepository users;

    public IncomeService(IncomeRepository incomes, UserRepository users) {
        this.incomes = incomes;
        this.users = users;
    }

    public List<IncomeResponse> list(Long userId) {
        return incomes.findByUserIdOrderByDateDesc(userId).stream().map(IncomeResponse::from).toList();
    }

    @Transactional
    public IncomeResponse create(Long userId, IncomeRequest request) {
        Income income = new Income();
        income.setUser(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
        apply(income, request);
        return IncomeResponse.from(incomes.save(income));
    }

    @Transactional
    public IncomeResponse update(Long userId, Long id, IncomeRequest request) {
        Income income = find(userId, id);
        apply(income, request);
        return IncomeResponse.from(income);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        incomes.delete(find(userId, id));
    }

    private Income find(Long userId, Long id) {
        return incomes.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Income"));
    }

    private void apply(Income income, IncomeRequest r) {
        if (r.endDate() != null) {
            if (r.frequency() == null) {
                throw ApiException.badRequest("Only recurring income can have an end date.");
            }
            if (r.endDate().isBefore(r.date())) {
                throw ApiException.badRequest("The end date must be on or after the first payment date.");
            }
        }
        income.setSource(r.source().trim());
        income.setAmount(r.amount());
        income.setDate(r.date());
        income.setFrequency(r.frequency());
        income.setEndDate(r.endDate());
    }
}

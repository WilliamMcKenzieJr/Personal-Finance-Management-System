package com.fintrack.repository;

import com.fintrack.model.Enums.ExpenseCategory;
import com.fintrack.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdOrderByDateDescIdDesc(Long userId);

    List<Expense> findByUserIdAndDateBetweenOrderByDateDesc(Long userId, LocalDate from, LocalDate to);

    List<Expense> findByUserIdAndCategoryAndDateBetweenOrderByDateDesc(
            Long userId, ExpenseCategory category, LocalDate from, LocalDate to);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    /** The most recent "Mark paid" payment for a bill (down payments are LARGE_PURCHASE, not BILLS). */
    Optional<Expense> findFirstByBillIdAndCategoryOrderByDateDescIdDesc(Long billId, ExpenseCategory category);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.user.id = :userId and e.date <= :asOf")
    BigDecimal sumUpTo(@Param("userId") Long userId, @Param("asOf") LocalDate asOf);

    @Query("select coalesce(sum(e.amount), 0) from Expense e "
            + "where e.user.id = :userId and e.date between :from and :to")
    BigDecimal sumBetween(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e.category, sum(e.amount) from Expense e "
            + "where e.user.id = :userId and e.date between :from and :to group by e.category")
    List<Object[]> sumByCategory(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    void deleteByUserId(Long userId);
}

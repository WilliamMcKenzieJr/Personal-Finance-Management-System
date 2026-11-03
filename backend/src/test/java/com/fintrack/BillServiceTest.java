package com.fintrack;

import com.fintrack.model.Enums.BillType;
import com.fintrack.model.Enums.ExpenseCategory;
import com.fintrack.model.Recurrence;
import com.fintrack.service.AccountService;
import com.fintrack.service.BillService;
import com.fintrack.service.ExpenseService;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.BillRequest;
import com.fintrack.web.dto.FinanceDtos.BillResponse;
import com.fintrack.web.dto.FinanceDtos.ExpenseResponse;
import com.fintrack.web.dto.UserDtos.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BillServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    AccountService accounts;

    @Autowired
    BillService bills;

    @Autowired
    ExpenseService expenses;

    Long userId;

    @BeforeEach
    void newUser() {
        userId = register("billuser");
    }

    private Long register(String prefix) {
        String name = prefix + COUNTER.incrementAndGet();
        return accounts.register(new RegisterRequest(name, name + "@test.local", "password123", "F", "L")).user().id();
    }

    private BillRequest bill(LocalDate due, Recurrence recurrence, String downPayment) {
        return new BillRequest("Car", BillType.LOAN, new BigDecimal("300"), due, recurrence,
                downPayment == null ? null : new BigDecimal(downPayment));
    }

    private BillRequest monthlyIn(int days) {
        return bill(LocalDate.now().plusDays(days), Recurrence.MONTHLY, null);
    }

    private long billPayments() {
        return expenses.list(userId).stream().filter(e -> e.category() == ExpenseCategory.BILLS).count();
    }

    @Test
    void downPaymentIsRecordedAsLargePurchaseCost() {
        bills.create(userId, bill(LocalDate.now().plusDays(5), Recurrence.MONTHLY, "1000"));

        assertThat(expenses.list(userId)).singleElement().satisfies(e -> {
            assertThat(e.category()).isEqualTo(ExpenseCategory.LARGE_PURCHASE);
            assertThat(e.amount()).isEqualByComparingTo("1000");
        });
    }

    @Test
    void cannotMarkPaidTwiceInTheSamePeriod() {
        BillResponse created = bills.create(userId, monthlyIn(5));
        assertThat(created.canPay()).isTrue();

        BillResponse paid = bills.pay(userId, created.id());
        assertThat(paid.canPay()).isFalse();
        assertThat(paid.payableFrom()).isAfter(LocalDate.now());

        assertThatThrownBy(() -> bills.pay(userId, created.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already marked paid");
        assertThat(billPayments()).isEqualTo(1);
    }

    @Test
    void weeklyBillCannotBePaidTwoWeeksAhead() {
        BillResponse created = bills.create(userId, bill(LocalDate.now().plusDays(3), Recurrence.WEEKLY, null));
        bills.pay(userId, created.id());

        assertThatThrownBy(() -> bills.pay(userId, created.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void overduePeriodsCanBeCaughtUpButNotPaidAhead() {
        BillResponse created = bills.create(userId, monthlyIn(-40));

        bills.pay(userId, created.id());          // period due 40 days ago
        BillResponse second = bills.pay(userId, created.id()); // period due ~10 days ago, still overdue
        assertThat(second.overdue()).isFalse();
        assertThat(second.canPay()).isFalse();    // next period is in the future and already paid this month
        assertThatThrownBy(() -> bills.pay(userId, created.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void undoReversesTheLastPayment() {
        BillResponse created = bills.create(userId, monthlyIn(5));
        bills.pay(userId, created.id());

        BillResponse undone = bills.undoPayment(userId, created.id());

        assertThat(undone.periodsPaid()).isZero();
        assertThat(undone.nextDueDate()).isEqualTo(created.nextDueDate());
        assertThat(undone.canPay()).isTrue();
        assertThat(undone.lastPaidDate()).isNull();
        assertThat(billPayments()).isZero();
    }

    @Test
    void undoWithNoPaymentsFails() {
        BillResponse created = bills.create(userId, monthlyIn(5));
        assertThatThrownBy(() -> bills.undoPayment(userId, created.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no payment");
    }

    @Test
    void deletingThePaymentFromCostsAlsoRollsBackTheBill() {
        BillResponse created = bills.create(userId, monthlyIn(5));
        bills.pay(userId, created.id());
        ExpenseResponse payment = expenses.list(userId).get(0);

        expenses.delete(userId, payment.id());

        BillResponse after = bills.list(userId).get(0);
        assertThat(after.periodsPaid()).isZero();
        assertThat(after.canPay()).isTrue();
    }

    @Test
    void removingDownPaymentRemovesItsCost() {
        BillResponse created = bills.create(userId, bill(LocalDate.now().plusDays(5), Recurrence.MONTHLY, "800"));
        bills.update(userId, created.id(), bill(created.nextDueDate(), Recurrence.MONTHLY, null));

        assertThat(expenses.list(userId)).isEmpty();
    }

    @Test
    void otherMembersCannotPayYourBill() {
        BillResponse created = bills.create(userId, monthlyIn(5));
        Long otherId = register("intruder");

        assertThatThrownBy(() -> bills.pay(otherId, created.id()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }
}

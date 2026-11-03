package com.fintrack.service;

import com.fintrack.model.BankStatement;
import com.fintrack.model.Enums.ExpenseCategory;
import com.fintrack.model.Expense;
import com.fintrack.model.Income;
import com.fintrack.model.User;
import com.fintrack.repository.BankStatementRepository;
import com.fintrack.repository.ExpenseRepository;
import com.fintrack.repository.IncomeRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.web.ApiException;
import com.fintrack.web.dto.FinanceDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Bank statement upload in two steps: {@link #preview} reads a CSV export and guesses each
 * row's category; the member reviews it, then {@link #importRows} saves the rows they kept.
 */
@Service
public class StatementService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("M-d-yyyy"));

    private static final Map<ExpenseCategory, List<String>> KEYWORDS = Map.of(
            ExpenseCategory.GROCERIES, List.of("grocery", "market", "walmart", "kroger", "aldi", "costco",
                    "safeway", "whole foods", "trader joe", "publix", "food"),
            ExpenseCategory.TRANSPORTATION, List.of("uber", "lyft", "gas", "fuel", "shell", "chevron", "exxon",
                    "bp ", "parking", "transit", "metro", "toll", "airline"),
            ExpenseCategory.ENTERTAINMENT, List.of("netflix", "spotify", "hulu", "disney", "cinema", "movie",
                    "theater", "steam", "playstation", "xbox", "concert", "restaurant", "bar "),
            ExpenseCategory.BILLS, List.of("electric", "water", "utility", "rent", "insurance", "internet",
                    "comcast", "verizon", "at&t", "t-mobile", "loan", "mortgage", "subscription"));

    private final BankStatementRepository statements;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final UserRepository users;
    private final FinanceService finance;

    public StatementService(BankStatementRepository statements, ExpenseRepository expenses, IncomeRepository incomes,
                            UserRepository users, FinanceService finance) {
        this.statements = statements;
        this.expenses = expenses;
        this.incomes = incomes;
        this.users = users;
        this.finance = finance;
    }

    public List<StatementResponse> list(Long userId) {
        return statements.findByUserIdOrderByUploadedAtDesc(userId).stream().map(StatementResponse::from).toList();
    }

    public StatementPreview preview(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "statement.csv" : file.getOriginalFilename();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw ApiException.badRequest("Please upload a CSV export of your bank statement.");
        }
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read.");
        }
        if (lines.isEmpty()) {
            throw ApiException.badRequest("The file is empty.");
        }

        Columns columns = Columns.detect(splitCsv(lines.get(0)));
        List<StatementRow> rows = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (int i = columns.hasHeader ? 1 : 0; i < lines.size(); i++) {
            String line = lines.get(i);
            try {
                rows.add(parseRow(splitCsv(line), columns));
            } catch (RuntimeException e) {
                skipped.add(line);
            }
        }
        if (rows.isEmpty()) {
            throw ApiException.badRequest("No transactions were found. Expected columns: Date, Description, Amount.");
        }
        return new StatementPreview(name, rows, skipped);
    }

    @Transactional
    public StatementResponse importRows(Long userId, StatementImportRequest request) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        BankStatement statement = new BankStatement();
        statement.setUser(user);
        statement.setFileName(request.fileName());
        statements.save(statement);

        int expenseCount = 0;
        int incomeCount = 0;
        Set<YearMonth> months = new HashSet<>();
        for (StatementRow row : request.rows()) {
            if (row.kind() == RowKind.INCOME) {
                Income income = new Income();
                income.setUser(user);
                income.setSource(row.description());
                income.setAmount(row.amount());
                income.setDate(row.date());
                income.setStatementId(statement.getId());
                incomes.save(income);
                incomeCount++;
            } else {
                if (row.category() == null) {
                    throw ApiException.badRequest("Choose a category for \"" + row.description() + "\".");
                }
                Expense expense = new Expense();
                expense.setUser(user);
                expense.setDescription(row.description());
                expense.setAmount(row.amount());
                expense.setDate(row.date());
                expense.setCategory(row.category());
                expense.setStatementId(statement.getId());
                expenses.save(expense);
                months.add(YearMonth.from(row.date()));
                expenseCount++;
            }
        }
        statement.setExpensesImported(expenseCount);
        statement.setIncomesImported(incomeCount);
        expenses.flush();
        months.forEach(m -> finance.checkSavings(userId, m.atDay(1)));
        return StatementResponse.from(statement);
    }

    private StatementRow parseRow(List<String> cells, Columns c) {
        LocalDate date = parseDate(cells.get(c.date));
        String description = c.description >= 0 && c.description < cells.size() ? cells.get(c.description).trim() : "";
        if (description.isEmpty()) {
            description = "Bank transaction";
        }
        if (description.length() > 150) {
            description = description.substring(0, 150);
        }

        BigDecimal signed;
        if (c.amount >= 0) {
            signed = parseAmount(cells.get(c.amount));
        } else {
            BigDecimal debit = cellAmount(cells, c.debit);
            BigDecimal credit = cellAmount(cells, c.credit);
            signed = credit.abs().subtract(debit.abs());
        }
        if (signed.signum() == 0) {
            throw new IllegalArgumentException("zero amount");
        }
        RowKind kind = signed.signum() < 0 ? RowKind.EXPENSE : RowKind.INCOME;
        ExpenseCategory category = kind == RowKind.EXPENSE ? guessCategory(description) : null;
        return new StatementRow(date, description, signed.abs(), kind, category);
    }

    private static BigDecimal cellAmount(List<String> cells, int index) {
        if (index < 0 || index >= cells.size() || cells.get(index).isBlank()) {
            return BigDecimal.ZERO;
        }
        return parseAmount(cells.get(index));
    }

    static ExpenseCategory guessCategory(String description) {
        String text = description.toLowerCase(Locale.ROOT) + " ";
        for (Map.Entry<ExpenseCategory, List<String>> entry : KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(text::contains)) {
                return entry.getKey();
            }
        }
        return null;
    }

    static LocalDate parseDate(String raw) {
        String value = raw.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("Unrecognised date: " + raw);
    }

    /** Accepts "$1,234.50", "-12.00" and accounting-style "(12.00)" for negatives. */
    static BigDecimal parseAmount(String raw) {
        String value = raw.trim().replace("$", "").replace(",", "").replace(" ", "");
        boolean negative = value.startsWith("(") && value.endsWith(")");
        if (negative) {
            value = value.substring(1, value.length() - 1);
        }
        BigDecimal amount = new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
        return negative ? amount.negate() : amount;
    }

    /** Splits one CSV line, honouring double-quoted fields. */
    static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    /** Where each field is in the file. Without a header row: Date, Description, Amount. */
    private static final class Columns {
        boolean hasHeader;
        int date = 0;
        int description = 1;
        int amount = 2;
        int debit = -1;
        int credit = -1;

        static Columns detect(List<String> firstRow) {
            Columns c = new Columns();
            List<String> names = firstRow.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
            if (names.stream().noneMatch(n -> n.contains("date"))) {
                return c;
            }
            c.hasHeader = true;
            c.date = c.description = c.amount = -1;
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i);
                if (c.date < 0 && n.contains("date")) {
                    c.date = i;
                } else if (c.description < 0 && (n.contains("description") || n.contains("memo")
                        || n.contains("payee") || n.contains("name") || n.contains("details"))) {
                    c.description = i;
                } else if (c.amount < 0 && n.contains("amount")) {
                    c.amount = i;
                } else if (n.contains("debit") || n.contains("withdrawal")) {
                    c.debit = i;
                } else if (n.contains("credit") || n.contains("deposit")) {
                    c.credit = i;
                }
            }
            if (c.amount < 0 && c.debit < 0 && c.credit < 0) {
                throw ApiException.badRequest("Couldn't find an Amount (or Debit/Credit) column in the header.");
            }
            return c;
        }
    }
}

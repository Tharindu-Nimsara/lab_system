package com.lab.backend.finance;

import com.lab.backend.auth.AppUser;
import com.lab.backend.auth.CurrentUserService;
import com.lab.backend.billing.InvoiceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final ExpenseRepository expenses;
    private final InvoiceRepository invoices;
    private final CurrentUserService currentUser;

    public record ExpenseRequest(@NotBlank String category,
                                 String description,
                                 @NotNull @Positive BigDecimal amount,
                                 @NotNull LocalDate expenseDate) {}

    @PostMapping("/expenses")
    public Expense addExpense(@Valid @RequestBody ExpenseRequest req) {
        AppUser user = currentUser.require();
        Expense e = new Expense();
        e.setBranchId(user.getBranchId());
        e.setCategory(req.category());
        e.setDescription(req.description());
        e.setAmount(req.amount());
        e.setExpenseDate(req.expenseDate());
        e.setEnteredBy(user.getId());
        return expenses.save(e);
    }

    @GetMapping("/expenses")
    public List<Expense> listExpenses(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenses.findByExpenseDateBetweenOrderByExpenseDateDesc(from, to);
    }

    public record Summary(LocalDate from, LocalDate to,
                          Map<String, BigDecimal> revenueByMethod,
                          Map<String, BigDecimal> revenueByCategory,
                          Map<String, BigDecimal> expensesByCategory,
                          BigDecimal totalRevenue,
                          BigDecimal totalExpenses,
                          BigDecimal net) {}

    @GetMapping("/daily")
    public Summary daily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return summarize(date, date);
    }

    @GetMapping("/monthly")
    public Summary monthly(@RequestParam String month) {
        YearMonth ym = YearMonth.parse(month);
        return summarize(ym.atDay(1), ym.atEndOfMonth());
    }

    private Summary summarize(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> byMethod = invoices.revenueByMethod(from, to).stream()
                .collect(Collectors.toMap(InvoiceRepository.MethodTotal::getMethod,
                        InvoiceRepository.MethodTotal::getTotal, (a, b) -> a, java.util.LinkedHashMap::new));
        Map<String, BigDecimal> byCategory = invoices.revenueByCategory(from, to).stream()
                .collect(Collectors.toMap(InvoiceRepository.CategoryTotal::getCategory,
                        InvoiceRepository.CategoryTotal::getTotal, (a, b) -> a, java.util.LinkedHashMap::new));
        Map<String, BigDecimal> expByCategory = expenses.byCategory(from, to).stream()
                .collect(Collectors.toMap(ExpenseRepository.CategoryAmount::getCategory,
                        ExpenseRepository.CategoryAmount::getAmount, (a, b) -> a, java.util.LinkedHashMap::new));

        BigDecimal totalRevenue = byMethod.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(from, to, byMethod, byCategory, expByCategory,
                totalRevenue, totalExpenses, totalRevenue.subtract(totalExpenses));
    }
}

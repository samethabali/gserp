package com.gscrm.repository;

import com.gscrm.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate from, LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate >= :from and e.expenseDate <= :to")
    BigDecimal sumByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate >= :from and e.expenseDate <= :to and e.category = :cat")
    BigDecimal sumByDateRangeAndCategory(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                         @Param("cat") com.gscrm.model.enums.ExpenseCategory cat);
}

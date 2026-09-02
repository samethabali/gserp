package com.gscrm.repository;

import com.gscrm.model.Payment;
import com.gscrm.model.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByCollectedAtBetweenOrderByCollectedAtAsc(LocalDateTime from, LocalDateTime to);

    List<Payment> findByCustomerPhoneOrderByCollectedAtDesc(String phone);

    Optional<Payment> findByAppointmentId(Long appointmentId);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.collectedAt >= :from and p.collectedAt < :to
              and p.method = :method
              and p.status = 'PAID'
            """)
    BigDecimal sumByMethodAndRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("method") PaymentMethod method);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.collectedAt >= :from and p.collectedAt < :to
              and p.status = 'PAID'
            """)
    BigDecimal sumCollectedInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}

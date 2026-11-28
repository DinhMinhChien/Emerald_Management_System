package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, String> {
    Optional<BillingPeriod> findFirstByDeletedFalseOrderByCreatedAtDesc();
    Optional<BillingPeriod> findFirstByDeletedFalseOrderByYearDescMonthDesc();
    List<BillingPeriod> findAllByDeletedFalseOrderByYearAscMonthAsc();
    boolean existsByDeletedFalseAndYearAndMonth(int year, int month);
    Optional<BillingPeriod> findByIdAndDeletedFalse(String id);
}

package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    @Query("""
            select sum(invoice.totalAmount)
            from Invoice invoice
            where invoice.deleted = false
              and invoice.periodId = :periodId
              and invoice.status = :status
            """)
    Long sumAmountByPeriodAndStatus(
            @Param("periodId") String periodId,
            @Param("status") InvoiceStatus status
    );

    @Query("""
            select sum(invoice.totalAmount)
            from Invoice invoice
            where invoice.deleted = false
              and invoice.periodId = :periodId
              and invoice.status <> :voidStatus
            """)
    Long sumAmountByPeriodExcludingStatus(
            @Param("periodId") String periodId,
            @Param("voidStatus") InvoiceStatus voidStatus
    );

    long countByDeletedFalseAndPeriodIdAndStatusAndDueDateBefore(
            String periodId,
            InvoiceStatus status,
            LocalDate date
    );

    long countByDeletedFalseAndPeriodIdAndStatusAndDueDateGreaterThanEqual(
            String periodId,
            InvoiceStatus status,
            LocalDate date
    );

    @Query("""
            select count(distinct invoice.apartmentId)
            from Invoice invoice
            where invoice.deleted = false
              and invoice.periodId = :periodId
              and invoice.status = :status
              and invoice.dueDate < :date
            """)
    long countOverdueApartments(
            @Param("periodId") String periodId,
            @Param("status") InvoiceStatus status,
            @Param("date") LocalDate date
    );

    @Query("""
            select invoice.apartmentId as apartmentId,
                   sum(invoice.totalAmount) as debtAmount,
                   sum(case when invoice.dueDate < :today then 1 else 0 end) as overdueInvoiceCount
            from Invoice invoice
            where invoice.deleted = false
              and invoice.status = :status
              and invoice.apartmentId in :apartmentIds
            group by invoice.apartmentId
            """)
    List<ApartmentDebtProjection> findApartmentDebtSummaries(
            @Param("apartmentIds") List<String> apartmentIds,
            @Param("status") InvoiceStatus status,
            @Param("today") LocalDate today
    );

    @Query("""
            select count(invoice.id) as pendingInvoiceCount,
                   coalesce(sum(invoice.totalAmount), 0) as totalAmount,
                   coalesce(sum(case when invoice.dueDate < :today then 1 else 0 end), 0) as overdueInvoiceCount,
                   coalesce(sum(case when invoice.dueDate < :today then invoice.totalAmount else 0 end), 0) as overdueAmount
            from Invoice invoice
            where invoice.deleted = false
              and invoice.status = :status
              and invoice.apartmentId = :apartmentId
            """)
    ApartmentDebtDetailProjection findDebtSummaryByApartmentId(
            @Param("apartmentId") String apartmentId,
            @Param("status") InvoiceStatus status,
            @Param("today") LocalDate today
    );

    List<Invoice> findTop3ByDeletedFalseOrderByCreatedAtDesc();

    interface ApartmentDebtProjection {
        String getApartmentId();
        Long getDebtAmount();
        Long getOverdueInvoiceCount();
    }

    interface ApartmentDebtDetailProjection {
        Long getTotalAmount();
        Long getPendingInvoiceCount();
        Long getOverdueInvoiceCount();
        Long getOverdueAmount();
    }
}

package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findByIdAndDeletedFalse(String id);

    @Query("""
            select invoice
            from Invoice invoice
            join BillingPeriod period on period.id = invoice.periodId
            where invoice.deleted = false
              and invoice.apartmentId = :apartmentId
              and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PENDING
            order by invoice.dueDate asc, period.year desc, period.month desc, invoice.id asc
            """)
    List<Invoice> findOutstandingInvoicesByApartmentId(
            @Param("apartmentId") String apartmentId
    );

    @Query(
            value = """
                    select invoice
                    from Invoice invoice
                    join BillingPeriod period on period.id = invoice.periodId
                    where invoice.deleted = false
                      and invoice.apartmentId = :apartmentId
                      and invoice.status <> org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID
                      and period.deleted = false
                    order by period.year desc, period.month desc,
                             invoice.createdAt desc, invoice.id desc
                    """,
            countQuery = """
                    select count(invoice.id)
                    from Invoice invoice
                    join BillingPeriod period on period.id = invoice.periodId
                    where invoice.deleted = false
                      and invoice.apartmentId = :apartmentId
                      and invoice.status <> org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID
                      and period.deleted = false
                    """
    )
    Page<Invoice> findResidentInvoicesByApartmentId(
            @Param("apartmentId") String apartmentId,
            Pageable pageable
    );
    @Query("""
            select invoice
            from Invoice invoice
            where invoice.deleted = false
              and invoice.apartmentId = :apartmentId
              and invoice.periodId = :periodId
              and invoice.status <> org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID
            order by invoice.createdAt desc, invoice.id desc
            """)
    List<Invoice> findCurrentApartmentInvoices(
            @Param("apartmentId") String apartmentId,
            @Param("periodId") String periodId,
            Pageable pageable
    );

    @Query("""
            select invoice
            from Invoice invoice
            join BillingPeriod period on period.id = invoice.periodId
            where invoice.deleted = false
              and invoice.apartmentId = :apartmentId
              and invoice.status <> org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID
              and period.deleted = false
            order by period.year desc, period.month desc, invoice.createdAt desc
            """)
    List<Invoice> findRecentInvoicesByApartmentId(
            @Param("apartmentId") String apartmentId,
            Pageable pageable
    );
    @Query(
            value = """
                    select invoice.id as id,
                           period.id as periodId,
                           period.year as periodYear,
                           period.month as periodMonth,
                           apartment.id as apartmentId,
                           apartment.code as apartmentCode,
                           tower.code as towerCode,
                           invoice.recipientName as recipientName,
                           invoice.totalAmount as totalAmount,
                           invoice.dueDate as dueDate,
                           invoice.status as status,
                           invoice.paidOn as paidOn
                    from Invoice invoice
                    join BillingPeriod period on period.id = invoice.periodId
                    join Apartment apartment on apartment.id = invoice.apartmentId
                    left join Tower tower on tower.id = apartment.towerId
                    where invoice.deleted = false
                      and period.deleted = false
                      and apartment.deleted = false
                      and (:periodId is null or period.id = :periodId)
                      and (:year is null or period.year = :year)
                      and (:month is null or period.month = :month)
                      and (:towerId is null or apartment.towerId = :towerId)
                      and (:apartmentId is null or apartment.id = :apartmentId)
                      and (:query is null
                           or lower(apartment.code) like :query
                           or lower(invoice.recipientName) like :query)
                      and (:status is null
                           or (:status = 'OVERDUE'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PENDING
                               and invoice.dueDate < :today)
                           or (:status = 'PENDING'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PENDING)
                           or (:status = 'PAID'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PAID)
                           or (:status = 'VOID'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID))
                    order by invoice.createdAt desc, invoice.id desc
                    """,
            countQuery = """
                    select count(invoice.id)
                    from Invoice invoice
                    join BillingPeriod period on period.id = invoice.periodId
                    join Apartment apartment on apartment.id = invoice.apartmentId
                    where invoice.deleted = false
                      and period.deleted = false
                      and apartment.deleted = false
                      and (:periodId is null or period.id = :periodId)
                      and (:year is null or period.year = :year)
                      and (:month is null or period.month = :month)
                      and (:towerId is null or apartment.towerId = :towerId)
                      and (:apartmentId is null or apartment.id = :apartmentId)
                      and (:query is null
                           or lower(apartment.code) like :query
                           or lower(invoice.recipientName) like :query)
                      and (:status is null
                           or (:status = 'OVERDUE'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PENDING
                               and invoice.dueDate < :today)
                           or (:status = 'PENDING'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PENDING)
                           or (:status = 'PAID'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PAID)
                           or (:status = 'VOID'
                               and invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID))
                    """
    )
    Page<InvoiceListProjection> searchInvoices(
            @Param("periodId") String periodId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("towerId") String towerId,
            @Param("apartmentId") String apartmentId,
            @Param("status") String status,
            @Param("query") String query,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Query("""
            select
                coalesce(sum(case when invoice.status <> org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID then 1 else 0 end), 0) as total,
                coalesce(sum(case when invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PAID then 1 else 0 end), 0) as paid,
                coalesce(sum(case when invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PENDING
                                  and (invoice.dueDate is null or invoice.dueDate >= :today) then 1 else 0 end), 0) as pending,
                coalesce(sum(case when invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.PENDING
                                  and invoice.dueDate < :today then 1 else 0 end), 0) as overdue,
                coalesce(sum(case when invoice.status = org.example.emeraldmanagementsystem.common.enums.InvoiceStatus.VOID then 1 else 0 end), 0) as voidCount
            from Invoice invoice
            join BillingPeriod period on period.id = invoice.periodId
            join Apartment apartment on apartment.id = invoice.apartmentId
            where invoice.deleted = false
              and period.deleted = false
              and apartment.deleted = false
              and (:periodId is null or period.id = :periodId)
              and (:year is null or period.year = :year)
              and (:month is null or period.month = :month)
              and (:towerId is null or apartment.towerId = :towerId)
            """)
    InvoiceSummaryProjection getInvoiceSummary(
            @Param("periodId") String periodId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("towerId") String towerId,
            @Param("today") LocalDate today
    );

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

    interface InvoiceListProjection {
        String getId();
        String getPeriodId();
        Integer getPeriodYear();
        Integer getPeriodMonth();
        String getApartmentId();
        String getApartmentCode();
        String getTowerCode();
        String getRecipientName();
        Long getTotalAmount();
        LocalDate getDueDate();
        InvoiceStatus getStatus();
        LocalDate getPaidOn();
    }

    interface InvoiceSummaryProjection {
        Long getTotal();
        Long getPaid();
        Long getPending();
        Long getOverdue();
        Long getVoidCount();
    }
}

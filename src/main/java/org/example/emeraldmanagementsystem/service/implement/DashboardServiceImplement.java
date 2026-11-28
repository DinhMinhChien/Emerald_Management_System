package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.enums.ApartmentStatus;
import org.example.emeraldmanagementsystem.common.enums.IncidentStatus;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.dto.response.DashboardResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.example.emeraldmanagementsystem.entity.Incident;
import org.example.emeraldmanagementsystem.entity.Invoice;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.example.emeraldmanagementsystem.repository.IncidentRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DashboardServiceImplement implements DashboardService {
    private final ApartmentRepository apartmentRepository;
    private final BillingPeriodRepository billingPeriodRepository;
    private final IncidentRepository incidentRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        long totalApartments = apartmentRepository.countByDeletedFalse();
        long occupiedApartments = apartmentRepository.countByDeletedFalseAndStatus(ApartmentStatus.OCCUPIED);
        BillingPeriod currentPeriod = billingPeriodRepository
                .findFirstByDeletedFalseOrderByCreatedAtDesc()
                .orElse(null);
        LocalDate today = LocalDate.now();

        long collectedAmount = 0;
        long periodTotalAmount = 0;
        long overdueApartments = 0;
        long pendingInvoices = 0;
        long overdueInvoices = 0;
        DashboardResponse.Period period = null;
        if (currentPeriod != null) {
            period = new DashboardResponse.Period(
                    currentPeriod.getId(),
                    currentPeriod.getYear(),
                    currentPeriod.getMonth()
            );
            collectedAmount = amountOrZero(
                    invoiceRepository.sumAmountByPeriodAndStatus(currentPeriod.getId(), InvoiceStatus.PAID)
            );
            periodTotalAmount = amountOrZero(
                    invoiceRepository.sumAmountByPeriodExcludingStatus(currentPeriod.getId(), InvoiceStatus.VOID)
            );
            overdueApartments = invoiceRepository.countOverdueApartments(
                    currentPeriod.getId(),
                    InvoiceStatus.PENDING,
                    today
            );
            pendingInvoices = invoiceRepository.countByDeletedFalseAndPeriodIdAndStatusAndDueDateGreaterThanEqual(
                    currentPeriod.getId(),
                    InvoiceStatus.PENDING,
                    today
            );
            overdueInvoices = invoiceRepository.countByDeletedFalseAndPeriodIdAndStatusAndDueDateBefore(
                    currentPeriod.getId(),
                    InvoiceStatus.PENDING,
                    today
            );
        }

        long newIncidents = incidentRepository.countByDeletedFalseAndStatus(IncidentStatus.NEW);
        long acknowledgedIncidents = incidentRepository.countByDeletedFalseAndStatus(IncidentStatus.ACKNOWLEDGED);
        long inProgressIncidents = incidentRepository.countByDeletedFalseAndStatus(IncidentStatus.IN_PROGRESS);
        List<Incident> latestIncidents = incidentRepository.findTop3ByDeletedFalseOrderByCreatedAtDesc();
        List<Invoice> recentInvoices = invoiceRepository.findTop3ByDeletedFalseOrderByCreatedAtDesc();

        Map<String, Apartment> apartmentsById = findApartmentsById(
                latestIncidents.stream().map(Incident::getApartmentId)
                        .toList(),
                recentInvoices.stream().map(Invoice::getApartmentId)
                        .toList()
        );

        return new DashboardResponse(
                period,
                new DashboardResponse.Occupancy(
                        occupiedApartments,
                        totalApartments,
                        percentage(occupiedApartments, totalApartments)
                ),
                new DashboardResponse.Collection(
                        collectedAmount,
                        periodTotalAmount,
                        percentage(collectedAmount, periodTotalAmount)
                ),
                overdueApartments,
                new DashboardResponse.IncidentSummary(
                        newIncidents + acknowledgedIncidents + inProgressIncidents,
                        newIncidents,
                        acknowledgedIncidents,
                        inProgressIncidents
                ),
                new DashboardResponse.Todo(pendingInvoices, overdueInvoices, newIncidents),
                latestIncidents.stream()
                        .map(incident -> toLatestIncident(incident, apartmentsById))
                        .toList(),
                recentInvoices.stream()
                        .map(invoice -> toRecentInvoice(invoice, apartmentsById, today))
                        .toList()
        );
    }

    private Map<String, Apartment> findApartmentsById(
            List<String> incidentApartmentIds,
            List<String> invoiceApartmentIds
    ) {
        List<String> apartmentIds = Stream
                .concat(incidentApartmentIds.stream(), invoiceApartmentIds.stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (apartmentIds.isEmpty()) {
            return Map.of();
        }

        return apartmentRepository.findAllById(apartmentIds).stream()
                .collect(Collectors.toMap(Apartment::getId, Function.identity()));
    }

    private DashboardResponse.LatestIncident toLatestIncident(
            Incident incident,
            Map<String, Apartment> apartmentsById
    ) {
        Apartment apartment = apartmentsById.get(incident.getApartmentId());
        return new DashboardResponse.LatestIncident(
                incident.getId(),
                incident.getTitle(),
                incident.getArea(),
                apartment == null ? null : apartment.getCode(),
                incident.getStatus() == null ? null : incident.getStatus().name(),
                incident.getCreatedAt()
        );
    }

    private DashboardResponse.RecentInvoice toRecentInvoice(
            Invoice invoice,
            Map<String, Apartment> apartmentsById,
            LocalDate today
    ) {
        Apartment apartment = apartmentsById.get(invoice.getApartmentId());
        String displayStatus = invoice.getStatus().name();
        if (invoice.getStatus() == InvoiceStatus.PENDING
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(today)) {
            displayStatus = "OVERDUE";
        }
        return new DashboardResponse.RecentInvoice(
                invoice.getId(),
                apartment == null ? null : apartment.getCode(),
                invoice.getRecipientName(),
                invoice.getTotalAmount(),
                displayStatus,
                invoice.getDueDate()
        );
    }

    private long amountOrZero(Long amount) {
        return amount == null ? 0 : amount;
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        BigDecimal value = BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100));
        return value.doubleValue();
    }
}

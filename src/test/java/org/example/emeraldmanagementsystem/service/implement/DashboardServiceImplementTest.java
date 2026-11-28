package org.example.emeraldmanagementsystem.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplementTest {
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private BillingPeriodRepository billingPeriodRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @InjectMocks
    private DashboardServiceImplement dashboardService;

    @Test
    void returnsDashboardWithZeroCollectionWhenNoBillingPeriodExists() {
        when(apartmentRepository.countByDeletedFalse()).thenReturn(3L);
        when(apartmentRepository.countByDeletedFalseAndStatus(ApartmentStatus.OCCUPIED)).thenReturn(1L);
        when(billingPeriodRepository.findFirstByDeletedFalseOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        stubIncidentCounts(2L, 1L, 1L);
        when(incidentRepository.findTop3ByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of());
        when(invoiceRepository.findTop3ByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard();

        assertNull(response.period());
        assertEquals(new DashboardResponse.Occupancy(1, 3, 33.3), response.occupancy());
        assertEquals(new DashboardResponse.Collection(0, 0, 0.0), response.collection());
        assertEquals(4, response.incidents().needHandling());
        assertEquals(2, response.todo().unacknowledgedIncidents());
        assertEquals(List.of(), response.latestIncidents());
        assertEquals(List.of(), response.recentInvoices());
    }

    @Test
    void calculatesCurrentPeriodTotalsAndMapsRecentItems() {
        when(apartmentRepository.countByDeletedFalse()).thenReturn(10L);
        when(apartmentRepository.countByDeletedFalseAndStatus(ApartmentStatus.OCCUPIED)).thenReturn(7L);

        BillingPeriod period = new BillingPeriod();
        period.setId("period-1");
        period.setYear(2026);
        period.setMonth(11);
        when(billingPeriodRepository.findFirstByDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(Optional.of(period));
        when(invoiceRepository.sumAmountByPeriodAndStatus("period-1", InvoiceStatus.PAID)).thenReturn(300L);
        when(invoiceRepository.sumAmountByPeriodExcludingStatus("period-1", InvoiceStatus.VOID))
                .thenReturn(600L);
        when(invoiceRepository.countOverdueApartments(
                org.mockito.ArgumentMatchers.eq("period-1"),
                org.mockito.ArgumentMatchers.eq(InvoiceStatus.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDate.class)
        )).thenReturn(2L);
        when(invoiceRepository.countByDeletedFalseAndPeriodIdAndStatusAndDueDateBefore(
                org.mockito.ArgumentMatchers.eq("period-1"),
                org.mockito.ArgumentMatchers.eq(InvoiceStatus.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDate.class)
        )).thenReturn(3L);
        when(invoiceRepository.countByDeletedFalseAndPeriodIdAndStatusAndDueDateGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq("period-1"),
                org.mockito.ArgumentMatchers.eq(InvoiceStatus.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDate.class)
        )).thenReturn(4L);

        stubIncidentCounts(1L, 2L, 3L);
        Incident incident = new Incident();
        incident.setId("incident-1");
        incident.setApartmentId("apartment-1");
        incident.setTitle("Elevator noise");
        incident.setArea("Elevator");
        incident.setStatus(IncidentStatus.NEW);
        incident.setCreatedAt(LocalDateTime.now());
        when(incidentRepository.findTop3ByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(incident));

        Invoice invoice = new Invoice();
        invoice.setId("invoice-1");
        invoice.setApartmentId("apartment-1");
        invoice.setRecipientName("Resident Name");
        invoice.setTotalAmount(250L);
        invoice.setDueDate(LocalDate.now().minusDays(1));
        invoice.setStatus(InvoiceStatus.PENDING);
        when(invoiceRepository.findTop3ByDeletedFalseOrderByCreatedAtDesc()).thenReturn(List.of(invoice));

        Apartment apartment = new Apartment();
        apartment.setId("apartment-1");
        apartment.setCode("A-1205");
        when(apartmentRepository.findAllById(anyIterable())).thenReturn(List.of(apartment));

        DashboardResponse response = dashboardService.getDashboard();

        assertEquals(new DashboardResponse.Period("period-1", 2026, 11), response.period());
        assertEquals(new DashboardResponse.Collection(300, 600, 50.0), response.collection());
        assertEquals(2, response.overdueApartments());
        assertEquals(4, response.todo().pendingInvoices());
        assertEquals(3, response.todo().overdueInvoices());
        assertEquals("A-1205", response.latestIncidents().getFirst().apartmentCode());
        assertEquals("A-1205", response.recentInvoices().getFirst().apartmentCode());
        assertEquals("OVERDUE", response.recentInvoices().getFirst().displayStatus());

        JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
        assertTrue(json.path("incidents").has("new"));
        assertFalse(json.path("incidents").has("newCount"));
    }

    private void stubIncidentCounts(long newCount, long acknowledgedCount, long inProgressCount) {
        when(incidentRepository.countByDeletedFalseAndStatus(IncidentStatus.NEW)).thenReturn(newCount);
        when(incidentRepository.countByDeletedFalseAndStatus(IncidentStatus.ACKNOWLEDGED))
                .thenReturn(acknowledgedCount);
        when(incidentRepository.countByDeletedFalseAndStatus(IncidentStatus.IN_PROGRESS))
                .thenReturn(inProgressCount);
    }
}

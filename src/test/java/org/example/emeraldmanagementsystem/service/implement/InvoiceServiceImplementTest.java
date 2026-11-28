package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.enums.FeeType;
import org.example.emeraldmanagementsystem.dto.response.InvoiceListResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceSummaryResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceDetailResponse;
import org.example.emeraldmanagementsystem.dto.request.InvoicePayRequest;
import org.example.emeraldmanagementsystem.dto.request.InvoiceVoidRequest;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.example.emeraldmanagementsystem.entity.Invoice;
import org.example.emeraldmanagementsystem.entity.InvoiceItem;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceItemRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplementTest {
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private BillingPeriodRepository billingPeriodRepository;
    @Mock
    private ApartmentRepository apartmentRepository;
    @Mock
    private TowerRepository towerRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private InvoiceServiceImplement invoiceService;

    @Test
    void mapsOverdueDisplayStatusAndPagination() {
        InvoiceRepository.InvoiceListProjection overdue = projection(
                "inv-1",
                InvoiceStatus.PENDING,
                LocalDate.now().minusDays(1)
        );
        when(invoiceRepository.searchInvoices(
                eq("per-202603"),
                eq(2026),
                eq(3),
                eq(null),
                eq(null),
                eq("OVERDUE"),
                eq("%hoa%"),
                eq(LocalDate.now()),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(overdue), PageRequest.of(0, 20), 1));

        InvoiceListResponse response = invoiceService.getInvoices(
                "per-202603",
                2026,
                3,
                null,
                null,
                "overdue",
                " Hoa ",
                0,
                20
        );

        assertEquals(1, response.total());
        assertEquals(1, response.items().size());
        assertEquals("OVERDUE", response.items().getFirst().displayStatus());
        assertEquals(InvoiceStatus.PENDING, response.items().getFirst().status());
        assertEquals("A-1205", response.items().getFirst().apartment().code());
    }

    @Test
    void rejectsInvalidStatus() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.getInvoices(
                        null, null, null, null, null, "UNKNOWN", null, 0, 20
                )
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
    }

    @Test
    void rejectsPartialYearMonth() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.getInvoices(
                        null, 2026, null, null, null, null, null, 0, 20
                )
        );

        assertEquals(400, exception.getErrorCode());
        assertEquals("VALIDATION_ERROR", exception.getMessage());
    }

    @Test
    void exportsAllFilteredInvoicesAsCsvAndXlsx() {
        InvoiceRepository.InvoiceListProjection invoice = projection(
                "inv-1",
                InvoiceStatus.PENDING,
                LocalDate.now().minusDays(1)
        );
        when(invoiceRepository.searchInvoices(
                eq("per-202603"),
                eq(2026),
                eq(3),
                eq("tower-1"),
                eq(null),
                eq("OVERDUE"),
                eq("%a-%"),
                eq(LocalDate.now()),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(invoice)));

        InvoiceService.InvoiceExportFile csv = invoiceService.exportInvoices(
                "per-202603",
                2026,
                3,
                "tower-1",
                null,
                "overdue",
                "A-",
                "csv"
        );
        InvoiceService.InvoiceExportFile xlsx = invoiceService.exportInvoices(
                "per-202603",
                2026,
                3,
                "tower-1",
                null,
                "overdue",
                "A-",
                "xlsx"
        );

        assertEquals("danh-sach-hoa-don.csv", csv.filename());
        assertEquals("danh-sach-hoa-don.xlsx", xlsx.filename());
        assertEquals(0, new String(csv.content()).indexOf('\uFEFF'));
        assertEquals((byte) 'P', xlsx.content()[0]);
        assertEquals((byte) 'K', xlsx.content()[1]);
    }

    @Test
    void returnsPaidInvoiceDetailsWithItemsAndPayment() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setPeriodId("per-202603");
        invoice.setApartmentId("apt-1");
        invoice.setRecipientResidentId("res-1");
        invoice.setRecipientName("Nguyen Hoa");
        invoice.setAreaM2(new BigDecimal("72.5"));
        invoice.setServiceTierName("Mức 2");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setDueDate(LocalDate.now().plusDays(10));
        invoice.setPaidOn(LocalDate.now());
        invoice.setPaidNote("Nộp tại văn phòng");
        invoice.setReceivedBy("Trần Văn Q");
        invoice.setTotalAmount(3_450_000L);

        BillingPeriod period = new BillingPeriod();
        period.setId("per-202603");
        period.setYear(2026);
        period.setMonth(3);
        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");
        InvoiceItem item = new InvoiceItem();
        item.setFeeType(FeeType.MANAGEMENT);
        item.setDescription("Phí quản lý");
        item.setQuantity(1);
        item.setUnitAmount(1_200_000L);
        item.setAmount(1_200_000L);

        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));
        when(billingPeriodRepository.findByIdAndDeletedFalse("per-202603")).thenReturn(Optional.of(period));
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(tower));
        when(invoiceItemRepository.findByInvoiceIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inv-1"))
                .thenReturn(List.of(item));

        InvoiceDetailResponse response = invoiceService.getInvoiceDetails("inv-1");

        assertEquals("PAID", response.displayStatus());
        assertEquals("Tòa A", response.apartment().towerName());
        assertEquals("Nguyen Hoa", response.recipient().fullName());
        assertEquals(1, response.items().size());
        assertEquals("Nộp tại văn phòng", response.payment().note());
        assertNull(response.voidInfo());
    }

    @Test
    void returnsNotFoundForMissingInvoice() {
        when(invoiceRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.getInvoiceDetails("missing")
        );

        assertEquals(404, exception.getErrorCode());
        assertEquals("INVOICE_NOT_FOUND", exception.getMessage());
    }

    @Test
    void rejectsPaymentForAlreadyPaidInvoice() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.payInvoice(
                        "inv-1",
                        new InvoicePayRequest(LocalDate.now(), "Test")
                )
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("INVOICE_NOT_PENDING", exception.getMessage());
    }

    @Test
    void rejectsFuturePaidDate() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setCreatedAt(java.time.LocalDateTime.now().minusDays(1));
        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.payInvoice(
                        "inv-1",
                        new InvoicePayRequest(LocalDate.now().plusDays(1), null)
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("INVALID_PAID_DATE", exception.getMessage());
    }

    @Test
    void rejectsPaidDateBeforeIssuance() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setCreatedAt(java.time.LocalDateTime.now().plusDays(1));
        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.payInvoice(
                        "inv-1",
                        new InvoicePayRequest(LocalDate.now(), null)
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("INVALID_PAID_DATE", exception.getMessage());
    }

    @Test
    void recordsFullPaymentAndReturnsPaidDetails() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setPeriodId("per-202603");
        invoice.setApartmentId("apt-1");
        invoice.setRecipientResidentId("res-1");
        invoice.setRecipientName("Nguyen Hoa");
        invoice.setAreaM2(new BigDecimal("72.5"));
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setCreatedAt(java.time.LocalDateTime.now().minusDays(5));
        invoice.setTotalAmount(3_450_000L);

        BillingPeriod period = new BillingPeriod();
        period.setId("per-202603");
        period.setYear(2026);
        period.setMonth(3);
        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");

        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));
        when(billingPeriodRepository.findByIdAndDeletedFalse("per-202603")).thenReturn(Optional.of(period));
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(tower));
        when(invoiceItemRepository.findByInvoiceIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inv-1"))
                .thenReturn(List.of());

        InvoiceDetailResponse response = invoiceService.payInvoice(
                "inv-1",
                new InvoicePayRequest(LocalDate.now(), "Nộp tại văn phòng")
        );

        assertEquals(InvoiceStatus.PAID, response.status());
        assertEquals("PAID", response.displayStatus());
        assertEquals("Nộp tại văn phòng", response.payment().note());
        assertEquals(LocalDate.now(), response.payment().paidOn());
    }

    @Test
    void voidsPendingInvoiceForWrongData() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setPeriodId("per-202603");
        invoice.setApartmentId("apt-1");
        invoice.setRecipientResidentId("res-1");
        invoice.setRecipientName("Nguyen Hoa");
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setAreaM2(new BigDecimal("72.5"));
        invoice.setTotalAmount(3_450_000L);

        BillingPeriod period = new BillingPeriod();
        period.setId("per-202603");
        period.setYear(2026);
        period.setMonth(3);
        Apartment apartment = new Apartment();
        apartment.setId("apt-1");
        apartment.setCode("A-1205");
        apartment.setTowerId("tower-1");
        Tower tower = new Tower();
        tower.setId("tower-1");
        tower.setCode("A");
        tower.setName("Tòa A");

        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));
        when(billingPeriodRepository.findByIdAndDeletedFalse("per-202603")).thenReturn(Optional.of(period));
        when(apartmentRepository.findByIdAndDeletedFalse("apt-1")).thenReturn(Optional.of(apartment));
        when(towerRepository.findByIdAndDeletedFalse("tower-1")).thenReturn(Optional.of(tower));
        when(invoiceItemRepository.findByInvoiceIdAndDeletedFalseOrderByCreatedAtAscIdAsc("inv-1"))
                .thenReturn(List.of());

        InvoiceDetailResponse response = invoiceService.voidInvoice(
                "inv-1",
                new InvoiceVoidRequest("WRONG_DATA", "Sai số xe máy")
        );

        assertEquals(InvoiceStatus.VOID, response.status());
        assertEquals("VOID", response.displayStatus());
        assertEquals(org.example.emeraldmanagementsystem.common.enums.VoidReason.WRONG_DATA,
                response.voidInfo().reason());
        assertEquals("Sai số xe máy", response.voidInfo().note());
        assertEquals(null, response.payment());
    }

    @Test
    void requiresNoteForOtherVoidReason() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setStatus(InvoiceStatus.PENDING);
        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.voidInvoice(
                        "inv-1",
                        new InvoiceVoidRequest("OTHER", " ")
                )
        );

        assertEquals(422, exception.getErrorCode());
        assertEquals("VOID_NOTE_REQUIRED", exception.getMessage());
    }

    @Test
    void rejectsVoidForNonPendingInvoice() {
        Invoice invoice = new Invoice();
        invoice.setId("inv-1");
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByIdAndDeletedFalse("inv-1")).thenReturn(Optional.of(invoice));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoiceService.voidInvoice(
                        "inv-1",
                        new InvoiceVoidRequest("WRONG_DATA", null)
                )
        );

        assertEquals(409, exception.getErrorCode());
        assertEquals("INVOICE_NOT_PENDING", exception.getMessage());
    }

    @Test
    void calculatesSummaryAndOverridesTotalFromNonVoidStatus() {
        when(invoiceRepository.getInvoiceSummary(
                eq("per-202603"),
                eq(2026),
                eq(3),
                eq("tower-1"),
                eq(LocalDate.now())
        )).thenReturn(summary(146, 30, 14, 2));

        InvoiceSummaryResponse response = invoiceService.getSummary(
                "per-202603",
                2026,
                3,
                "tower-1"
        );

        assertEquals(190, response.total());
        assertEquals(146, response.paid());
        assertEquals(30, response.pending());
        assertEquals(14, response.overdue());
        assertEquals(2, response.voidCount());
    }

    @Test
    void returnsZeroSummaryForNoRows() {
        when(invoiceRepository.getInvoiceSummary(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(LocalDate.now())
        )).thenReturn(null);

        InvoiceSummaryResponse response = invoiceService.getSummary(null, null, null, null);

        assertEquals(new InvoiceSummaryResponse(0, 0, 0, 0, 0), response);
    }

    private InvoiceRepository.InvoiceSummaryProjection summary(
            long paid,
            long pending,
            long overdue,
            long voidCount
    ) {
        return new InvoiceRepository.InvoiceSummaryProjection() {
            public Long getTotal() { return paid + pending + overdue; }
            public Long getPaid() { return paid; }
            public Long getPending() { return pending; }
            public Long getOverdue() { return overdue; }
            public Long getVoidCount() { return voidCount; }
        };
    }

    private InvoiceRepository.InvoiceListProjection projection(
            String id,
            InvoiceStatus status,
            LocalDate dueDate
    ) {
        return new InvoiceRepository.InvoiceListProjection() {
            public String getId() { return id; }
            public String getPeriodId() { return "per-202603"; }
            public Integer getPeriodYear() { return 2026; }
            public Integer getPeriodMonth() { return 3; }
            public String getApartmentId() { return "apt-1"; }
            public String getApartmentCode() { return "A-1205"; }
            public String getTowerCode() { return "A"; }
            public String getRecipientName() { return "Nguyen Hoa"; }
            public Long getTotalAmount() { return 3_450_000L; }
            public LocalDate getDueDate() { return dueDate; }
            public InvoiceStatus getStatus() { return status; }
            public LocalDate getPaidOn() { return null; }
        };
    }
}

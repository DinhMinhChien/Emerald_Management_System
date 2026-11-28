package org.example.emeraldmanagementsystem.service.implement;

import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.common.enums.InvoiceStatus;
import org.example.emeraldmanagementsystem.common.enums.VoidReason;
import org.example.emeraldmanagementsystem.dto.request.InvoicePayRequest;
import org.example.emeraldmanagementsystem.dto.request.InvoiceVoidRequest;
import org.example.emeraldmanagementsystem.dto.response.InvoiceListResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceSummaryResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceDetailResponse;
import org.example.emeraldmanagementsystem.entity.Apartment;
import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.example.emeraldmanagementsystem.entity.Invoice;
import org.example.emeraldmanagementsystem.entity.InvoiceItem;
import org.example.emeraldmanagementsystem.entity.Tower;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.example.emeraldmanagementsystem.repository.ApartmentRepository;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceItemRepository;
import org.example.emeraldmanagementsystem.repository.InvoiceRepository;
import org.example.emeraldmanagementsystem.repository.TowerRepository;
import org.example.emeraldmanagementsystem.repository.UserRepository;
import org.example.emeraldmanagementsystem.service.InvoiceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImplement implements InvoiceService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> VALID_STATUSES =
            Set.of("PENDING", "PAID", "VOID", "OVERDUE");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final BillingPeriodRepository billingPeriodRepository;
    private final ApartmentRepository apartmentRepository;
    private final TowerRepository towerRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public InvoiceListResponse getInvoices(
            String periodId,
            Integer year,
            Integer month,
            String towerId,
            String apartmentId,
            String status,
            String query,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        if ((year == null) != (month == null)
                || (year != null && (year < 1 || year > 9999))
                || (month != null && (month < 1 || month > 12))) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        String normalizedStatus = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !VALID_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        String normalizedQuery = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String normalizedPeriodId = periodId == null || periodId.isBlank()
                ? null
                : periodId.trim();
        String normalizedTowerId = towerId == null || towerId.isBlank()
                ? null
                : towerId.trim();
        String normalizedApartmentId = apartmentId == null || apartmentId.isBlank()
                ? null
                : apartmentId.trim();

        Page<InvoiceRepository.InvoiceListProjection> invoicePage =
                invoiceRepository.searchInvoices(
                        normalizedPeriodId,
                        year,
                        month,
                        normalizedTowerId,
                        normalizedApartmentId,
                        normalizedStatus,
                        normalizedQuery,
                        LocalDate.now(),
                        PageRequest.of(page, size)
                );

        List<InvoiceListResponse.InvoiceItem> items = invoicePage.getContent().stream()
                .map(this::toInvoiceItem)
                .toList();
        return new InvoiceListResponse(
                items,
                invoicePage.getTotalElements(),
                page,
                size
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceSummaryResponse getSummary(
            String periodId,
            Integer year,
            Integer month,
            String towerId
    ) {
        if ((year == null) != (month == null)
                || (year != null && (year < 1 || year > 9999))
                || (month != null && (month < 1 || month > 12))) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        String normalizedPeriodId = periodId == null || periodId.isBlank()
                ? null
                : periodId.trim();
        String normalizedTowerId = towerId == null || towerId.isBlank()
                ? null
                : towerId.trim();
        InvoiceRepository.InvoiceSummaryProjection projection = invoiceRepository.getInvoiceSummary(
                normalizedPeriodId,
                year,
                month,
                normalizedTowerId,
                LocalDate.now()
        );
        if (projection == null) {
            return new InvoiceSummaryResponse(0, 0, 0, 0, 0);
        }
        long paid = projection.getPaid() == null ? 0 : projection.getPaid();
        long pending = projection.getPending() == null ? 0 : projection.getPending();
        long overdue = projection.getOverdue() == null ? 0 : projection.getOverdue();
        long voidCount = projection.getVoidCount() == null ? 0 : projection.getVoidCount();
        long total = paid + pending + overdue;
        return new InvoiceSummaryResponse(total, paid, pending, overdue, voidCount);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDetailResponse getInvoiceDetails(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new BusinessException(404, "INVOICE_NOT_FOUND");
        }
        Invoice invoice = invoiceRepository.findByIdAndDeletedFalse(invoiceId.trim())
                .orElseThrow(() -> new BusinessException(404, "INVOICE_NOT_FOUND"));
        BillingPeriod period = billingPeriodRepository.findByIdAndDeletedFalse(invoice.getPeriodId())
                .orElseThrow(() -> new BusinessException(404, "INVOICE_NOT_FOUND"));
        Apartment apartment = apartmentRepository.findByIdAndDeletedFalse(invoice.getApartmentId())
                .orElseThrow(() -> new BusinessException(404, "INVOICE_NOT_FOUND"));
        Tower tower = towerRepository.findByIdAndDeletedFalse(apartment.getTowerId())
                .orElse(null);

        List<InvoiceDetailResponse.InvoiceItem> items =
                invoiceItemRepository.findByInvoiceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(invoice.getId())
                        .stream()
                        .map(this::toDetailItem)
                        .toList();

        String displayStatus = invoice.getStatus().name();
        if (invoice.getStatus() == InvoiceStatus.PENDING
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(LocalDate.now())) {
            displayStatus = "OVERDUE";
        }

        InvoiceDetailResponse.Payment payment = null;
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            payment = new InvoiceDetailResponse.Payment(
                    invoice.getPaidOn(),
                    invoice.getReceivedBy(),
                    invoice.getPaidNote()
            );
        }

        InvoiceDetailResponse.VoidInfo voidInfo = null;
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            String voidedBy = null;
            if (invoice.getUpdatedBy() != null && !invoice.getUpdatedBy().isBlank()) {
                String updatedBy = invoice.getUpdatedBy();
                voidedBy = userRepository.findUserByUsernameAndDeletedFalse(updatedBy)
                        .map(user -> user.getFullName())
                        .filter(name -> name != null && !name.isBlank())
                        .orElse(updatedBy);
            }
            voidInfo = new InvoiceDetailResponse.VoidInfo(
                    invoice.getVoidReason(),
                    invoice.getVoidNote(),
                    toOffsetDateTime(invoice.getVoidedAt()),
                    voidedBy
            );
        }

        return new InvoiceDetailResponse(
                invoice.getId(),
                new InvoiceDetailResponse.Period(period.getId(), period.getYear(), period.getMonth()),
                new InvoiceDetailResponse.Apartment(
                        apartment.getId(),
                        apartment.getCode(),
                        tower == null ? null : tower.getCode(),
                        tower == null ? null : tower.getName()
                ),
                new InvoiceDetailResponse.Recipient(
                        invoice.getRecipientResidentId(),
                        invoice.getRecipientName()
                ),
                invoice.getAreaM2(),
                invoice.getServiceTierName(),
                invoice.getStatus(),
                displayStatus,
                invoice.getDueDate(),
                toOffsetDateTime(invoice.getCreatedAt()),
                items,
                invoice.getTotalAmount(),
                payment,
                voidInfo
        );
    }

    private InvoiceDetailResponse.InvoiceItem toDetailItem(InvoiceItem item) {
        return new InvoiceDetailResponse.InvoiceItem(
                item.getFeeType(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitAmount(),
                item.getAmount()
        );
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceExportFile exportInvoices(
            String periodId,
            Integer year,
            Integer month,
            String towerId,
            String apartmentId,
            String status,
            String query,
            String format
    ) {
        if ((year == null) != (month == null)
                || (year != null && (year < 1 || year > 9999))
                || (month != null && (month < 1 || month > 12))) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }

        String normalizedFormat = format == null
                ? "xlsx"
                : format.trim().toLowerCase(Locale.ROOT);
        if (!normalizedFormat.equals("xlsx") && !normalizedFormat.equals("csv")) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        String normalizedStatus = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !VALID_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        String normalizedQuery = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";

        Page<InvoiceRepository.InvoiceListProjection> invoicePage =
                invoiceRepository.searchInvoices(
                        blankToNull(periodId),
                        year,
                        month,
                        blankToNull(towerId),
                        blankToNull(apartmentId),
                        normalizedStatus,
                        normalizedQuery,
                        LocalDate.now(),
                        Pageable.unpaged()
                );

        List<InvoiceRepository.InvoiceListProjection> rows = invoicePage.getContent();
        if (normalizedFormat.equals("csv")) {
            byte[] content = generateCsv(rows);
            return new InvoiceExportFile(
                    content,
                    "danh-sach-hoa-don.csv",
                    "text/csv;charset=UTF-8"
            );
        }
        byte[] content = generateXlsx(rows);
        return new InvoiceExportFile(
                content,
                "danh-sach-hoa-don.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    private byte[] generateCsv(List<InvoiceRepository.InvoiceListProjection> rows) {
        String[] headers = {
                "Căn hộ", "Tòa", "Chủ hộ", "Kỳ", "Số tiền",
                "Hạn thanh toán", "Trạng thái", "Ngày thu"
        };
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", headers)).append('\n');
        for (InvoiceRepository.InvoiceListProjection row : rows) {
            csv.append(String.join(",",
                    csvValue(row.getApartmentCode()),
                    csvValue(row.getTowerCode()),
                    csvValue(row.getRecipientName()),
                    csvValue(row.getPeriodMonth() + "/" + row.getPeriodYear()),
                    csvValue(row.getTotalAmount()),
                    csvValue(row.getDueDate()),
                    csvValue(displayStatus(row)),
                    csvValue(row.getPaidOn())
            )).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateXlsx(List<InvoiceRepository.InvoiceListProjection> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Hoa don");
            String[] headers = {
                    "Căn hộ", "Tòa", "Chủ hộ", "Kỳ", "Số tiền",
                    "Hạn thanh toán", "Trạng thái", "Ngày thu"
            };
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                headerRow.createCell(column).setCellValue(headers[column]);
            }
            int rowIndex = 1;
            for (InvoiceRepository.InvoiceListProjection row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                excelRow.createCell(0).setCellValue(value(row.getApartmentCode()));
                excelRow.createCell(1).setCellValue(value(row.getTowerCode()));
                excelRow.createCell(2).setCellValue(value(row.getRecipientName()));
                excelRow.createCell(3).setCellValue(row.getPeriodMonth() + "/" + row.getPeriodYear());
                excelRow.createCell(4).setCellValue(row.getTotalAmount() == null
                        ? 0
                        : row.getTotalAmount());
                excelRow.createCell(5).setCellValue(value(row.getDueDate()));
                excelRow.createCell(6).setCellValue(displayStatus(row));
                excelRow.createCell(7).setCellValue(value(row.getPaidOn()));
            }
            for (int column = 0; column < headers.length; column++) {
                sheet.autoSizeColumn(column);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException(500, "EXPORT_GENERATION_FAILED");
        }
    }

    private String displayStatus(InvoiceRepository.InvoiceListProjection row) {
        if (row.getStatus() == InvoiceStatus.PENDING
                && row.getDueDate() != null
                && row.getDueDate().isBefore(LocalDate.now())) {
            return "OVERDUE";
        }
        return row.getStatus() == null ? "" : row.getStatus().name();
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }



    @Override
    @Transactional
    public InvoiceDetailResponse payInvoice(String invoiceId, InvoicePayRequest request) {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new BusinessException(404, "INVOICE_NOT_FOUND");
        }
        Invoice invoice = invoiceRepository.findByIdAndDeletedFalse(invoiceId.trim())
                .orElseThrow(() -> new BusinessException(404, "INVOICE_NOT_FOUND"));
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new BusinessException(409, "INVOICE_NOT_PENDING");
        }
        if (request == null || request.paidOn() == null) {
            throw new BusinessException(422, "INVALID_PAID_DATE");
        }

        LocalDate issuedOn = invoice.getCreatedAt() == null
                ? billingPeriodRepository.findByIdAndDeletedFalse(invoice.getPeriodId())
                        .map(BillingPeriod::getCreatedAt)
                        .map(LocalDateTime::toLocalDate)
                        .orElse(null)
                : invoice.getCreatedAt().toLocalDate();
        LocalDate paidOn = request.paidOn();
        if (paidOn.isAfter(LocalDate.now())
                || (issuedOn != null && paidOn.isBefore(issuedOn))) {
            throw new BusinessException(422, "INVALID_PAID_DATE");
        }

        String username = org.example.emeraldmanagementsystem.utils.SecurityUtils.getUsername();
        String receivedBy = username == null || username.isBlank()
                ? "SYSTEM"
                : userRepository.findUserByUsernameAndDeletedFalse(username)
                        .map(user -> user.getFullName())
                        .filter(name -> name != null && !name.isBlank())
                        .orElse(username);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidOn(paidOn);
        invoice.setPaidNote(request.note());
        invoice.setReceivedBy(receivedBy);
        invoiceRepository.saveAndFlush(invoice);

        return getInvoiceDetails(invoice.getId());
    }

    @Override
    @Transactional
    public InvoiceDetailResponse voidInvoice(String invoiceId, InvoiceVoidRequest request) {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new BusinessException(404, "INVOICE_NOT_FOUND");
        }
        Invoice invoice = invoiceRepository.findByIdAndDeletedFalse(invoiceId.trim())
                .orElseThrow(() -> new BusinessException(404, "INVOICE_NOT_FOUND"));
        if (invoice.getStatus() != InvoiceStatus.PENDING) {
            throw new BusinessException(409, "INVOICE_NOT_PENDING");
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(422, "VOID_NOTE_REQUIRED");
        }

        VoidReason reason;
        try {
            reason = VoidReason.valueOf(request.reason().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        if (reason == VoidReason.OTHER
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessException(422, "VOID_NOTE_REQUIRED");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidReason(reason);
        invoice.setVoidNote(
                request.note() == null || request.note().isBlank()
                        ? null
                        : request.note().trim()
        );
        invoice.setVoidedAt(now);
        invoiceRepository.saveAndFlush(invoice);

        return getInvoiceDetails(invoice.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public InvoicePrintFile printInvoice(String invoiceId, String type) {
        InvoiceDetailResponse detail = getInvoiceDetails(invoiceId);
        String normalizedType = type == null ? "invoice" : type.trim().toLowerCase(Locale.ROOT);
        if (!normalizedType.equals("invoice") && !normalizedType.equals("receipt")) {
            throw new BusinessException(400, "VALIDATION_ERROR");
        }
        if (detail.status() == InvoiceStatus.VOID) {
            throw new BusinessException(422, "INVOICE_VOID");
        }
        if (normalizedType.equals("receipt") && detail.status() != InvoiceStatus.PAID) {
            throw new BusinessException(422, "RECEIPT_NOT_AVAILABLE");
        }

        String filename = (normalizedType.equals("receipt") ? "bien-lai-" : "hoa-don-")
                + "%d-%02d-".formatted(detail.period().year(), detail.period().month())
                + safeFilename(detail.apartment().code())
                + ".pdf";
        byte[] content = generatePdf(detail, normalizedType);
        return new InvoicePrintFile(content, filename);
    }

    private byte[] generatePdf(InvoiceDetailResponse detail, String type) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD);

            String title = type.equals("receipt")
                    ? "BIEN LAI THANH TOAN"
                    : "HOA DON THANG " + "%02d/%d".formatted(
                            detail.period().month(),
                            detail.period().year()
                    );
            document.add(new Paragraph(title, titleFont));
            document.add(new Paragraph("Can ho: " + value(detail.apartment().code()), normalFont));
            document.add(new Paragraph("Toa: " + value(detail.apartment().towerName()), normalFont));
            document.add(new Paragraph("Chu ho: " + value(detail.recipient().fullName()), normalFont));
            document.add(new Paragraph("Dien tich: " + value(detail.areaM2() == null
                    ? null
                    : detail.areaM2().toPlainString()) + " m2", normalFont));
            document.add(new Paragraph("Trang thai: " + detail.displayStatus(), normalFont));
            document.add(new Paragraph("Han thanh toan: " + value(detail.dueDate()), normalFont));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.2f, 3.5f, 1f, 1.5f, 1.5f});
            for (String heading : new String[]{"Loai phi", "Mo ta", "SL", "Don gia", "Thanh tien"}) {
                PdfPCell header = new PdfPCell(new Phrase(heading, boldFont));
                header.setBackgroundColor(new java.awt.Color(230, 230, 230));
                table.addCell(header);
            }
            for (InvoiceDetailResponse.InvoiceItem item : detail.items()) {
                table.addCell(new Phrase(item.feeType().name(), normalFont));
                table.addCell(new Phrase(value(item.description()), normalFont));
                table.addCell(new Phrase(String.valueOf(item.quantity()), normalFont));
                table.addCell(new Phrase(formatMoney(item.unitAmount()), normalFont));
                table.addCell(new Phrase(formatMoney(item.amount()), normalFont));
            }
            document.add(table);
            document.add(new Paragraph("Tong tien: " + formatMoney(detail.totalAmount()), boldFont));

            if (detail.payment() != null) {
                document.add(new Paragraph(
                        "Ngay thu: " + value(detail.payment().paidOn()),
                        normalFont
                ));
                document.add(new Paragraph(
                        "Nguoi thu: " + value(detail.payment().receivedBy()),
                        normalFont
                ));
                document.add(new Paragraph(
                        "Ghi chu: " + value(detail.payment().note()),
                        normalFont
                ));
            }
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException(500, "PDF_GENERATION_FAILED");
        }
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private String formatMoney(Long amount) {
        return amount == null
                ? "0"
                : java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount);
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private InvoiceListResponse.InvoiceItem toInvoiceItem(
            InvoiceRepository.InvoiceListProjection projection
    ) {
        InvoiceStatus status = projection.getStatus();
        String displayStatus = status.name();
        if (status == InvoiceStatus.PENDING
                && projection.getDueDate() != null
                && projection.getDueDate().isBefore(LocalDate.now())) {
            displayStatus = "OVERDUE";
        }

        return new InvoiceListResponse.InvoiceItem(
                projection.getId(),
                new InvoiceListResponse.Period(
                        projection.getPeriodId(),
                        projection.getPeriodYear(),
                        projection.getPeriodMonth()
                ),
                new InvoiceListResponse.Apartment(
                        projection.getApartmentId(),
                        projection.getApartmentCode(),
                        projection.getTowerCode()
                ),
                projection.getRecipientName(),
                projection.getTotalAmount(),
                projection.getDueDate(),
                status,
                displayStatus,
                projection.getPaidOn()
        );
    }
}

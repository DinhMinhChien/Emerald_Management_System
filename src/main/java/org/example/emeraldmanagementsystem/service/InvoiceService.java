package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.dto.response.InvoiceListResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceSummaryResponse;
import org.example.emeraldmanagementsystem.dto.response.InvoiceDetailResponse;
import org.example.emeraldmanagementsystem.dto.request.InvoicePayRequest;
import org.example.emeraldmanagementsystem.dto.request.InvoiceVoidRequest;

public interface InvoiceService {
    InvoiceListResponse getInvoices(
            String periodId,
            Integer year,
            Integer month,
            String towerId,
            String apartmentId,
            String status,
            String query,
            int page,
            int size
    );

    InvoiceSummaryResponse getSummary(
            String periodId,
            Integer year,
            Integer month,
            String towerId
    );

    InvoiceDetailResponse getInvoiceDetails(String invoiceId);

    InvoiceDetailResponse payInvoice(String invoiceId, InvoicePayRequest request);

    InvoiceDetailResponse voidInvoice(String invoiceId, InvoiceVoidRequest request);

    InvoicePrintFile printInvoice(String invoiceId, String type);

    record InvoicePrintFile(byte[] content, String filename) {
    }

    InvoiceExportFile exportInvoices(
            String periodId,
            Integer year,
            Integer month,
            String towerId,
            String apartmentId,
            String status,
            String query,
            String format
    );

    record InvoiceExportFile(byte[] content, String filename, String mediaType) {
    }
}

package org.example.emeraldmanagementsystem.repository;

import org.example.emeraldmanagementsystem.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, String> {
    List<InvoiceItem> findByInvoiceIdAndDeletedFalseOrderByCreatedAtAscIdAsc(String invoiceId);
}

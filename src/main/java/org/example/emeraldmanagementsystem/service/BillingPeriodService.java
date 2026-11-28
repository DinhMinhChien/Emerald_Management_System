package org.example.emeraldmanagementsystem.service;

import org.example.emeraldmanagementsystem.dto.response.BillingAvailableMonthsResponse;
import org.example.emeraldmanagementsystem.dto.response.BillingPeriodPreviewResponse;
import org.example.emeraldmanagementsystem.dto.request.BillingPeriodIssueRequest;
import org.example.emeraldmanagementsystem.dto.response.BillingPeriodIssueResponse;

public interface BillingPeriodService {
    BillingAvailableMonthsResponse getAvailableMonths();

    BillingPeriodPreviewResponse preview(
            Integer year,
            Integer month,
            String query,
            String towerId,
            int page,
            int size
    );

    BillingPeriodIssueResponse issue(BillingPeriodIssueRequest request);
}

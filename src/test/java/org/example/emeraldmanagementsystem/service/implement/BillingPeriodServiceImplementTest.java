package org.example.emeraldmanagementsystem.service.implement;

import org.example.emeraldmanagementsystem.dto.response.BillingAvailableMonthsResponse;
import org.example.emeraldmanagementsystem.entity.BillingPeriod;
import org.example.emeraldmanagementsystem.repository.BillingPeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingPeriodServiceImplementTest {
    @Mock
    private BillingPeriodRepository billingPeriodRepository;
    @InjectMocks
    private BillingPeriodServiceImplement billingPeriodService;

    @Test
    void returnsUnissuedMonthsAndDefaultsToMonthAfterLatestPeriod() {
        YearMonth current = YearMonth.now();
        BillingPeriod previousPeriod = period(current.minusMonths(2));
        BillingPeriod latestPeriod = period(current);
        when(billingPeriodRepository.findAllByDeletedFalseOrderByYearAscMonthAsc())
                .thenReturn(List.of(previousPeriod, latestPeriod));
        when(billingPeriodRepository.findFirstByDeletedFalseOrderByYearDescMonthDesc())
                .thenReturn(Optional.of(latestPeriod));

        BillingAvailableMonthsResponse response = billingPeriodService.getAvailableMonths();

        assertEquals(new BillingAvailableMonthsResponse.DefaultMonth(
                current.plusMonths(1).getYear(),
                current.plusMonths(1).getMonthValue()
        ), response.defaultMonth());
        List<BillingAvailableMonthsResponse.AvailableMonth> expected = java.util.stream.Stream
                .iterate(current.minusMonths(11), month -> month.plusMonths(1))
                .limit(13)
                .filter(month -> !month.equals(current.minusMonths(2)) && !month.equals(current))
                .map(month -> new BillingAvailableMonthsResponse.AvailableMonth(
                        month.getYear(),
                        month.getMonthValue(),
                        "%02d/%d".formatted(month.getMonthValue(), month.getYear())
                ))
                .toList();
        assertEquals(expected, response.items());
    }

    @Test
    void returnsNullDefaultMonthAndCurrentMonthAsAvailableWhenNoPeriodsExist() {
        when(billingPeriodRepository.findAllByDeletedFalseOrderByYearAscMonthAsc()).thenReturn(List.of());
        when(billingPeriodRepository.findFirstByDeletedFalseOrderByYearDescMonthDesc())
                .thenReturn(Optional.empty());

        BillingAvailableMonthsResponse response = billingPeriodService.getAvailableMonths();

        assertNull(response.defaultMonth());
        assertEquals(13, response.items().size());
        YearMonth current = YearMonth.now();
        assertEquals(current.getYear(), response.items().get(11).year());
        assertEquals(current.getMonthValue(), response.items().get(11).month());
        assertEquals(current.plusMonths(1).getYear(), response.items().getLast().year());
        assertEquals(current.plusMonths(1).getMonthValue(), response.items().getLast().month());
    }

    @Test
    void computesNextDefaultMonthAcrossYearBoundary() {
        BillingPeriod latestPeriod = period(YearMonth.of(2026, 12));
        when(billingPeriodRepository.findAllByDeletedFalseOrderByYearAscMonthAsc())
                .thenReturn(List.of(latestPeriod));
        when(billingPeriodRepository.findFirstByDeletedFalseOrderByYearDescMonthDesc())
                .thenReturn(Optional.of(latestPeriod));

        BillingAvailableMonthsResponse response = billingPeriodService.getAvailableMonths();

        assertEquals(new BillingAvailableMonthsResponse.DefaultMonth(2027, 1), response.defaultMonth());
    }

    private BillingPeriod period(YearMonth month) {
        BillingPeriod period = new BillingPeriod();
        period.setYear(month.getYear());
        period.setMonth(month.getMonthValue());
        return period;
    }
}

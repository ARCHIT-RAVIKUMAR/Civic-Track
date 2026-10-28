package com.civictrack.controller;

import com.civictrack.enums.ComplaintStatus;
import com.civictrack.service.ComplaintService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChartDataHelperTest {

    @Mock private ComplaintService complaintService;
    @Mock private com.civictrack.service.SatisfactionService satisfactionService;

    @Test
    void rejectedAndWithdrawnAreShownTogetherAndTotalsAreCorrect() {
        Map<ComplaintStatus, Long> counts = new EnumMap<>(ComplaintStatus.class);
        for (ComplaintStatus s : ComplaintStatus.values()) {
            counts.put(s, 0L);
        }
        counts.put(ComplaintStatus.SUBMITTED, 2L);
        counts.put(ComplaintStatus.REJECTED, 1L);
        counts.put(ComplaintStatus.WITHDRAWN, 3L);
        when(complaintService.countByStatus()).thenReturn(counts);
        Map<String, Long> byCategory = new LinkedHashMap<>();
        byCategory.put("Roads", 6L);
        when(complaintService.countByCategory()).thenReturn(byCategory);

        Model model = new ExtendedModelMap();
        new ChartDataHelper(complaintService, satisfactionService).addTo(model);

        List<?> labels = (List<?>) model.getAttribute("statusLabels");
        List<?> values = (List<?>) model.getAttribute("statusValues");
        assertEquals(7, labels.size());
        assertEquals("Rejected / withdrawn", labels.get(6));
        assertEquals(4L, values.get(6));
        assertEquals(true, model.getAttribute("chartHasData"));
    }

    @Test
    void noComplaintsMeansNoChartData() {
        Map<ComplaintStatus, Long> counts = new EnumMap<>(ComplaintStatus.class);
        when(complaintService.countByStatus()).thenReturn(counts);
        when(complaintService.countByCategory()).thenReturn(new LinkedHashMap<>());

        Model model = new ExtendedModelMap();
        new ChartDataHelper(complaintService, satisfactionService).addTo(model);

        assertEquals(false, model.getAttribute("chartHasData"));
    }
}

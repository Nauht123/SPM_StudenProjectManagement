package vn.huflit.spm;

import org.junit.jupiter.api.Test;
import vn.huflit.spm.service.SubmissionService;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionLateRuleTest {
    private final LocalDate due = LocalDate.of(2026, 11, 10);

    @Test
    void submittedOnDueDateEndOfDayIsNotLate() {
        assertFalse(SubmissionService.isLate(due, LocalDateTime.of(2026, 11, 10, 23, 59)));
    }

    @Test
    void submittedBeforeDueDateIsNotLate() {
        assertFalse(SubmissionService.isLate(due, LocalDateTime.of(2026, 11, 9, 8, 0)));
    }

    @Test
    void submittedAfterDueDateIsLate() {
        assertTrue(SubmissionService.isLate(due, LocalDateTime.of(2026, 11, 11, 0, 1)));
    }
}

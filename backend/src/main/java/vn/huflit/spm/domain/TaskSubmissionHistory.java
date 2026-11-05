package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Moi lan bao hoan thanh task (khong chi lan cuoi), kem ket qua duyet cua nhom truong. */
@Entity
@Table(name = "task_submission_history")
@Getter
@Setter
@NoArgsConstructor
public class TaskSubmissionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Column(length = 1000)
    private String note;

    private String fileName;

    @Column(length = 1000)
    private String storedPath;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private SubmissionOutcome outcome = SubmissionOutcome.PENDING;

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewNote;
}

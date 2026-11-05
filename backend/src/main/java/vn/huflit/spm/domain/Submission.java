package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Column(name = "version_no", nullable = false)
    private int version;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 1000)
    private String storedPath;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "is_late", nullable = false)
    private boolean late;
}

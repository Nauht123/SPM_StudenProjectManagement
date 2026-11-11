package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Danh gia cheo giua cac thanh vien theo tung milestone chinh. Hai tieu chi 1..5:
 * muc do dong gop va do nang cua cong viec duoc giao.
 * AN DANH: nguoi danh gia chi duoc doc lai danh gia cua chinh minh; moi API tong hop/hien thi cho nguoi khac
 * (giang vien, nguoi duoc danh gia) khong bao gio tra ve evaluator.
 */
@Entity
@Table(name = "peer_evaluations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"milestone_id", "evaluator_id", "evaluatee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PeerEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @ManyToOne(optional = false)
    @JoinColumn(name = "evaluator_id")
    private User evaluator;

    @ManyToOne(optional = false)
    @JoinColumn(name = "evaluatee_id")
    private User evaluatee;

    /** Muc do dong gop 1..5. */
    @Column(nullable = false)
    private int contributionScore;

    /** Do nang cua cong viec duoc giao 1..5. */
    @Column(nullable = false)
    private int workloadScore;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

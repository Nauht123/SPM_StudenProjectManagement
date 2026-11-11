package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Giang vien cong/tru diem dong gop cua mot thanh vien, bat buoc co ly do. Chi them moi / thu hoi, khong sua. */
@Entity
@Table(name = "contribution_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class ContributionAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id")
    private User member;

    /** Milestone chinh lien quan (khong bat buoc). */
    @ManyToOne
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    /** So diem cong (duong) hoac tru (am). */
    @Column(nullable = false)
    private double delta;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(optional = false)
    @JoinColumn(name = "adjusted_by")
    private User adjustedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean revoked = false;

    private LocalDateTime revokedAt;

    @ManyToOne
    @JoinColumn(name = "revoked_by")
    private User revokedBy;
}

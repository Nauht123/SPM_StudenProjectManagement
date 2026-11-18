package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Yeu cau thay doi nhom (them / xoa / thay thanh vien, chuyen nhom truong) sau khi de tai da duoc duyet.
 * Nhom truong (hoac thanh vien xin roi nhom) gui, giang vien huong dan duyet; khong ai duoc don phuong thay doi.
 */
@Entity
@Table(name = "team_change_requests", indexes = @Index(columnList = "team_id,status"))
@Getter
@Setter
@NoArgsConstructor
public class TeamChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamChangeType type;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    /** Thanh vien bi xoa / bi thay / duoc chon lam nhom truong moi (tuy loai yeu cau). */
    @ManyToOne
    @JoinColumn(name = "target_member_id")
    private User targetMember;

    /** Sinh vien moi duoc them (ADD_MEMBER, REPLACE_MEMBER). */
    @ManyToOne
    @JoinColumn(name = "new_member_id")
    private User newMember;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status = ChangeRequestStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(length = 1000)
    private String decisionNote;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime decidedAt;
}

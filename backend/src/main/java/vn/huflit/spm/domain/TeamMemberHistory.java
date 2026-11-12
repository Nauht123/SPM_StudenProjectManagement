package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Lich su thay doi thanh vien nhom: ai them/xoa ai, luc nao. Chi ghi them, khong sua/xoa. */
@Entity
@Table(name = "team_member_history")
@Getter
@Setter
@NoArgsConstructor
public class TeamMemberHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id")
    private User member;

    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberAction action;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

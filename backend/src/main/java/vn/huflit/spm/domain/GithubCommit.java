package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Commit da dong bo tu GitHub. Nguoi thuc hien duoc suy ra luc doc tu bang GithubAccount (khong luu cung). */
@Entity
@Table(name = "github_commits", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "sha"}),
        indexes = @Index(columnList = "team_id,committedAt"))
@Getter
@Setter
@NoArgsConstructor
public class GithubCommit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false, length = 64)
    private String sha;

    @Column(length = 100)
    private String authorLogin;

    @Column(length = 200)
    private String authorName;

    @Column(length = 500)
    private String message;

    @Column(nullable = false)
    private LocalDateTime committedAt;

    @Column(length = 300)
    private String url;
}

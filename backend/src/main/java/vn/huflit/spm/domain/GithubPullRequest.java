package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Pull request da dong bo tu GitHub; state: OPEN, MERGED hoac CLOSED. */
@Entity
@Table(name = "github_pull_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "number"}))
@Getter
@Setter
@NoArgsConstructor
public class GithubPullRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(length = 300)
    private String title;

    @Column(nullable = false, length = 10)
    private String state;

    @Column(length = 100)
    private String authorLogin;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime closedAt;

    private LocalDateTime mergedAt;

    @Column(length = 300)
    private String url;
}

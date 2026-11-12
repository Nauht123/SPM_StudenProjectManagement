package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tai khoan GitHub cua mot thanh vien trong nhom (dung de gan commit/PR cho dung nguoi). */
@Entity
@Table(name = "github_accounts", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GithubAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String login;
}

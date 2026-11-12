package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lien ket mot nhom voi mot repo GitHub. Token ca nhan (neu co) duoc MA HOA truoc khi luu va
 * khong bao gio duoc tra ve qua API.
 */
@Entity
@Table(name = "github_repo_links", uniqueConstraints = @UniqueConstraint(columnNames = "team_id"))
@Getter
@Setter
@NoArgsConstructor
public class GithubRepoLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false, length = 100)
    private String repo;

    /** Token da ma hoa (AES-GCM, base64); null neu repo cong khai khong can token. */
    @Column(length = 800)
    private String encryptedToken;

    @ManyToOne(optional = false)
    @JoinColumn(name = "linked_by")
    private User linkedBy;

    @Column(nullable = false)
    private LocalDateTime linkedAt = LocalDateTime.now();

    private LocalDateTime lastSyncAt;

    @Column(length = 500)
    private String lastSyncError;
}

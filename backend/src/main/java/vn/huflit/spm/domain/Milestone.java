package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "milestones")
@Getter
@Setter
@NoArgsConstructor
public class Milestone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    /** null = milestone chinh (do giang vien quan ly); khac null = milestone phu (do nhom truong quan ly). */
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Milestone parent;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Luu tru (Archive) thay cho xoa khi milestone da co du lieu lien quan. Milestone luu tru chi doc, khong tinh vao tien do. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived = false;

    private LocalDateTime archivedAt;
}

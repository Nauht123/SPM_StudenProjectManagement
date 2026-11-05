package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    private LocalDate dueDate;

    /** Ket qua thanh vien bao hoan thanh (file/ghi chu la tuy chon). */
    @Column(length = 1000)
    private String resultNote;

    private String resultFileName;

    @Column(length = 1000)
    private String resultStoredPath;

    /** Thoi diem nop (bao hoan thanh) gan nhat. */
    private LocalDateTime submittedAt;

    /** Noi dung nhom truong yeu cau sua (neu co). */
    @Column(length = 1000)
    private String reviewNote;

    private LocalDateTime confirmedAt;
}

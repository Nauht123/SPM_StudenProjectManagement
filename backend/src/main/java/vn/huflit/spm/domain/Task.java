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
    public static final int DEFAULT_DIFFICULTY = 3;
    public static final int DEFAULT_WEIGHT = 3;
    public static final int DEFAULT_WORKLOAD_HOURS = 4;

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

    /** Nguoi giao viec va thoi diem giao (lan phan cong hien tai). Lich su day du nam o TaskAssignmentHistory. */
    @ManyToOne
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    private LocalDateTime assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    private LocalDate dueDate;

    /** Do kho 1..5. */
    @Column(nullable = false, columnDefinition = "int default 3")
    private int difficulty = DEFAULT_DIFFICULTY;

    /** Trong so 1..10: ti trong cua task trong tien do va diem dong gop. */
    @Column(nullable = false, columnDefinition = "int default 3")
    private int weight = DEFAULT_WEIGHT;

    /** Khoi luong cong viec uoc tinh (gio). */
    @Column(nullable = false, columnDefinition = "int default 4")
    private int workloadHours = DEFAULT_WORKLOAD_HOURS;

    /** So lan bi yeu cau sua. */
    @Column(nullable = false, columnDefinition = "int default 0")
    private int revisionCount = 0;

    /** Diem chat luong 1..5 do nhom truong cham khi xac nhan (khong bat buoc). */
    private Integer qualityRating;

    /** Nguoi de xuat (neu task xuat phat tu de xuat cua thanh vien). */
    @ManyToOne
    @JoinColumn(name = "suggested_by")
    private User suggestedBy;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime cancelledAt;

    @Column(length = 1000)
    private String cancelReason;

    /** Ket qua thanh vien bao hoan thanh gan nhat (file/ghi chu la tuy chon). Moi lan nop nam o TaskSubmissionHistory. */
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

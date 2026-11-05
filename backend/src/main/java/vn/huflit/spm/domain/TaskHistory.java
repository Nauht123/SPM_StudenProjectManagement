package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Nhat ky thay doi cua Task: ai, luc nao, lam gi, chuyen trang thai nao. */
@Entity
@Table(name = "task_history")
@Getter
@Setter
@NoArgsConstructor
public class TaskHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskStatus toStatus;

    @Column(length = 1500)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

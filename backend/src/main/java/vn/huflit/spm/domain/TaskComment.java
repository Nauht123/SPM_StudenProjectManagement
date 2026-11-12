package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** Binh luan trong task (thanh vien nhom, giang vien huong dan, admin). Chi tac gia hoac admin xoa duoc. */
@Entity
@Table(name = "task_comments", indexes = @Index(columnList = "task_id,createdAt"))
@Getter
@Setter
@NoArgsConstructor
public class TaskComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE) // xoa task (chi khi chua phat sinh du lieu) thi xoa luon binh luan
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

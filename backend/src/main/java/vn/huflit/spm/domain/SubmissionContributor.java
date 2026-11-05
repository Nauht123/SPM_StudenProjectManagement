package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Thanh vien thuc su dong gop noi dung cho bai nop milestone (khac voi nguoi upload Submission.submittedBy). */
@Entity
@Table(name = "submission_contributors",
        uniqueConstraints = @UniqueConstraint(columnNames = {"submission_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SubmissionContributor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;
}

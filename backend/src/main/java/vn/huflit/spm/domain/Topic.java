package vn.huflit.spm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "topics")
@Getter
@Setter
@NoArgsConstructor
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "lecturer_id")
    private User lecturer;

    @Column(nullable = false)
    private int maxGroups = 1;

    @Column(length = 50)
    private String semester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TopicStatus status = TopicStatus.OPEN;
}

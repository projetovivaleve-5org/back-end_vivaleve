
package vivaleve.proj.model;

import jakarta.persistence.*;
import lombok.*;
import vivaleve.proj.model.enums.MoodType;

import java.time.LocalDate;

@Entity
@Table(name = "registros_humor")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoodLogEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MoodType mood;

    @Column(length = 500)
    private String note;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;
}


package vivaleve.proj.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;


@Table(name = "usuarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class UserEntity extends BaseEntity {


        @Column(nullable = false, unique = true, length = 100)
        private String email;

        @Column(name = "password", nullable = false, length = 64)
        private String password;


    }


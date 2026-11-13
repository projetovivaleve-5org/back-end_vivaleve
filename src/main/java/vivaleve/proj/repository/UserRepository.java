package vivaleve.proj.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vivaleve.proj.model.UserEntity;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /** Usado para vincular uma conta antiga (email + senha) no primeiro login Google. */
    Optional<UserEntity> findByEmail(String email);

    /** NOVO — Busca principal do login Google: acha o usuário pelo {@code sub} da conta Google. */
    Optional<UserEntity> findByGoogleSub(String googleSub);
}

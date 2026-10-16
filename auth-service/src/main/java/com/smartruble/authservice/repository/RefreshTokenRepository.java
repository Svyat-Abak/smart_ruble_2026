package com.smartruble.authservice.repository;

import com.smartruble.authservice.entity.RefreshToken;
import com.smartruble.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("delete from RefreshToken t where t.token = :token")
    int deleteByToken(String token);

    @Modifying
    @Query("delete from RefreshToken t where t.user = :user")
    int deleteAllByUser(User user);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}

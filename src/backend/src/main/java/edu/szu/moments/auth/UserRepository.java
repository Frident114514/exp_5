package edu.szu.moments.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByAccount(String account);
}

package edu.szu.moments.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByAccount(String account);

    @Query("select new edu.szu.moments.auth.UserAuthState(u.id, u.account, u.nickname, u.role, u.status, u.tokenVersion) "
            + "from UserAccount u where u.id = :id")
    Optional<UserAuthState> findAuthState(@Param("id") long id);
}

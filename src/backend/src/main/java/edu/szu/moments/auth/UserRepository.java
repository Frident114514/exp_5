package edu.szu.moments.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByAccount(String account);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> lockById(@Param("id") long id);

    @Query("select u from UserAccount u where :keyword = '' or "
            + "locate(lower(:keyword), lower(u.account)) > 0 or locate(lower(:keyword), lower(u.nickname)) > 0")
    Page<UserAccount> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("select new edu.szu.moments.auth.UserAuthState(u.id, u.account, u.nickname, u.role, u.status, u.tokenVersion) "
            + "from UserAccount u where u.id = :id")
    Optional<UserAuthState> findAuthState(@Param("id") long id);
}

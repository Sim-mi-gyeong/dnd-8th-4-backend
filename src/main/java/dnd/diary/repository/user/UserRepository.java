package dnd.diary.repository.user;

import dnd.diary.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    Boolean existsByEmail(String email);
    Boolean existsByNickName(String nickName);
    Optional<User> findOneWithAuthoritiesByEmail(String email);
    List<User> findByNickNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String nickName, String email);
    List<User> findByNickNameContainingIgnoreCase(String nickName);
}

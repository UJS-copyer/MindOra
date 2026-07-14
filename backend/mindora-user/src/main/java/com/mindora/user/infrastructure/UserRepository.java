package com.mindora.user.infrastructure;

import com.mindora.user.domain.UserAccount;
import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findByEmail(String email);

    UserAccount save(UserAccount user);
}

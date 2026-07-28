package com.mindora.user.domain;

import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findByEmail(String email);

    UserAccount save(UserAccount user);
}

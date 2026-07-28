package com.mindora.user.infrastructure.persistence.memory;

import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return Optional.ofNullable(users.get(email));
    }

    @Override
    public UserAccount save(UserAccount user) {
        users.put(user.email(), user);
        return user;
    }
}

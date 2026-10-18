package com.mindora.user.infrastructure.persistence.memory;

import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserRepository implements UserRepository {
    private final Map<UUID, UserAccount> users = new ConcurrentHashMap<>();

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.email().equals(email))
                .findFirst();
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public UserAccount save(UserAccount user) {
        users.put(user.id(), user);
        return user;
    }

    @Override
    public int bumpTokenVersion(UUID id) {
        UserAccount updated = users.computeIfPresent(id, (key, user) -> new UserAccount(
                user.id(),
                user.email(),
                user.passwordHash(),
                user.roles(),
                user.tokenVersion() + 1,
                user.status(),
                user.deleted(),
                user.displayName()));
        return updated == null ? 0 : 1;
    }
}

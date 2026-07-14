package com.mindora.user.infrastructure;

import com.mindora.user.domain.UserAccount;

public interface TokenService {
    String issue(UserAccount user);
}

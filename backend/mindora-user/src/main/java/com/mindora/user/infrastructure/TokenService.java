package com.mindora.user.infrastructure;

import com.mindora.user.domain.UserAccount;
import com.mindora.user.domain.TokenPrincipal;
import java.util.Optional;

public interface TokenService {
    String issue(UserAccount user);

    Optional<TokenPrincipal> verify(String accessToken);
}

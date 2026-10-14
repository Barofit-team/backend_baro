package com.barofit.identity.auth;

import com.barofit.identity.token.IssuedTokens;
import com.barofit.identity.user.User;

public record LoginResult(IssuedTokens tokens, User user) {
}

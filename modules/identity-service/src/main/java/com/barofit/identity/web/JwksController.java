package com.barofit.identity.web;

import com.barofit.identity.api.IdentityPublicPaths;
import com.barofit.identity.token.JwtKeys;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 바로핏 JWT 공개키 목록. 모놀리스 안에서는 쓰지 않고, 서비스 분리에 대비해 노출만 해 둔다.
 */
@RestController
@RequiredArgsConstructor
class JwksController {

	private final JwtKeys jwtKeys;

	@GetMapping(path = IdentityPublicPaths.JWKS, produces = MediaType.APPLICATION_JSON_VALUE)
	Map<String, Object> jwks() {
		return jwtKeys.getPublicJwks().toJSONObject(true);
	}
}

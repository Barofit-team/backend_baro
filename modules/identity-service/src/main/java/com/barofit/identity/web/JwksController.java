package com.barofit.identity.web;

import com.barofit.identity.api.IdentityPublicPaths;
import com.barofit.identity.token.JwtKeys;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** JWT 공개키 목록. 서비스 분리 대비 */
@RestController
@RequiredArgsConstructor
class JwksController {

	private final JwtKeys jwtKeys;

	@GetMapping(path = IdentityPublicPaths.JWKS, produces = MediaType.APPLICATION_JSON_VALUE)
	Map<String, Object> jwks() {
		return jwtKeys.getPublicJwks().toJSONObject(true);
	}
}

package com.barofit.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtKeysTest {

	@Test
	void PEM과_JWK_Set_파일에서_키를_읽는다() {
		TestJwtKeyFiles files = TestJwtKeyFiles.create("kid-1");

		JwtKeys keys = JwtKeys.load(properties(files, "kid-1"));

		assertThat(keys.currentKid()).isEqualTo("kid-1");
		assertThat(keys.getSigningKey().isPrivate()).isTrue();
		assertThat(keys.getPublicJwks().getKeys()).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
	}

	@Test
	void 키_교체_기간에는_이전_키도_공개키_목록에_남는다() {
		RSAKey oldKey = new RSAKey.Builder((RSAPublicKey) TestJwtKeyFiles.generateKeyPair().getPublic())
				.keyID("old").build();
		TestJwtKeyFiles files = TestJwtKeyFiles.create("new", TestJwtKeyFiles.generateKeyPair(), List.of(oldKey));

		JwtKeys keys = JwtKeys.load(properties(files, "new"));

		assertThat(keys.getPublicJwks().getKeyByKeyId("old")).isNotNull();
		assertThat(keys.currentKid()).isEqualTo("new");
	}

	@Test
	void 현재_kid가_공개키_목록에_없으면_시작하지_않는다() {
		TestJwtKeyFiles files = TestJwtKeyFiles.create("kid-1");

		assertThatThrownBy(() -> JwtKeys.load(properties(files, "unknown")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("unknown");
	}

	@Test
	void 개인키와_현재_kid의_공개키가_짝이_아니면_시작하지_않는다() {
		RSAKey otherPublic = new RSAKey.Builder((RSAPublicKey) TestJwtKeyFiles.generateKeyPair().getPublic())
				.keyID("other").build();
		TestJwtKeyFiles files = TestJwtKeyFiles.create("kid-1", TestJwtKeyFiles.generateKeyPair(), List.of(otherPublic));

		assertThatThrownBy(() -> JwtKeys.load(properties(files, "other")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("짝이 맞지 않습니다");
	}

	private static JwtKeyProperties properties(TestJwtKeyFiles files, String kid) {
		return new JwtKeyProperties(files.privateKeyPath().toString(), files.publicJwksPath().toString(), kid);
	}
}

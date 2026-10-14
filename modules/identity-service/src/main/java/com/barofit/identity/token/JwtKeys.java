package com.barofit.identity.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.util.Base64;
import java.util.UUID;
import lombok.Getter;

/**
 * 바로핏 JWT 서명 키와 공개키 목록. 생성 시점에 개인키와 현재 kid의 공개키가 짝인지 검증하고, 아니면 앱이 뜨지 않는다.
 */
@Getter
public class JwtKeys {

	/** 서명용 키(개인키 포함). kid는 현재 서명 kid */
	private final RSAKey signingKey;

	/** 검증용 공개키 목록 */
	private final JWKSet publicJwks;

	public JwtKeys(RSAPrivateKey privateKey, JWKSet publicJwks, String currentKid) {
		JWK current = publicJwks.getKeyByKeyId(currentKid);
		if (!(current instanceof RSAKey currentPublic)) {
			throw new IllegalStateException("공개키 목록에 현재 kid('" + currentKid + "')의 RSA 키가 없습니다");
		}
		try {
			this.signingKey = new RSAKey.Builder(currentPublic.toRSAPublicKey())
					.privateKey(privateKey)
					.keyID(currentKid)
					.build();
		} catch (JOSEException e) {
			throw new IllegalStateException("현재 kid의 공개키를 읽을 수 없습니다", e);
		}
		this.publicJwks = publicJwks.toPublicJWKSet();
		verifyKeyPair(privateKey, currentPublic);
	}

	public static JwtKeys load(JwtKeyProperties properties) {
		try {
			RSAPrivateKey privateKey = readPkcs8PrivateKey(Files.readString(properties.privateKeyFile()));
			JWKSet jwks = JWKSet.parse(Files.readString(properties.publicJwksFile()));
			return new JwtKeys(privateKey, jwks, properties.currentKid());
		} catch (IOException | ParseException | GeneralSecurityException e) {
			throw new IllegalStateException("JWT 키 파일을 읽을 수 없습니다", e);
		}
	}

	public String currentKid() {
		return signingKey.getKeyID();
	}

	static RSAPrivateKey readPkcs8PrivateKey(String pem) throws GeneralSecurityException {
		if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
			throw new GeneralSecurityException("PKCS#8 형식(BEGIN PRIVATE KEY)의 PEM이 아닙니다");
		}
		String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		byte[] der = Base64.getDecoder().decode(base64);
		return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
	}

	// 키 교체 사고(개인키와 공개키 목록이 어긋남)를 시작 시점에 잡는다
	private static void verifyKeyPair(RSAPrivateKey privateKey, RSAKey publicKey) {
		try {
			JWSObject probe = new JWSObject(new JWSHeader(JWSAlgorithm.RS256), new Payload(UUID.randomUUID().toString()));
			probe.sign(new RSASSASigner(privateKey));
			if (!probe.verify(new RSASSAVerifier(publicKey))) {
				throw new IllegalStateException("개인키와 현재 kid('" + publicKey.getKeyID() + "')의 공개키가 짝이 맞지 않습니다");
			}
		} catch (JOSEException e) {
			throw new IllegalStateException("JWT 키 정합성 검증에 실패했습니다", e);
		}
	}
}

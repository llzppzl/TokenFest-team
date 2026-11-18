package com.tokenfest.gateway.crypto;

import java.time.ZoneId;
import java.util.Base64;

import com.tokenfest.gateway.config.TokenFestProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTests {

	private static CryptoService service(byte fill) {
		byte[] key = new byte[32];
		java.util.Arrays.fill(key, fill);
		return new CryptoService(new TokenFestProperties("test", ZoneId.of("UTC"),
				Base64.getEncoder().encodeToString(key), null, 1000, null, null, null));
	}

	@Test
	void encryptThenDecryptReturnsOriginal() {
		CryptoService crypto = service((byte) 1);
		String encrypted = crypto.encrypt("sk-secret-123");
		assertThat(encrypted).doesNotContain("sk-secret");
		assertThat(crypto.decrypt(encrypted)).isEqualTo("sk-secret-123");
	}

	@Test
	void sameTextGivesDifferentCipherText() {
		CryptoService crypto = service((byte) 1);
		assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
	}

	@Test
	void decryptWithOtherKeyFails() {
		String encrypted = service((byte) 1).encrypt("sk-secret");
		assertThatThrownBy(() -> service((byte) 2).decrypt(encrypted)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TOKENFEST_ENCRYPTION_KEY");
	}

	@Test
	void missingOrShortKeyIsRejected() {
		assertThatThrownBy(() -> CryptoService.parseKey("")).hasMessageContaining("not set");
		assertThatThrownBy(() -> CryptoService.parseKey(Base64.getEncoder().encodeToString(new byte[16])))
			.hasMessageContaining("32 bytes");
	}

}

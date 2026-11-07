package com.tokenfest.gateway.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.tokenfest.gateway.config.TokenFestProperties;

import org.springframework.stereotype.Service;

/**
 * Encrypts provider API keys with AES-256-GCM. The stored value is
 * base64(12-byte IV + ciphertext + tag).
 */
@Service
public class CryptoService {

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private final SecretKey key;

	private final SecureRandom random = new SecureRandom();

	public CryptoService(TokenFestProperties properties) {
		this.key = parseKey(properties.encryptionKey());
	}

	static SecretKey parseKey(String base64) {
		if (base64 == null || base64.isBlank()) {
			throw new IllegalStateException(
					"TOKENFEST_ENCRYPTION_KEY is not set. Generate one with: openssl rand -base64 32");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64.trim());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("TOKENFEST_ENCRYPTION_KEY is not valid base64", e);
		}
		if (bytes.length != 32) {
			throw new IllegalStateException("TOKENFEST_ENCRYPTION_KEY must be 32 bytes (base64 of 32 random bytes)");
		}
		return new SecretKeySpec(bytes, "AES");
	}

	/**
	 * Derives a separate HMAC-SHA256 key for another purpose (for example signing
	 * dashboard logins), so only one secret has to be configured.
	 */
	public SecretKey deriveKey(String purpose) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
			return new SecretKeySpec(mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	public String encrypt(String plainText) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder()
				.encodeToString(ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array());
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Encryption failed", e);
		}
	}

	public String decrypt(String encrypted) {
		try {
			byte[] data = Base64.getDecoder().decode(encrypted);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
			byte[] plain = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Could not decrypt provider API key. Was TOKENFEST_ENCRYPTION_KEY changed?",
					e);
		}
	}

}

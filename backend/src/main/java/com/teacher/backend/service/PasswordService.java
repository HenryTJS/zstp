package com.teacher.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordService {

    private static final int SCRYPT_N = 32768;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SCRYPT_KEY_LENGTH = 64;
    private static final String SALT_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SecureRandom secureRandom = new SecureRandom();

    public String hashPassword(String rawPassword) {
        String salt = generateSalt(16);
        byte[] derived = SCrypt.generate(
            rawPassword.getBytes(StandardCharsets.UTF_8),
            salt.getBytes(StandardCharsets.UTF_8),
            SCRYPT_N,
            SCRYPT_R,
            SCRYPT_P,
            SCRYPT_KEY_LENGTH
        );
        return "scrypt:" + SCRYPT_N + ":" + SCRYPT_R + ":" + SCRYPT_P + "$" + salt + "$" + HexFormat.of().formatHex(derived);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedHash)) {
            return false;
        }

        try {
            if (storedHash.startsWith("scrypt:")) {
                return verifyScrypt(rawPassword, storedHash);
            }
            if (storedHash.startsWith("pbkdf2:")) {
                return verifyPbkdf2(rawPassword, storedHash);
            }
        } catch (RuntimeException exception) {
            return false;
        }

        return MessageDigest.isEqual(
            rawPassword.getBytes(StandardCharsets.UTF_8),
            storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean verifyScrypt(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$", 3);
        if (parts.length != 3) {
            return false;
        }

        String[] methodParts = parts[0].split(":");
        if (methodParts.length != 4) {
            return false;
        }

        int n = Integer.parseInt(methodParts[1]);
        int r = Integer.parseInt(methodParts[2]);
        int p = Integer.parseInt(methodParts[3]);
        String salt = parts[1];
        byte[] expected = HexFormat.of().parseHex(parts[2]);
        byte[] actual = SCrypt.generate(
            rawPassword.getBytes(StandardCharsets.UTF_8),
            salt.getBytes(StandardCharsets.UTF_8),
            n,
            r,
            p,
            expected.length
        );
        return MessageDigest.isEqual(actual, expected);
    }

    private boolean verifyPbkdf2(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$", 3);
        if (parts.length != 3) {
            return false;
        }

        String[] methodParts = parts[0].split(":");
        if (methodParts.length < 2) {
            return false;
        }

        String algorithm = methodParts[1];
        int iterations = methodParts.length >= 3 ? Integer.parseInt(methodParts[2]) : 600000;
        String salt = parts[1];
        byte[] expected = HexFormat.of().parseHex(parts[2]);
        byte[] actual = pbkdf2(rawPassword, salt, algorithm, iterations, expected.length);
        return MessageDigest.isEqual(actual, expected);
    }

    private byte[] pbkdf2(String rawPassword, String salt, String algorithm, int iterations, int keyLengthBytes) {
        String jcaAlgorithm = switch (algorithm.toLowerCase()) {
            case "sha1" -> "PBKDF2WithHmacSHA1";
            case "sha512" -> "PBKDF2WithHmacSHA512";
            default -> "PBKDF2WithHmacSHA256";
        };

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(jcaAlgorithm);
            KeySpec spec = new PBEKeySpec(
                rawPassword.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                iterations,
                keyLengthBytes * 8
            );
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Unsupported PBKDF2 configuration", exception);
        }
    }

    private String generateSalt(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(SALT_ALPHABET.length());
            builder.append(SALT_ALPHABET.charAt(index));
        }
        return builder.toString();
    }
}

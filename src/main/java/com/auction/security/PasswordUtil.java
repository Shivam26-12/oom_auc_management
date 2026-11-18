package com.auction.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class PasswordUtil {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT_ID = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int MIN_VERIFY_ITERATIONS = 100_000;
    private static final int MAX_VERIFY_ITERATIONS = 2_000_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String hash(char[] password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }
        try {
            if (password.length == 0) {
                throw new IllegalArgumentException("Password cannot be empty");
            }
            byte[] salt = new byte[SALT_BYTES];
            byte[] derivedHash = null;
            SECURE_RANDOM.nextBytes(salt);
            try {
                derivedHash = derive(password, salt, ITERATIONS);
                Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
                return FORMAT_ID + "$" + ITERATIONS + "$" + encoder.encodeToString(salt)
                        + "$" + encoder.encodeToString(derivedHash);
            } finally {
                Arrays.fill(salt, (byte) 0);
                if (derivedHash != null) {
                    Arrays.fill(derivedHash, (byte) 0);
                }
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static boolean verify(char[] password, String storedHash) {
        if (password == null) {
            return false;
        }
        byte[] salt = null;
        byte[] expectedHash = null;
        byte[] actualHash = null;
        try {
            if (storedHash == null || password.length == 0) {
                return false;
            }
            String[] parts = storedHash.split("\\$", -1);
            if (parts.length != 4 || !FORMAT_ID.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < MIN_VERIFY_ITERATIONS || iterations > MAX_VERIFY_ITERATIONS) {
                return false;
            }
            Base64.Decoder decoder = Base64.getDecoder();
            salt = decoder.decode(parts[2]);
            expectedHash = decoder.decode(parts[3]);
            if (salt.length != SALT_BYTES || expectedHash.length != HASH_BITS / 8) {
                return false;
            }
            actualHash = derive(password, salt, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            Arrays.fill(password, '\0');
            clear(salt);
            clear(expectedHash);
            clear(actualHash);
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } finally {
            keySpec.clearPassword();
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }
}
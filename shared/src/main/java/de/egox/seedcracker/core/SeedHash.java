package de.egox.seedcracker.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SeedHash {
    private SeedHash() { }
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    });
    public static long of(long seed) {
        byte[] input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(seed).array();
        return ByteBuffer.wrap(SHA256.get().digest(input)).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}

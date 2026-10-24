package org.opensaiclink.trial;

import java.util.Arrays;

/** The total length includes optional extension bytes, not the fixed header. */
public final class WlFrame {
    public static final int MAX_PAYLOAD = 4 * 1024 * 1024;
    public final int type;
    public final byte[] payload;

    public WlFrame(int type, byte[] payload) {
        if (type < 0 || type > 255 || payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Invalid WL frame");
        }
        this.type = type;
        this.payload = payload;
    }

    public byte[] encode() {
        byte[] out = new byte[8 + payload.length];
        out[0] = 'W'; out[1] = 'L'; out[2] = (byte) type;
        int n = payload.length;
        out[4] = (byte) (n >>> 24); out[5] = (byte) (n >>> 16);
        out[6] = (byte) (n >>> 8); out[7] = (byte) n;
        System.arraycopy(payload, 0, out, 8, n);
        return out;
    }

    public interface Receiver { void receive(WlFrame frame); }

    public static final class Parser {
        private byte[] buffer = new byte[65536];
        private int size;
        public long discardedBytes;

        public void feed(byte[] source, int count, Receiver receiver) {
            if (count < 0 || count > source.length || count > 65536) {
                throw new IllegalArgumentException("Invalid USB read size");
            }
            if (size + count > MAX_PAYLOAD + 65544) {
                throw new IllegalStateException("WL receive limit exceeded");
            }
            if (size + count > buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.min(MAX_PAYLOAD + 65544,
                        Math.max(size + count, buffer.length * 2)));
            }
            System.arraycopy(source, 0, buffer, size, count);
            size += count;
            int p = 0;
            while (size - p >= 8) {
                if (buffer[p] != 'W' || buffer[p + 1] != 'L') {
                    p++; discardedBytes++; continue;
                }
                int extension = buffer[p + 3] & 255;
                long length = ((long) (buffer[p + 4] & 255) << 24)
                        | ((long) (buffer[p + 5] & 255) << 16)
                        | ((long) (buffer[p + 6] & 255) << 8) | (buffer[p + 7] & 255);
                if (length < extension || length > MAX_PAYLOAD) {
                    p++; discardedBytes++; continue;
                }
                int end = p + 8 + (int) length;
                if (end > size) break;
                receiver.receive(new WlFrame(buffer[p + 2] & 255,
                        Arrays.copyOfRange(buffer, p + 8 + extension, end)));
                p = end;
            }
            if (p > 0) {
                System.arraycopy(buffer, p, buffer, 0, size - p);
                size -= p;
            }
        }
    }
}

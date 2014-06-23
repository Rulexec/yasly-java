package by.muna.buffers.util;

import by.muna.buffers.IBufferReadable;

import java.nio.ByteBuffer;

public class BufferReadableUtil {
    /**
     * Создаёт 128-байтный буффер и читает в него, пока reader.read не возвратит 0.
     *
     * Избегайте использования этой функции.
     * @param reader
     */
    public static void clear(IBufferReadable reader) {
        ByteBuffer buffer = ByteBuffer.allocate(128);

        while (reader.read(buffer) > 0) {
            buffer.position(0);
        }
    }
}

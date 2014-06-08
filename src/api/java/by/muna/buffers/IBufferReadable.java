package by.muna.buffers;

import java.nio.ByteBuffer;

public interface IBufferReadable {
    /**
     * @param buffer Буффер, в который записать.
     * @return Количество байт, которое было прочитано в буффер.
     */
    int read(ByteBuffer buffer);
}

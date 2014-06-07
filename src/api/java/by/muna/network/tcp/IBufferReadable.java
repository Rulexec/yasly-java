package by.muna.network.tcp;

import java.nio.ByteBuffer;

public interface IBufferReadable {
    /**
     * @param buffer Буффер, в который записать.
     * @return Количество байт, которое было прочитано.
     */
    int read(ByteBuffer buffer);
}

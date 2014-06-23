package by.muna.network.tcp;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public interface ITCPSocket {
    SocketAddress getAddress();

    void setListener(ITCPSocketListener listener);
    void requestWriting(Supplier<ByteBuffer> bufferProvider, ITCPSendStatusListener listener);
    default void requestWriting(ByteBuffer buffer, ITCPSendStatusListener listener) {
        this.requestWriting(() -> buffer, listener);
    }

    void close();

    void attach(Object attachment);
    Object attachment();
}

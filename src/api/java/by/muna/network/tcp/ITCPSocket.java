package by.muna.network.tcp;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public interface ITCPSocket {
    SocketAddress getAddress();

    void setListener(ITCPSocketListener listener);
    void requestWriting(Supplier<ByteBuffer> bufferProvider, ITCPSendStatusListener listener);

    void close();
}

package by.muna.network.tcp;

import java.net.SocketAddress;

public interface ITCPSocket {
    SocketAddress getAddress();

    void setListener(ITCPSocketListener listener);
    void requestWriting(IByteBufferProvider bufferProvider, ITCPSendStatusListener listener);

    void close();
}

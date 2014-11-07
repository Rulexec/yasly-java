package by.muna.network.tcp;

import by.muna.io.IAsyncByteInputStream;
import by.muna.io.IAsyncByteOutputStream;
import by.muna.monads.IAsyncFuture;

import java.net.SocketAddress;

public interface ITCPSocket {
    SocketAddress getRemoteAddress();
    SocketAddress getLocalAddress();

    IAsyncByteInputStream getInputStream();
    IAsyncByteOutputStream getOutputStream();

    /**
     * Returns null if successfully connected, or error, if not.
     * @return
     */
    IAsyncFuture<Object> onConnection();
    /**
     * Returns error. If null -- no error happened.
     * If error is happened at connection, then this will be called too with the same error.
     * @return
     */
    IAsyncFuture<Object> onShutdown();

    boolean isClosed();
}

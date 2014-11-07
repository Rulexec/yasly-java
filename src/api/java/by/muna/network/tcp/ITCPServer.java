package by.muna.network.tcp;

import by.muna.monads.IAsyncFuture;

import java.net.SocketAddress;
import java.util.function.Consumer;

public interface ITCPServer {
    SocketAddress getLocalAddress();

    void setConsumer(Consumer<ITCPSocket> listener);

    /**
     * @return null or error
     */
    IAsyncFuture<Object> stop();
}

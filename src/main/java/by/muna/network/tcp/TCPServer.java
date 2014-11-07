package by.muna.network.tcp;

import by.muna.monads.IAsyncFuture;
import by.muna.monads.OneTimeEventAsyncFuture;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.function.Consumer;

public class TCPServer implements ITCPServer {
    TCPSocketsThread mothership;

    public ServerSocketChannel serverChannel;
    private Consumer<ITCPSocket> listener;

    private OneTimeEventAsyncFuture<Object> shutdownEvent = new OneTimeEventAsyncFuture<>();

    public TCPServer(SocketAddress local) throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(local);
    }

    void _connected(TCPSocket socket) {
        this.listener.accept(socket);
    }
    void _shutdown(Object error) {
        this.shutdownEvent.event(error);
    }

    @Override
    public SocketAddress getLocalAddress() {
        try {
            return this.serverChannel.getLocalAddress();
        } catch (IOException ex) {
            throw new RuntimeException("We need to handle this", ex);
        }
    }

    @Override
    public void setConsumer(Consumer<ITCPSocket> listener) {
        this.listener = listener;
    }

    @Override
    public IAsyncFuture<Object> stop() {
        return callback -> {
            TCPServer.this.mothership.closeMe(TCPServer.this);
            TCPServer.this.shutdownEvent.run(callback);
        };
    }
}

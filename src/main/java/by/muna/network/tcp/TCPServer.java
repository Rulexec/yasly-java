package by.muna.network.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

public class TCPServer implements ITCPServer {
    public ServerSocketChannel serverChannel;
    private ITCPServerListener listener;

    public TCPServer(SocketAddress local) throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(local);
    }

    @Override
    public void setListener(ITCPServerListener listener) {
        this.listener = listener;
    }

    @Override
    public void stop() {
        throw new RuntimeException("Not implemented yet.");
    }

    void onConnected(TCPSocket socket) {
        this.listener.onConnected(socket);
    }
    void onStop() {
        throw new RuntimeException();
    }
}

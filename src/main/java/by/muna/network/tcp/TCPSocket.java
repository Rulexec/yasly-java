package by.muna.network.tcp;

import by.muna.buffers.IBufferReadable;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Supplier;

public class TCPSocket implements ITCPSocket {
    private static class WriteRequest {
        public Supplier<ByteBuffer> bufferProvider;
        public ITCPSendStatusListener listener;

        public WriteRequest(Supplier<ByteBuffer> bufferProvider, ITCPSendStatusListener listener) {
            this.bufferProvider = bufferProvider;
            this.listener = listener;
        }
    }

    // TODO: Назвать это как-то иначе
    public TCPSocketsThread mothership;

    public SocketChannel channel;
    private SocketAddress address;
    private ITCPSocketListener listener;

    private Queue<WriteRequest> writeRequests = new LinkedList<>();

    public boolean closed = false;
    private boolean onClosedCalled = false;

    TCPSocket(TCPSocketsThread mothership, SocketChannel channel) throws IOException {
        this.mothership = mothership;
        this.channel = channel;
        this.address = channel.getRemoteAddress();
    }
    public TCPSocket(SocketAddress remote) throws IOException {
        this.channel = SocketChannel.open();
        this.channel.configureBlocking(false);

        this.channel.connect(remote);
    }

    public boolean isWritingRequested() {
        return !this.writeRequests.isEmpty();
    }

    @Override
    public SocketAddress getAddress() {
        return this.address;
    }

    @Override
    public void setListener(ITCPSocketListener listener) {
        this.listener = listener;
    }

    @Override
    public void requestWriting(Supplier<ByteBuffer> bufferProvider, ITCPSendStatusListener listener) {
        if (this.closed) {
            listener.onFail();
            return;
        }

        this.writeRequests.add(new WriteRequest(bufferProvider, listener));
        this.mothership.requestWriting(this);
    }

    @Override
    public void close() {
        this.closed = true;
        this.mothership.closeMe(this);
    }

    /**
     * @return true, если больше нечего отправлять
     * @throws IOException
     */
    boolean send() throws IOException {
        if (this.closed) {
            this.failAllRequests();
            throw new SocketClosedException();
        }

        while (!this.writeRequests.isEmpty()) {
            //SocketSendData data = this.sendQueue.peek();
            WriteRequest writeRequest = this.writeRequests.peek();

            ByteBuffer buffer = writeRequest.bufferProvider.get();

            if (buffer == null) {
                // if IBufferProvider returns null, it means, that user don't want send packet.
                writeRequest.listener.onSent();

                this.writeRequests.poll();
                continue;
            }

            int writed;

            try {
                writed = this.channel.write(buffer);
            } catch (IOException ex) {
                this.failAllRequests();
                throw ex;
            }

            if (writed == -1) {
                this.failAllRequests();
                throw new SocketClosedException();
            }

            if (!buffer.hasRemaining()) {
                this.writeRequests.poll();

                writeRequest.listener.onSent();
            }

            if (writed == 0) break;
        }

        return this.writeRequests.isEmpty();
    }
    private void failAllRequests() {
        while (!this.writeRequests.isEmpty()) {
            WriteRequest writeRequest = this.writeRequests.poll();
            writeRequest.listener.onFail();
        }
    }

    void receive() throws IOException {
        if (this.closed) throw new SocketClosedException();

        class Container {
            public IOException exception = null;
            public boolean isClosed = false;
        }

        final Container container = new Container();

        this.listener.onData(new IBufferReadable() {
            @Override
            public int read(ByteBuffer buffer) {
                int readed;

                try {
                    readed = TCPSocket.this.channel.read(buffer);
                } catch (IOException ex) {
                    container.exception = ex;
                    return 0;
                }

                if (readed != -1) {
                    return readed;
                } else {
                    container.isClosed = true;
                    return 0;
                }
            }
        });

        if (container.exception != null) {
            throw container.exception;
        }

        if (container.isClosed) {
            throw new SocketClosedException();
        }
    }

    void onConnected() {
        this.listener.onConnected();
    }
    void onClosed() {
        if (this.onClosedCalled) return;
        this.closed = true;
        this.onClosedCalled = true;

        this.failAllRequests();
        this.listener.onClosed();
    }
}

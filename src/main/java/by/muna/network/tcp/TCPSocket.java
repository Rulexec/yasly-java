package by.muna.network.tcp;

import by.muna.io.IAsyncByteInputStream;
import by.muna.io.IAsyncByteOutputStream;
import by.muna.io.IByteReader;
import by.muna.io.IByteWriter;
import by.muna.monads.IAsyncFuture;
import by.muna.monads.OneTimeEventAsyncFuture;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.Function;

public class TCPSocket implements ITCPSocket {
    private class SocketInputStream implements IAsyncByteInputStream {
        private Consumer<IByteReader> consumer;

        @Override
        public void onData(Consumer<IByteReader> consumer) {
            this.consumer = consumer;
        }

        @Override
        public boolean isEnded() {
            return TCPSocket.this.closed;
        }

        void raiseEnd() {
            this.consumer.accept(new IByteReader() {
                @Override public int read(byte[] buffer, int offset, int length) {
                    return 0;
                }

                @Override public int read(ByteBuffer buffer) {
                    return 0;
                }

                @Override public boolean isEnd() {
                    return true;
                }
            });
        }

        void process() throws IOException {
            if (TCPSocket.this.closed) throw new SocketClosedException();

            class Container {
                IOException exception = null;
            }

            Container container = new Container();

            SocketChannel channel = TCPSocket.this.channel;

            this.consumer.accept(new IByteReader() {
                @Override
                public int read(ByteBuffer buffer) {
                    int totalReaded = 0;

                    while (buffer.hasRemaining()) {
                        int readed;

                        try {
                            readed = channel.read(buffer);
                        } catch (IOException ex) {
                            container.exception = ex;
                            TCPSocket.this.closed = true;
                            break;
                        }

                        if (readed == -1) {
                            TCPSocket.this.closed = true;
                            break;
                        }

                        if (readed == 0) break;

                        totalReaded += readed;
                    }

                    return totalReaded;
                }

                @Override
                public boolean isEnd() {
                    return TCPSocket.this.closed;
                }
            });

            if (container.exception != null) throw container.exception;

            if (TCPSocket.this.closed) throw new SocketClosedException();
        }
    }

    private class SocketOutputStream implements IAsyncByteOutputStream {
        boolean writingRequested = false;
        Function<IByteWriter, Boolean> writer = null;

        @Override
        public void requestWriting() {
            this.writingRequested = true;

            if (TCPSocket.this.mothership != null) {
                TCPSocket.this.mothership.requestWriting(TCPSocket.this);
            }
        }

        @Override
        public void onCanWrite(Function<IByteWriter, Boolean> writer) {
            this.writer = writer;
        }

        @Override
        public void end() {
            TCPSocket.this.closed = true;
            TCPSocket.this.mothership.closeMe(TCPSocket.this);
        }

        @Override
        public boolean isEnded() {
            return TCPSocket.this.closed;
        }

        boolean process() throws IOException {
            this.writingRequested = false;
            if (TCPSocket.this.closed) throw new SocketClosedException();

            class Container {
                IOException exception = null;
            }

            Container container = new Container();

            SocketChannel channel = TCPSocket.this.channel;

            boolean writingEnded = this.writer.apply(new IByteWriter() {
                @Override
                public int write(ByteBuffer buffer) {
                    if (TCPSocket.this.closed) return 0;

                    int totalWrited = 0;

                    while (buffer.hasRemaining()) {
                        int writed;
                        try {
                            writed = channel.write(buffer);
                        } catch (IOException ex) {
                            container.exception = ex;
                            TCPSocket.this.closed = true;
                            break;
                        }

                        if (writed == 0) break;

                        totalWrited += writed;
                    }

                    return totalWrited;
                }

                @Override
                public void end() {
                    TCPSocket.this.closed = true;
                }

                @Override
                public boolean isEnded() {
                    return TCPSocket.this.closed;
                }
            });

            if (container.exception != null) throw container.exception;

            if (TCPSocket.this.closed) {
                TCPSocket.this.mothership.closeMe(TCPSocket.this);
            }

            if (!writingEnded) {
                this.writingRequested = true;
            }

            return writingEnded;
        }
    }

    TCPSocketsThread mothership;

    SocketChannel channel;

    SocketInputStream inputStream = new SocketInputStream();
    SocketOutputStream outputStream = new SocketOutputStream();

    boolean closed = false;

    private OneTimeEventAsyncFuture<Object> connectionEvent = new OneTimeEventAsyncFuture<>();
    private OneTimeEventAsyncFuture<Object> shutdownEvent = new OneTimeEventAsyncFuture<>();

    TCPSocket(TCPSocketsThread mothership, SocketChannel channel) throws IOException {
        this.mothership = mothership;
        this.channel = channel;
    }
    public TCPSocket(SocketAddress remote) throws IOException {
        this.channel = SocketChannel.open();
        this.channel.configureBlocking(false);

        this.channel.connect(remote);
    }

    boolean isWritingRequested() {
        return this.outputStream.writingRequested;
    }

    void _connection(Object error) {
        this.connectionEvent.event(error);
    }
    void _shutdown(Object error) {
        if (!this.shutdownEvent.isEventHappened()) {
            this.inputStream.raiseEnd();
        }

        this.shutdownEvent.event(error);
    }

    /**
     * @return true, if all what needed is written
     * @throws IOException
     */
    boolean _send() throws IOException {
        return this.outputStream.process();
    }
    void _receive() throws IOException {
        this.inputStream.process();
    }

    @Override
    public IAsyncByteInputStream getInputStream() {
        return this.inputStream;
    }

    @Override
    public IAsyncByteOutputStream getOutputStream() {
        return this.outputStream;
    }

    @Override
    public IAsyncFuture<Object> onConnection() {
        return this.connectionEvent;
    }

    @Override
    public IAsyncFuture<Object> onShutdown() {
        return this.shutdownEvent;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        try {
            return this.channel.getRemoteAddress();
        } catch (IOException ex) {
            throw new RuntimeException("We should handle this", ex);
        }
    }

    @Override
    public SocketAddress getLocalAddress() {
        try {
            return this.channel.getLocalAddress();
        } catch (IOException ex) {
            throw new RuntimeException("We should handle this", ex);
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }
}

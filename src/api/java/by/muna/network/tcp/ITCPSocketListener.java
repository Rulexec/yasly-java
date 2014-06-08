package by.muna.network.tcp;

import by.muna.buffers.IBufferReadable;

public interface ITCPSocketListener {
    void onConnected();

    /**
     * Вызывается когда можно читать из сокета.
     * В ней следует прочитать всё, что есть,
     * пока IBufferReadable::read не станет возвращать 0,
     * иначе эта функция будет вызываться снова и снова,
     * до тех пор, пока всё не будет прочитано.
     * @param reader
     */
    void onData(IBufferReadable reader);

    void onClosed();
}

package by.muna.network.tcp;

import by.muna.callbacks.FinishListener;

import java.io.IOException;

/**
 * Класс, реализующий данный интерфейс должен предъявить требования на возможные реализации ITCPSocket'ов,
 * которые он принимает.
 *
 * К примеру, реализация данного класса может использовать NIO Selector'ы и принимать только SocketChannel'ы.
 */
public interface ITCPSockets {
    void register(ITCPServer server, FinishListener<IOException> listener);
    void register(ITCPSocket socket, FinishListener<IOException> listener);

    void unregister(ITCPServer server, FinishListener<IOException> listener);
    void unregister(ITCPSocket socket, FinishListener<IOException> listener);
}

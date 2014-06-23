package by.muna.network.tcp;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Класс, реализующий данный интерфейс должен предъявить требования на возможные реализации ITCPSocket'ов,
 * которые он принимает.
 *
 * К примеру, реализация данного класса может использовать NIO Selector'ы и принимать только SocketChannel'ы.
 */
public interface ITCPSockets {
    void register(ITCPServer server, Consumer<IOException> listener);
    void register(ITCPSocket socket, Consumer<IOException> listener);

    void unregister(ITCPServer server, Consumer<IOException> listener);
    void unregister(ITCPSocket socket, Consumer<IOException> listener);
}

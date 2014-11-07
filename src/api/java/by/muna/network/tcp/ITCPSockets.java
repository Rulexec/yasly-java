package by.muna.network.tcp;

import by.muna.monads.IAsyncFuture;

/**
 * Класс, реализующий данный интерфейс должен предъявить требования на возможные реализации ITCPSocket'ов,
 * которые он принимает.
 *
 * К примеру, реализация данного класса может использовать NIO Selector'ы и принимать только SocketChannel'ы.
 *
 * All of async futures can be runned only once. If runned more, than one time, undefined behaviour happens.
 */
public interface ITCPSockets {
    IAsyncFuture<Object> register(ITCPServer server);
    IAsyncFuture<Object> register(ITCPSocket socket);

    IAsyncFuture<Object> unregister(ITCPServer server);
    IAsyncFuture<Object> unregister(ITCPSocket socket);
}

package by.muna.monads;

import by.muna.data.IEither;
import by.muna.data.either.EitherLeft;
import by.muna.data.either.EitherRight;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

public class OneTimeEventAsyncFuture<T> implements IAsyncFuture<T> {
    private IEither<Queue<Consumer<T>>, T> listenersOrValue = new EitherLeft<>(new LinkedList<>());

    public OneTimeEventAsyncFuture() {}

    @Override
    public void run(Consumer<T> callback) {
        if (this.listenersOrValue.isRight()) {
            callback.accept(this.listenersOrValue.getRight());
        } else {
            synchronized (this) {
                if (this.listenersOrValue.isRight()) callback.accept(this.listenersOrValue.getRight());
                else this.listenersOrValue.getLeft().add(callback);
            }
        }
    }

    /**
     * Can be called only once, else noop.
     * @param value
     */
    public void event(T value) {
        Queue<Consumer<T>> listeners;

        synchronized (this) {
            if (this.listenersOrValue.isRight()) return;

            listeners = this.listenersOrValue.getLeft();
            this.listenersOrValue = new EitherRight<>(value);
        }

        for (Consumer<T> listener : listeners) listener.accept(value);
    }

    public boolean isEventHappened() {
        synchronized (this) {
            return this.listenersOrValue.isRight();
        }
    }
}

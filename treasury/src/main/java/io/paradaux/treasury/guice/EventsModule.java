package io.paradaux.treasury.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.paradaux.treasury.events.FirstPlayerJoinEvent;
import io.paradaux.treasury.events.PlayerLoginListener;
import org.bukkit.event.Listener;

/**
 * Wires Bukkit listeners into a {@code Multibinder<Listener>} so that
 * {@code Treasury.onEnable()} can iterate the set and register each one.
 *
 * <p>Adding a new listener: bind the class as a singleton and add a
 * {@code handlerBinder.addBinding().to(...)} line. No edit to {@code Treasury}
 * is required.
 */
public class EventsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FirstPlayerJoinEvent.class).in(Singleton.class);
        bind(PlayerLoginListener.class).in(Singleton.class);

        Multibinder<Listener> handlerBinder = Multibinder.newSetBinder(binder(), Listener.class);
        handlerBinder.addBinding().to(FirstPlayerJoinEvent.class);
        handlerBinder.addBinding().to(PlayerLoginListener.class);
    }
}

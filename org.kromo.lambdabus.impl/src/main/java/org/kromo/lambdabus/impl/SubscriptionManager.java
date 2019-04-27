/*
 * Copyright (c) 2019: Victor Toni
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied:
 *     GNU General Public License, version 2 with the GNU Classpath Exception
 * which is available at
 *     https://www.gnu.org/software/classpath/license.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 *
 * Contributors:
 *     Victor Toni - initial API and implementation
 */
package org.kromo.lambdabus.impl;

import java.util.Collection;
import java.util.function.Consumer;

import org.kromo.lambdabus.Subscription;

/**
 * Subscribing handlers as {@link Consumer}, searching for subscribed handlers,
 * and terminating subscriptions are functions independent of event handling
 * and are bundled in this independent interface.
 *
 * @author Victor Toni - initial API
 *
 */
public interface SubscriptionManager extends AutoCloseable {

    /**
     * Subscribe an event handler as {@link Consumer} for events of a given
     * class or interface.
     *
     * <p>
     * By convention the {@link Class} of an event is evaluated first for
     * subscriber lookup.<br>
     * If there are no matching subscriber found for the {@code Class} of an
     * event, the directly implemented interfaces of the event will be
     * evaluated in order of appearance.
     * </p>
     *
     * @param <T>
     *            the type of events subscription is for
     * @param eventClass
     *            non-{@code null} {@link Class} of events the subscription is for
     * @param eventHandler
     *            non-{@code null} {@link Consumer} which should get events of the given class
     * @return non-{@code null} {@link Subscription} which will remove the {@code eventHandler}
     *            from the subscriber list on closure
     * @throws NullPointerException
     *             if any of {@code eventClass} or {@code eventHandler} is {@code null}
     * @throws IllegalStateException
     *             if the {@code SubscriptionManager} has been closed already
     * @see #hasHandlerFor(Class)
     * @see #close()
     */
    <T> Subscription subscribe(final Class<T> eventClass, final Consumer<T> eventHandler);

    /**
     * Checks if there is any {@link Consumer} subscribed specifically for the
     * given type.
     *
     * <p>
     * The difference between this method and {@link #hasHandlerFor(Class)} is
     * that only the specific type is checked for and no inheritance based
     * checks are done.
     * </p>
     *
     * @param <T>
     *            the type of event
     * @param eventClass
     *            event {@link Class} to check for subscribed handler for
     * @return {@code true} if at least one subscribed handler was found for
     *            the specific class, {@code false} otherwise
     */
    <T> boolean hasHandlerForSpecificType(final Class<T> eventClass);

    /**
     * Checks if an event of the given type could be handled, either by its
     * class or eventually by one of its directly implemented interfaces.
     *
     * @param <T>
     *            the type of event
     * @param eventClass
     *            event {@link Class} to check subscriber for
     * @return {@code true} if any subscribed handler was found, either for the
     *            class or one of the directly implemented interfaces,
     *            {@code false} otherwise
     */
    <T> boolean hasHandlerFor(final Class<T> eventClass);

    /**
     * Returns a {@link Collection} of {@link Consumer} for a given {@link Class}.
     *
     * <p>
     * By convention the {@link Class} of an event is evaluated first for
     * handler lookup.<br>
     * If there are no matching handler for the {@code Class} of an event, the
     * directly implemented interfaces of the event will be evaluated in order
     * of appearance.<br>
     * The collection for the first matching interface will be returned if any
     * was found.
     * </p>
     *
     * @param <T>
     *            type of event
     * @param eventClass
     *            class to get {@link Collection} of {@link Consumer} for
     * @return found {@link Collection}, empty {@link Collection} otherwise,
     *            never {@code null}
     * @see #hasHandlerFor(Class)
     */
     <T> Collection<Consumer<T>> getHandlerFor(final Class<T> eventClass);

    /**
     * Closes the {@code SubscriptionManager} and closes all associated
     * {@link Subscription}s.
     *
     * <p>
     * A closed  {@code SubscriptionManager} effectively prevents any
     * {@link Subscription}s to be created.
     * </p>
     */
    @Override
    void close();

    /**
     * Gets the state of the {@code SubscriptionManager}.
     *
     * @return returns {@code true} if {@link #close()} has been called,
     *         {@code false} otherwise
     */
    boolean isClosed();

}

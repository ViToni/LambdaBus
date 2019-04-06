/*******************************************************************************
 * Copyright (c) 2018: Victor Toni
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
 *     Victor Toni - initial implementation
 *******************************************************************************/
package org.kromo.lambdabus.dispatcher.impl;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.EventDispatcher;

/**
 * Base class providing the core {@link EventDispatcher} functionality,
 * implementations can focus on the dispatching algorithm.
 * <p>
 * Implementations have to implement only the method:
 * <ul>
 * <li>{@link #dispatchEventToHandler(Object, Collection, ThreadingMode)}</li>
 * </ul>
 *
 * @author Victor Toni - initial implementation
 *
 */
public abstract class AbstractEventDispatcher
    implements EventDispatcher {

    /**
     * Flag indicating whether the bus is closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * {@link ThreadingMode} used by default.
     */
    private final ThreadingMode defaultThreadingMode;

    /**
     * {@link ThreadingMode}s supported by the event dispatcher.
     */
    private final Set<ThreadingMode> supportedThreadingModes;

    /**
     * Initialize the defaults.
     *
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default when
     *            posting to the bus (unsupported modes will be mapped to this one)
     * @throws NullPointerException
     *             if defaultThreadingMode is {@code null}
     */
    protected AbstractEventDispatcher(
            final ThreadingMode defaultThreadingMode
    ) {
        this(
                Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null"),
                EnumSet.of(defaultThreadingMode)
        );
    }

    /**
     * Initialize the defaults.
     *
     * @param defaultThreadingMode
     *            non-{@code null} {@link ThreadingMode} to be used as default when
     *            posting to the bus (unsupported modes will be mapped to this one)
     * @param supportedThreadingModes
     *            non-empty {@link Set} of supported {@link ThreadingMode}s
     * @throws NullPointerException
     *             if {@code defaultThreadingMode} or
     *             {@code supportedThreadingModes} is {@code null} or if any of the
     *             elements of {@code supportedThreadingModes} is {@code null}
     * @throws IllegalArgumentException
     *             if {@code supportedThreadingModes} is empty or the
     *             {@code defaultThreadingMode} is not contained within
     *             {@code supportedThreadingModes}
     */
    protected AbstractEventDispatcher(
            final ThreadingMode defaultThreadingMode,
            final Set<ThreadingMode> supportedThreadingModes
    ) {
        Objects.requireNonNull(defaultThreadingMode, "'defaultThreadingMode' must not be null");
        Objects.requireNonNull(supportedThreadingModes, "'supportedThreadingModes' must not be null");
        if (supportedThreadingModes.isEmpty()) {
            throw new IllegalArgumentException("'supportedThreadingModes' must not be empty");
        }
        final Set<ThreadingMode> copyOfSupportedThreadingModes = new HashSet<>(supportedThreadingModes);
        for (final ThreadingMode threadingMode : copyOfSupportedThreadingModes) {
            if (null == threadingMode) {
                throw new NullPointerException("'supportedThreadingModes' contains NULL ThreadingMode");
            }
        }

        if (!copyOfSupportedThreadingModes.contains(defaultThreadingMode)) {
            throw new IllegalArgumentException("Default ThreadingMode " + defaultThreadingMode
                    + " not contained within supported ThreadingModes: " + copyOfSupportedThreadingModes);
        }

        this.supportedThreadingModes = EnumSet.copyOf(copyOfSupportedThreadingModes);

        this.defaultThreadingMode = defaultThreadingMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void close() {
        if(closed.compareAndSet(false, true)) {
            cleanupBeforeClose();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Either dispatches the given event directly (in case of
     * {@link ThreadingMode#SYNC}) or adds the event, its subscribed
     * {@link Consumer}s and the {@link ThreadingMode} to the internal queue for
     * further processing.
     *
     * <p>
     * All parameters are non-{@code null} because the calling method has
     * checked them already.
     * </p>
     *
     * @param <T>
     *            type of posted event
     * @param event
     *            non-{@code null} object to be dispatched
     * @param eventHandlerCollection
     *            non-{@code null} {@link Collection} of non-{@code null}
     *            {@link Consumer}s registered for the {@link Class} of the
     *            event
     * @param supportedThreadingMode
     *            non-{@code null} {@link ThreadingMode} how the event should be
     *            dispatched
     */
    @Override
    public final <T> void dispatchEventToHandler(
            final T event,
            final Collection<Consumer<T>> eventHandlerCollection,
            final ThreadingMode supportedThreadingMode
    ) {
        if (isClosed()) {
            throw new IllegalStateException(getClass().getSimpleName() + " is closed. Event not dispatched: " + event);
        }

        internalDispatchEventToHandler( //
                event, //
                eventHandlerCollection, //
                supportedThreadingMode);
    }

    /**
     * Gets the default {@link ThreadingMode} of the event dispatcher.
     *
     * @return {@link ThreadingMode} used as default
     */
    @Override
    public final ThreadingMode getDefaultThreadingMode() {
        return defaultThreadingMode;
    }

    /**
     * Gets the supported {@link ThreadingMode}s of the event dispatcher.
     *
     * @return {@link Set} of supported {@link ThreadingMode}s
     */
    @Override
    public final Set<ThreadingMode> getSupportedThreadingModes() {
        return this.supportedThreadingModes;
    }

    //##########################################################################
    // Methods required to be implemented by sub-classes
    //##########################################################################

    protected abstract <T> void internalDispatchEventToHandler(
            final T event,
            final Collection<Consumer<T>> eventHandlerCollection,
            final ThreadingMode supportedThreadingMode
    );

    //##########################################################################
    // Methods which can be overridden to customize behavior
    //##########################################################################

    /**
     * Cleanup before closing the bus.
     */
    protected void cleanupBeforeClose() {
    }

}

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
 *     Victor Toni - initial implementation
 */
package org.kromo.lambdabus.dispatcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.impl.AbstractEventDispatcher;

/**
 * Defines tests for behavioral aspects expected from implementations of the
 * {@link EventDispatcher} interface.
 *
 * @param <EventDispatcherType>
 *            type to test which implements the {@link EventDispatcher}
 *            interface
 *
 * @author Victor Toni - initial implementation
 *
 */
public abstract class EventDispatcherContract<EventDispatcherType extends AbstractEventDispatcher> {

    protected abstract EventDispatcherType createEventDispatcher();

    @Test
    @DisplayName("Created EventDispatcher is not null")
    public void created_EventDispatcher_is_not_null() {
        try (final EventDispatcher eventDispatcher = createEventDispatcher()) {
            assertNotNull(eventDispatcher, "Created 'EventDispatcher' is null");
        }
    }

    @Test
    @DisplayName("Created EventDispatcher can be closed")
    public void created_EventDispatcher_can_be_closed() {
        final EventDispatcher eventDispatcher = createEventDispatcher();
        try {
            final String eventDispatcherName = getClass().getSimpleName();
            assertFalse(
                    eventDispatcher.isClosed(),
                    eventDispatcherName + " is closed");

            eventDispatcher.dispatchEventToHandler("event",
                    Collections.singletonList(System.out::println), ThreadingMode.SYNC);

            eventDispatcher.close();
            assertTrue(
                    eventDispatcher.isClosed(),
                    eventDispatcherName + " is not closed after close()");

            assertThrows(
                    IllegalStateException.class,
                    () -> eventDispatcher.dispatchEventToHandler("event",
                            Collections.singletonList(System.out::println), ThreadingMode.SYNC));
        } finally {
            eventDispatcher.close();
        }
    }

    @Test
    @DisplayName("defaultThreadingMode is contained within supportedThreadingModes")
    public void defaultThreadingMode_is_contained_within_supportedThreadingModes() {
        try (final EventDispatcher eventDispatcher = createEventDispatcher()) {
            final String eventDispatcherName = getClass().getSimpleName();

            assertNotNull(
                    eventDispatcher.getDefaultThreadingMode(),
                    "'" + eventDispatcherName + ".getDefaultThreadingMode()' must not return null");

            assertNotNull(
                    eventDispatcher.getSupportedThreadingModes(),
                    "'" + eventDispatcherName
                            + ".getSupportedThreadingModes()' must not return null");

            assertFalse(
                    eventDispatcher.getSupportedThreadingModes()
                            .isEmpty(),
                    "'" + eventDispatcherName
                            + ".getSupportedThreadingModes()' must not return empty Set");

            for (final ThreadingMode supportedThreadingMode : eventDispatcher
                    .getSupportedThreadingModes()) {
                assertNotNull(
                        supportedThreadingMode,
                        "'" + eventDispatcherName
                                + ".getDefaultThreadingMode()' must not contain null");
            }

            final ThreadingMode defaultThreadingMode = eventDispatcher.getDefaultThreadingMode();
            final Set<ThreadingMode> supportedThreadingModes = eventDispatcher
                    .getSupportedThreadingModes();

            assertTrue(supportedThreadingModes.contains(defaultThreadingMode),
                    "'supportedThreadingModes' does not contain 'defaultThreadingMode'");
        }
    }

}

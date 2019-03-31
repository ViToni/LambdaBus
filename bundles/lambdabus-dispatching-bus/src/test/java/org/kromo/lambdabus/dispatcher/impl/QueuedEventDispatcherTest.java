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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.dispatcher.EventDispatcherContract;
import org.kromo.lambdabus.queue.EventQueue;
import org.kromo.lambdabus.queue.impl.SharableEventQueue;

/**
 * Testing {@link QueuedEventDispatcher} reusing tests from
 * {@link EventDispatcherContract}. Custom test are added for
 * {@link QueuedEventDispatcher} constructors.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class QueuedEventDispatcherTest
    extends EventDispatcherContract<QueuedEventDispatcher> {

    @Override
    protected QueuedEventDispatcher createEventDispatcher() {
        return new QueuedEventDispatcher();
    }

    @Test
    @DisplayName("Constructor - default constructor")
    public void defaultConstructor() {
        try (final EventDispatcher ed = new QueuedEventDispatcher()) {
            assertFalse(ed.isClosed(), "Created 'QueuedEventDispatcher' must not be closed.");
        }
    }

    @Test
    @DisplayName("Constructor - (EventQueue)")
    public void constructorWithExecutorService() {
        final EventQueue eventQueue = new SharableEventQueue();
        try (final EventDispatcher eventDispatcher = new QueuedEventDispatcher(eventQueue)) {
            assertFalse(eventDispatcher.isClosed(), "Created 'QueuedEventDispatcher' must not be closed.");
            assertFalse(eventQueue.isClosed(), "External 'EventQueue' must not be closed");
        } finally {
            eventQueue.close();
        }
        assertTrue(eventQueue.isClosed(), "External 'EventQueue' must be closed");
    }

    @Test
    @DisplayName("Constructor - null EventQueue throws NullPointerException")
    public void constructorNullExecutorServiceThrowsNPE() {
        final EventQueue nullEventQueue = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final EventDispatcher eventDispatcher = new QueuedEventDispatcher(nullEventQueue)) {}
                }
        );
    }

    @DisplayName("Constructor - default ThreadingMode with null ExecutorService throws NullPointerException")
    @ParameterizedTest(name = "Constructor - default ThreadingMode.{0} as default and null ExecutorService throws NullPointerException")
    @EnumSource(ThreadingMode.class)
    public void constructorNullExecutorServiceThrowsNPE(
            final ThreadingMode defaultThreadingMode
    ) {
        final EventQueue nullEventQueue = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final EventDispatcher eventDispatcher = new QueuedEventDispatcher(defaultThreadingMode, nullEventQueue)) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor - null ThreadingMode throws NullPointerException")
    public void constructorNullThreadingModeThrowsNPE() {
        final ThreadingMode nullDefaultThreadingMode = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final EventDispatcher eventDispatcher = new QueuedEventDispatcher(nullDefaultThreadingMode)) {}
                }
        );

        final EventQueue eventQueue = new SharableEventQueue();
        try {
            assertThrows(
                    NullPointerException.class,
                    () -> {
                        try (final EventDispatcher eventDispatcher = new QueuedEventDispatcher(nullDefaultThreadingMode, eventQueue)) {}
                    }
            );
        } finally {
            eventQueue.close();
        }
    }

}

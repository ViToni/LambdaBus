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
    public void constructorWithNullEventDispatcher() {
        final EventDispatcher eventDispatcher = new SynchronousEventDispatcher();
        try (final EventDispatcher ed = new QueuedEventDispatcher(eventDispatcher)) {
            assertFalse(ed.isClosed(), "Created 'QueuedEventDispatcher' must not be closed.");
            assertFalse(eventDispatcher.isClosed(), "External 'eventDispatcher' must not be closed");
        } finally {
            eventDispatcher.close();
        }
        assertTrue(eventDispatcher.isClosed(), "External 'EventQueue' must be closed");
    }

    @Test
    @DisplayName("Constructor - null EventDispatcher throws NullPointerException")
    public void constructorNullExecutorServiceThrowsNPE() {
        final EventDispatcher nullEventDispatcher = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final EventDispatcher ed = new QueuedEventDispatcher(nullEventDispatcher)) {}
                }
        );
    }

    @DisplayName("Constructor - default ThreadingMode with null EventDispatcher throws NullPointerException")
    @ParameterizedTest(name = "Constructor - default ThreadingMode.{0} as default and null EventDispatcher throws NullPointerException")
    @EnumSource(ThreadingMode.class)
    public void constructorNullExecutorServiceThrowsNPE(
            final ThreadingMode defaultThreadingMode
    ) {
        final EventDispatcher nullEventDispatcher = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final EventDispatcher ed = new QueuedEventDispatcher(defaultThreadingMode, nullEventDispatcher)) {}
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
                    try (final EventDispatcher ed = new QueuedEventDispatcher(nullDefaultThreadingMode)) {}
                }
        );

        final EventDispatcher eventQueue = new SynchronousEventDispatcher();
        try {
            assertThrows(
                    NullPointerException.class,
                    () -> {
                        try (final EventDispatcher ed = new QueuedEventDispatcher(nullDefaultThreadingMode, eventQueue)) {}
                    }
            );
        } finally {
            eventQueue.close();
        }
    }

}

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.dispatcher.EventDispatcherContract;

/**
 * Tests for the {@link AbstractEventDispatcher}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class AbstractEventDispatcherTest extends
    EventDispatcherContract<AbstractEventDispatcher> {

    protected static final int ONE = 1;
    protected static final int TWO = 2;

    /**
     * Timeout used to wait for published events to complete
     */
    protected static final int DEFAULT_TIMEOUT_MILLIS = 500;

    @Override
    protected AbstractEventDispatcher createEventDispatcher() {
        return new TestingAbstractEventDispatcher(ThreadingMode.SYNC);
    }

    @ParameterizedTest(name = "Constructor with default ThreadingMode.{0}")
    @EnumSource(value=ThreadingMode.class)
    @DisplayName("Constructor with default ThreadingMode")
    public void constructor_with_defaultThreadingMode(
            final ThreadingMode threadingMode
    ) {
            try (final EventDispatcher ed = new TestingAbstractEventDispatcher(threadingMode)) {}
    }

    @ParameterizedTest(name = "Constructor with default ThreadingMode.{0} and supported EnumSet.of(ThreadingMode.{0})")
    @EnumSource(value=ThreadingMode.class)
    @DisplayName("Constructor with default ThreadingMode and supported ThreadingModes")
    public void constructor_with_defaultThreadingMode_and_supportedThreadingModes(
            final ThreadingMode threadingMode
    ) {
        final Set<ThreadingMode> supportedThreadingModes = EnumSet.of(threadingMode);
        try (final EventDispatcher ed = new TestingAbstractEventDispatcher(threadingMode, supportedThreadingModes)) {}
    }

    @Test
    @DisplayName("Constructor - Setting null default ThreadingMode throws NullPointerException")
    public void constructor_null_defaultThreadingMode_throws_NPE() {
        assertThrows(
            NullPointerException.class,
            () -> new TestingAbstractEventDispatcher(ThreadingMode.SYNC, null));
    }

    @Test
    @DisplayName("Constructor - Setting null supported ThreadingModes throws NullPointerException")
    public void constructor_null_supportedThreadingModes_throws_NPE() {
        assertThrows(
            NullPointerException.class,
            () -> new TestingAbstractEventDispatcher(ThreadingMode.SYNC, null));
    }

    @ParameterizedTest(name = "Constructor - Setting unsupported default ThreadingMode.{0} throws IllegalArgumentException")
    @EnumSource(value=ThreadingMode.class)
    @DisplayName("Constructor - Setting unsupported default ThreadingMode throws IllegalArgumentException")
    public void constructor_unsupported_ThreadingMode_throws_exception(
            final ThreadingMode threadingMode
    ) {
        final Set<ThreadingMode> allOtherThreadingModes = EnumSet.allOf(ThreadingMode.class);
        allOtherThreadingModes.remove(threadingMode);
        assertThrows(
            IllegalArgumentException.class,
            () -> new TestingAbstractEventDispatcher(threadingMode, allOtherThreadingModes));
    }

    @ParameterizedTest(name = "Constructor - {0} - Setting null supportedThreadingModes throws NullPointerException")
    @EnumSource(value=ThreadingMode.class)
    @DisplayName("Constructor - Setting null supportedThreadingModes throws NullPointerException")
    public void constructor_null_supportedThreadingModes_throws_NPE(
            final ThreadingMode threadingMode
    ) {
        assertThrows(
            NullPointerException.class,
            () -> new TestingAbstractEventDispatcher(threadingMode, null));
    }

    @ParameterizedTest(name = "Constructor - {0} - Setting empty supportedThreadingModes throws IllegalArgumentException")
    @EnumSource(value=ThreadingMode.class)
    @DisplayName("Constructor - Setting empty supportedThreadingModes throws IllegalArgumentException")
    public void constructor_null_supportedThreadingModes_throws_exception(
            final ThreadingMode threadingMode
    ) {
        assertThrows(
            IllegalArgumentException.class,
            () -> new TestingAbstractEventDispatcher(threadingMode, EnumSet.noneOf(ThreadingMode.class)));
    }

    @ParameterizedTest(name = "Constructor - {0} - Setting supportedThreadingModes containing null ThreadingMode throws NullPointerException")
    @EnumSource(value=ThreadingMode.class)
    @DisplayName("Constructor - Setting supportedThreadingModes containing null ThreadingMode throws NullPointerException")
    public void constructor_supportedThreadingModes_with_null_ThreadingMode_throws_NPE(
            final ThreadingMode threadingMode
    ) {
        final Set<ThreadingMode> allThreadingModesAndNull = new HashSet<>(EnumSet.allOf(ThreadingMode.class));
        allThreadingModesAndNull.add(null);
        assertThrows(
            NullPointerException.class,
            () -> new TestingAbstractEventDispatcher(threadingMode, allThreadingModesAndNull));
    }

    //##########################################################################
    // Helper class to instantiate the abstract class to be tested
    //##########################################################################

    private final static class TestingAbstractEventDispatcher
        extends AbstractEventDispatcher {

        TestingAbstractEventDispatcher(
                final ThreadingMode defaultThreadingMode
        ) {
            super(defaultThreadingMode);
        }

        TestingAbstractEventDispatcher(
                final ThreadingMode defaultThreadingMode,
                final Set<ThreadingMode> supportedThreadingModes
        ) {
            super(defaultThreadingMode, supportedThreadingModes);
        }

        @Override
        protected <T> void internalDispatchEventToSubscriber(
                final T event,
                final Collection<Consumer<T>> eventSubscriberCollection,
                final ThreadingMode supportedThreadingMode
        ) {
            assertNotNull(event);
            assertNotNull(eventSubscriberCollection);
            eventSubscriberCollection.stream().forEach(Assertions::assertNotNull);
            assertNotNull(supportedThreadingMode);

            eventSubscriberCollection.stream().forEach(consumer -> consumer.accept(event));
        }
    }

}

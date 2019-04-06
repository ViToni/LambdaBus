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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.dispatcher.EventDispatcherContract;

/**
 * Tests for the {@link ThreadedEventDispatcher}.
 *
 * @author Victor Toni - initial implementation
 *
 */
public class ThreadedEventDispatcherTest
    extends EventDispatcherContract<ThreadedEventDispatcher> {

    @Override
    protected ThreadedEventDispatcher createEventDispatcher() {
        return new ThreadedEventDispatcher();
    }

    @Test
    @DisplayName("Constructor")
    public void constructor() {
        try (final EventDispatcher ed = new ThreadedEventDispatcher()) {
            assertFalse(ed.isClosed(), "Created 'ThreadedEventDispatcher' must not be closed.");
        }
    }

    @Test
    @DisplayName("Constructor with ExecutorService")
    public void constructor_with_ExecutorService() {
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (final EventDispatcher ed = new ThreadedEventDispatcher(executorService)) {
            assertFalse(ed.isClosed(), "Created 'ThreadedEventDispatcher' must not be closed.");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Constructor with null ExecutorService throws NullPointerException")
    public void constructor_null_ExecutorService_throws_NPE() {
        final ExecutorService nullExecutorService = null;
        assertThrows(
                NullPointerException.class,
                () -> new ThreadedEventDispatcher(nullExecutorService)
        );
    }

}

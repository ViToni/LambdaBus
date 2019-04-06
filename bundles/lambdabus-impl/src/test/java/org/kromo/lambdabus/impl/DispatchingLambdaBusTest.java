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
package org.kromo.lambdabus.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.kromo.lambdabus.ThreadingMode;
import org.kromo.lambdabus.dispatcher.EventDispatcher;

/**
 * Test for the {@link DispatchingLambdaBus}
 *
 * @author Victor Toni - initial implementation
 *
 */
public class DispatchingLambdaBusTest {

    @Test
    @DisplayName("Constructor with null EventDispatcher throws NullPointerException")
    public void constructor_with_null_EventDispatcher_throws_NPE() {
        final EventDispatcher nullEventDispatcher = null;
        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(
                            nullEventDispatcher)) {}
                }
        );
    }

    @Test
    @DisplayName("Default Constructor")
    public void defaultConstructor() {
        final EventDispatcher eventDispatcher = Mockito.mock(EventDispatcher.class);
        Mockito //
            .doReturn(ThreadingMode.SYNC) //
            .when(eventDispatcher).getDefaultThreadingMode();
        Mockito //
            .doReturn(EnumSet.of(ThreadingMode.SYNC)) //
            .when(eventDispatcher).getSupportedThreadingModes();

        assertDoesNotThrow(
                () -> {
                    try (final DispatchingLambdaBus lb = new DispatchingLambdaBus(
                            eventDispatcher)) {}
                }
        );
    }

}

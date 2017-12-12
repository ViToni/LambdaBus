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
package org.kromo.lambdabus.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Some basic tests for {@link NullEventPublisherLogger}.
 * 
 * @author Victor Toni - initial implementation
 *
 */
public class NullEventPublisherLoggerTest {
    
    /*
     * Number used to test removal of stack-trace elements.
     */
    private static final int SOME_INVALID_NUMBER = -5;
    private static final int LOWER_VALID_NUMBER_BOUND = 0;
    private static final int SOME_VALID_NUMBER = 5;

    @Test
    public void constructor() {
        new NullEventPublisherLogger();
    }

    @Test
    public void constructorWithValidParam() {
        for (int i = LOWER_VALID_NUMBER_BOUND; i < SOME_VALID_NUMBER; i++) {
            new NullEventPublisherLogger(i);
        }
    }

    @Test
    public void constructorWithWrongParam() {
        for (int i = SOME_INVALID_NUMBER; i < LOWER_VALID_NUMBER_BOUND; i++) {
            final int param = i;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new NullEventPublisherLogger(param)
            );
        }
    }

}

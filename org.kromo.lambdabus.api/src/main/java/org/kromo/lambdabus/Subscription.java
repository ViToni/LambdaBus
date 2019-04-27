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
 *     Victor Toni - initial API
 */
package org.kromo.lambdabus;

/**
 * The {@link Subscription} interface is used to remove the {@link Exception}
 * from the {@link AutoCloseable#close()} method.<br>
 * The {@link #close()} method does not do any IO operations and is used as a
 * simple way to unsubscribe from the {@link LambdaBus}.
 *
 * @author Victor Toni - initial API
 *
 */
public interface Subscription
    extends AutoCloseable {

    /**
     * Returns the class the subscription has been created for.
     *
     * @return {@link Class} of events the subscriptions has been created for
     */
    Class<?> forClass();

    /**
     * Returns whether the subscription has been closed already.
     *
     * @return {@code true } if closed has been called already, {@code false}
     *         otherwise
     */
    boolean isClosed();

    /**
     * Removes the subscription from the associated event bus.
     * <p>
     * This method is expected to be idempotent.
     * </p>
     */
    @Override
    void close();
}

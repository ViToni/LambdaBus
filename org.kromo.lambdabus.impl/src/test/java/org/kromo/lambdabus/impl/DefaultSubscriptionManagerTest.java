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
package org.kromo.lambdabus.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test for the {@link DefaultSubscriptionManager}
 *
 * @author Victor Toni - initial implementation
 *
 */
public class DefaultSubscriptionManagerTest
    extends SubscriptionManagerContract<SubscriptionManager> {

    @Test
    @DisplayName("Default Constructor")
    public void defaultConstructor() {
        assertDoesNotThrow(
                () -> {
                    try (final SubscriptionManager subscriptionManager = new DefaultSubscriptionManager()) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor with Supplier")
    public void constructorUsingSupplier() {
        final Supplier<Map<Class<?>, Collection<Consumer<?>>>> subscriberCollectionMapSupplier = ConcurrentHashMap::new;
        final Supplier<Collection<Consumer<?>>> subscriberCollectionSupplier = CopyOnWriteArrayList::new;

        assertDoesNotThrow(
                () -> {
                    try (final SubscriptionManager subscriptionManager = new DefaultSubscriptionManager(
                            subscriberCollectionMapSupplier,
                            subscriberCollectionSupplier
                            )) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor with null SubscriberCollectionMapSupplier")
    public void constructorUsingNullSubscriberCollectionMapSupplier() {
        final Supplier<Map<Class<?>, Collection<Consumer<?>>>> nullSubscriberCollectionMapSupplier = null;
        final Supplier<Collection<Consumer<?>>> subscriberCollectionSupplier = CopyOnWriteArrayList::new;

        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final SubscriptionManager subscriptionManager = new DefaultSubscriptionManager(
                            nullSubscriberCollectionMapSupplier,
                            subscriberCollectionSupplier
                            )) {}
                }
        );
    }

    @Test
    @DisplayName("Constructor with null SubscriberCollectionSupplier")
    public void constructorUsingNullSubscriberCollectionSupplier() {
        final Supplier<Map<Class<?>, Collection<Consumer<?>>>> subscriberCollectionMapSupplier = ConcurrentHashMap::new;
        final Supplier<Collection<Consumer<?>>> nullSubscriberCollectionSupplier = null;

        assertThrows(
                NullPointerException.class,
                () -> {
                    try (final SubscriptionManager subscriptionManager = new DefaultSubscriptionManager(
                            subscriberCollectionMapSupplier,
                            nullSubscriberCollectionSupplier
                            )) {}
                }
        );
    }

    @Override
    protected SubscriptionManager createSubscriptionManager() {
        return new DefaultSubscriptionManager();
    }


}

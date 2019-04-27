package org.kromo.lambdabus.examples;

import java.util.function.Consumer;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.dispatcher.EventDispatcher;
import org.kromo.lambdabus.dispatcher.impl.SynchronousEventDispatcher;
import org.kromo.lambdabus.impl.DispatchingLambdaBus;

public class HelloLambdaWorld {

    public static void main(String[] args) {
        // SynchronousEventDispatcher is the simplest EventDispatcher
        final EventDispatcher eventDispatcher = new SynchronousEventDispatcher();
        final LambdaBus lb = new DispatchingLambdaBus(eventDispatcher);

        /*
         * For every "String" event published to the bus call System.out.println() with
         * the String as parameter.
         */
        lb.subscribe(String.class, System.out::println);

        final Consumer<Object> postRef = lb::post;

        // publish a "String" event via regular method call
        lb.post("Hello Old World.");

        /*
         * Publishes a "String" event via the method reference of the event bus. The use
         * of method references avoids the need to "know" the event bus.
         */
        postRef.accept("Hello Lambda World.");

        lb.close();
    }
}

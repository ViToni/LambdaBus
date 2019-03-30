package org.kromo.lambdabus.examples;

import java.util.function.Consumer;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.impl.SynchronousLambdaBus;

public class HelloLambdaWorld {

    public static void main(String[] args) {
        // SyncLambdaBus is the simplest LambdaBus
        final LambdaBus lb = new SynchronousLambdaBus();

        /*
         * For every "String" event published to the bus
         * call System.out.println() with the String as
         * parameter.
         */
        lb.subscribe(String.class, System.out::println);

        final Consumer<Object> postRef = lb::post;

        // publish a "String" event via regular method call
        lb.post("Hello Old World.");

        /*
         * Publish a "String" event via the method reference
         * of the event bus. Using method references avoids
         * needing to "know" about the event bus.
         */
        postRef.accept("Hello Lambda World.");

        lb.close();
    }
}

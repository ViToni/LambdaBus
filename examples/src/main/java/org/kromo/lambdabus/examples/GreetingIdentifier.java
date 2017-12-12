package org.kromo.lambdabus.examples;

import java.util.function.Supplier;

import org.kromo.lambdabus.LambdaBus;
import org.kromo.lambdabus.impl.SynchronousLambdaBus;

public class GreetingIdentifier {

    public static void main(String[] args) {
         testLambdaBus(SynchronousLambdaBus::new);
    }

    public static void testLambdaBus(
            final Supplier<LambdaBus> lbSupplier
    ) {
        try (final LambdaBus lb = lbSupplier.get()) {
            System.out.println("--------------------------------");
            System.out.println("LambdaBus implementation: " + lb);
            System.out.println("----");

            lb.subscribe(String.class, System.out::println);
            lb.subscribe(String.class, GreetingIdentifier::identifyGreeting);

            lb.post("Say something");

            lb.post("Hello World.");
        }
    }

    private static void identifyGreeting(final String event) {
        if (event.startsWith("Hello")) {
            System.out.println(event + " => Received greeting.");
        } else {
            System.out.println(event + " => Not a greeting.");
        }
    }
}

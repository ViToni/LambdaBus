package org.kromo.lambdabus.examples;
import java.util.function.Consumer;

public class LambdasAreNoObjects {
    private static final Consumer<String> CONSUMER = System.out::println;

    public static void main(String[] args) {
        printEquals(System.out::println, System.out::println);
        printEquals(CONSUMER, CONSUMER);

        System.out.println("\nHashes of method reference for 'System.out::println':");
        for (int i = 0; i < 5; i++) {
            printHash(System.out::println);
        }

        System.out.println("\nHashes of saved method reference to 'System.out::println':");
        for (int i = 0; i < 5; i++) {
            printHash(CONSUMER);
        }
    }

    private static <T> void printEquals(final Consumer<T> a, final Consumer<T> b) {
        System.out.println("Comparing lambdas:");
        System.out.println("(a == b)    = " + (a == b));
        System.out.println("a.equals(b) = " + a.equals(b));
        System.out.println(a.getClass().getCanonicalName());
        System.out.println(b.getClass().getCanonicalName());
        System.out.println("------------------------------");
    }

    private static <T> void printHash(final Consumer<T> c) {
        System.out.println(c + " = " + c.hashCode());
    }

}
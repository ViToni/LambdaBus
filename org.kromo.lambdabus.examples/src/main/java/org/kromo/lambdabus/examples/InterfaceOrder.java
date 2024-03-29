package org.kromo.lambdabus.examples;

public class InterfaceOrder {
    private interface A {
    }

    private interface B {
    }

    private interface C {
    }

    private static class SuperImpl
            implements A, B, C {
    }

    private interface M {
    }

    private interface N {
    }

    private interface O {
    }

    private static class SomeImpl
            extends SuperImpl
            implements M, N, O {
    }

    public static void main(String[] args) {
        final Class<SomeImpl> clazz = SomeImpl.class;

        printClassInformation(clazz, "");
    }

    private static void printClassInformation(final Class<?> clazz, final String prefix) {
        if (prefix.isEmpty()) {
            System.out.println("Class '" + clazz + "'");
        } else {
            System.out.println("\n" + prefix + "Super-Class '" + clazz + "'");
        }
        final Class<?>[] interfazzes = clazz.getInterfaces();
        if (0 < interfazzes.length) {
            System.out.println(prefix + "Direct Interfaces of '" + clazz + "':");
            for (final Class<?> interfazz : interfazzes) {
                System.out.println(prefix + "\tInterface: " + interfazz.getName());
            }
        }
        final Class<?> superClass = clazz.getSuperclass();
        if (null != superClass) {
            printClassInformation(superClass, prefix + "\t");
        }
    }

}
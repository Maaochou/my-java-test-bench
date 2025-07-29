package maaochou.virtualthreads.java21.second;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;

// Virtual threads were added to the to JAVA 21 as the first LTS version to have it
// initially available since java 19
public class VirtualThreads {

    static void log(String message) {
        System.out.println(message + " " + Thread.currentThread());
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // concurrent code on top of OS or JVM threads (Kotlin coroutines ...)
    // coroutines green threads scheduled on a collaborative thread pool

    // JVM gives us an abstraction on OS threads with the class/data structure 'Thread'
    // Before project loom every thread on the JVM is just a wrapper on an OS thread
    // OS/JVM thread = platform thread

    // platform thread are very expensive,
    // they occupy a lot of memory and take a lot of time to create or destroy

    /**
     * Exception in thread "main" java.lang.OutOfMemoryError:
     * unable to create native thread: possibly out of memory or process/resource limits reached
     */
    public static void createPlatformThreads() {
        IntStream.range(0, 100000).forEach(i -> {
            new Thread(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(1L));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }).start();
        });

    }

    // virtual thread are a data structure which the jvm would use to
    // schedule lightweight threads on top o few platform threads

    // virtual threads try to overcome the ressource limitation problem of platform threads

    // the stack of a virtual thread is stored on the heap and not on the jvm stack, this makes sure that
    // the virtual thread is treated as a data structure passed around between a number of platform threads

    private static Thread virtualThread(String name, Runnable runnable) {
        return Thread.ofVirtual().name(name).start(runnable);
    }

    // simulate a morning routine, boil some water while taking a shower
    static Thread bathTime() {
        return virtualThread("Bath time", () -> {
            log("-Going for a bath");
            // Block platform thread if not in the virtual env
            // If it's a virtual thread, it gets descheduled from platform thread (also called the carrier thread in this context)
            // It gets rescheduled once the sleep is done.
            sleep(Duration.ofMillis(500L));
            log("-I'm done with the bath");
        });
    }

    static Thread boilWater() {
        return virtualThread("Boil water", () -> {
            log("-Going to boil water");
            // Block platform thread if not in the virtual env
            // If it's a virtual thread, it gets descheduled from platform thread (also called the carrier thread in this context)
            // It gets rescheduled once the sleep is done.
            sleep(Duration.ofMillis(1000L));
            log("-I'm done with water boiling");
        });
    }

    static void concurrentMorningRoutine() {
        var bathTime = bathTime();
        var boilWater = boilWater();
        try {
            // join waits for the thread to finish
            bathTime.join();
            boilWater.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // executors on virtual threads

    static void concurrentMorningRoutineWithExecutors() {
        final ThreadFactory factory = Thread.ofVirtual().name("routine-", 0).factory();
        try (final var executor = Executors.newThreadPerTaskExecutor(factory)) {

            /* we introduced the factory to give our virtual threads more descriptive thread names.
               if you want to not use the factory you can use the following syntax :
               final var executor = Executors.newVirtualThreadPerTaskExecutor() */

            var bathTime = executor.submit(() -> {
                log("-Going for a bath");
                // Block platform thread if not in the virtual env
                // If it's a virtual thread, it gets descheduled from platform thread (also called the carrier thread in this context)
                // It gets rescheduled once the sleep is done.
                sleep(Duration.ofMillis(500L));
                log("-I'm done with the bath");
            });
            var boilWater = executor.submit(() -> {
                log("-Going to boil water");
                // Block platform thread if not in the virtual env
                // If it's a virtual thread, it gets descheduled from platform thread (also called the carrier thread in this context)
                // It gets rescheduled once the sleep is done.
                sleep(Duration.ofMillis(1000L));
                log("-I'm done with water boiling");
            });
            try {
                // get waits for the computation to complete
                // get is equivalent to join
                bathTime.get();
                boilWater.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /* The way that virtual threads work is that we have a forkJoinPool which is a collaborative
    thread pool, which automatically deschedules a virtual thread once we get into a blocking
    situation */

    static int numberOfCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    static void viewCarrierThreadPoolSize() {
        final ThreadFactory factory = Thread.ofVirtual().name("routine-", 0).factory();
        try (final var executor = Executors.newThreadPerTaskExecutor(factory)) {

            // Running more virtual threads than the number of cores means that at least one
            // platform thread will handle 2 virtual threads
            IntStream.range(0, numberOfCores() + 1).forEach(i -> executor.submit(() -> {
                log("- Hi from virtual thread " + i);
                sleep(Duration.ofSeconds(1L));
            }));
        }
    }

    static boolean alwaysTrue() {
        return true;
    }

    static Thread workHard() {
        /*
        Add the following run configuration to test the code on one platform/carrier thread
        -Djdk.virtualThreadScheduler.parallelism=1
        -Djdk.virtualThreadScheduler.maxPoolSize=1
        -Djdk.virtualThreadScheduler.minRunnable=1
        */
        return virtualThread("Work Hard", () -> {
            log("-Working hard");
            // using function to avoid unreachable statement compiler error.
            while (alwaysTrue()) {
                // do nothing, CPU intensive
            }
            sleep(Duration.ofMillis(100L));
            log("-Done Working");
        });
    }

    static Thread takeABreak() {
        return virtualThread("Take a break", () -> {
            log("-Taking a break");
            sleep(Duration.ofSeconds(1L));
            log("-Done taking a break");
        });
    }

    static void workHardRoutine() {
        var workHard = workHard(); // start the virtual thread
        var takeBreak = takeABreak();

        /* With CPU bound tasks, virtual threads will never yield control over the platform/carrier thread
         * A sleep would force the virtual thread to yield control over the carrier
         * A Thread.yield() would also have the same effect
         */

        try {
            workHard.join(); // blocking calling thread
            takeBreak.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static Thread workConsciously() {
        return virtualThread("Work Hard", () -> {
            log("-Working hard");
            // using function to avoid unreachable statement compiler error.
            while (alwaysTrue()) {
                sleep(Duration.ofMillis(100L)); // this will yield control to the platform/carrier thread
                // we could also use Thread.yield()
            }

            log("-Done Working");
        });
    }

    static void workConsciouslyRoutine() {
        var workConsciously = workConsciously(); // start the virtual thread
        var takeBreak = takeABreak();

        /* With CPU bound tasks, virtual threads will never yield control over the platform/carrier thread
         * A sleep would force the virtual thread to yield control over the carrier
         * A Thread.yield() would also have the same effect
         */

        try {
            workConsciously.join(); // blocking calling thread
            takeBreak.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Pinning
    /* pinning a thread happens if you execute code inside synchronized block of methods,
    or when you use java native interface (i have to google this one) */

    // Example: one bathroom in an office, so the access to it is synchronized
    static class Bathroom {
        synchronized void use() { //  this code will pin the virtual thread to the carrier thread until it's out
            log("- Using the bathroom");
            sleep(Duration.ofSeconds(3L)); // this yields normally, but not in a synchronized method.
            log("- Done using the bathroom");
        }
    }

    static Bathroom bathroom = new Bathroom();

    static Thread goToBathroom() {
        return virtualThread("Go to Bathroom", () -> bathroom.use());
    }

    static void twoPeopleRacingToBathroom() {
        var person1 = goToBathroom();
        var person2 = goToBathroom();

        try {
            person1.join();
            person2.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        //createPlatformThreads();
        //System.out.println("Threads done");

        //concurrentMorningRoutine();

        //concurrentMorningRoutineWithExecutors();

        //viewCarrierThreadPoolSize();

        //workHardRoutine();

        //workConsciouslyRoutine();

        twoPeopleRacingToBathroom();
    }
}

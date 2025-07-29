package maaochou.jmm;

import java.util.concurrent.atomic.AtomicInteger;

public class JavaMemoryModel {

    // source code -> byte code -> machine code
    //           |javac|       |JVM|          |processor|

    /* source code gets compiled to byte code by javac */
    /* at runtime the JVM takes the byte code and run it through a jit or aot compilation
    to get machine code out of it, going to machine code includes some optimisations to the code
    (switch write read orders for example).
    So the actual execution of the code might be different from what we wrote,
    especially in the case of concurrency.
    That's why it's important to understand the JMM*/

    // Sequential consistency
    static class Reordering {
        int foo = 0;
        int bar = 0;

        /* Without optimizations the processor has to
        1- read from the 'Main memory' place the value in the processor cache.
        2- apply the operation and place the value in the cache
        3- write the value to the main memory
        Three times the 'method', once for each line.

        A possible optimization is to optimize cache reads and writes for 'foo'
        * */
        void method() {
            foo += 1;
            bar += 1;

            foo += 2;
        }

        // Sequentially inconsistent optimization done by the jvm
        /* we switch the order of the lines so that we read and write 'foo'
         only once.
         Here a new outcome is possible when looking from another thread
         in which 'foo == 3, bar == 0' which means that the code we wrote
         is different from its optimized version.
         */
        void possibleJvmFirstOptimizedMethod() {
            foo += 1;
            foo += 2;

            bar += 1;
        }

        /* next optimization is to summarize the two increments on 'foo'*/
        void possibleJvmSecondOptimizedMethod() {
            foo += 3;
            bar += 1;
        }
    }

    // Eventual consistency
    // TODO research this a bit more
    static class Caching {
        boolean flag = true;
        int count = 0;

        /* Let's imagine two processors, one only executes thread1 the other executes thread2
        - For thread1 the flag is always true, and it never changes since it's not in a synchronized block,
        this will affect the optimized code.
        - For thread2 the flag is set to false, but the change has no effect on other threads since it's not
        in a synchronized block, this will affect the optimized code.
         */

        void thread1() {
            while (flag) {
                count++;
            }
        }

        void thread2() {
            flag = false;
        }

        void possibleOptimizedThread1() {

            while (true) { /* since from the perspective of this thread the flag would never change
             because it's not in a synchronized block. */
                count++;
            }
        }

        void possibleOptimizedThread2() {
            /* thread2 might never write its changes from processor cache to main memory,
            because this change from the perspective of thread2 has no consequence so we will
            never flush the value to main memory  */
            //flag = false;
        }
    }

    //Atomicity (less common with 64 bit processors)
    static class WordTearing {
        /*
        Long is 64 bits, in a 32 bit processor we might have two slots taken in
        the processor cache to store it, if thread1 syncs the second part of the cache
        and thread2 the first part without proper sync, we might get a forth possible value.
        */ long foo = 0L;

        void thread1() {
            foo = 0x0000FFFF;
            // = 2147483647
        }

        void thread2() {
            foo = 0xFFFF0000;
            // = -2147483648
        }

    }

   /*
   Optimizations are bound to hardware architecture, the optimizations could be
   different from a processor architecture to another, hence the behavior of your code
   might be different.
   (example: android mobile devices having an arm processor running an app developed
   on an intel pc)
   (fyi: android is a jvm implementation, so the memory model does not apply 100% there)
   */

   /*
   Java Memory Model: it answers the question of what values could be observed upon reading
   from a specific field.

   JMM is specified by 'actions' (read, write...) and applying 'ordering' to these actions,
   the JMM guarantees that the read returns a particular value
    */

    /*
    In a single threaded scenario the JVM guarantees that the semantics of your program
    will be consistent with your program order

    JVM guarantees intra-thread consistency resembling sequential consistency.
     */
    static class SingleThreaded {
        int foo = 0;

        void method() {
            foo = 1;         // write action
            // program order V
            assert foo == 1; // read action
        }
    }

    /*
    JMM building blocks

    field scoped | method scoped
    -----------------------------
    final        | synchronized (method/block)
    volatile     | java.util.concurrent.locks.Lock

    using these keywords, we indicate to the JVM that it should refrain from optimizations
    that could cause concurrency issues.

    in terms of JMM the above concepts introduce additional sync actions, without such
    modifiers, reads and writes might not be ordered, which results in a data race.
     */

    //Volatile field semantics
    static class DataRace {
        boolean ready = false;
        int answer = 0;

        /*
        This wouldn't work because the optimization might decide to write ready
        before answer because it's already in cache. For example (there are many possible
        reasons why an optimization might reorder the actions), so the assertion
        in 'thread1' that 'answer == 42' might not be true.

         */
        void thread1() {
            while (!ready) ;
            assert answer == 42;
        }

        void thread2() {
            answer = 42;
            ready = true;
        }
    }

    // reordering restrictions
    static class FixedDataRaceWithVolatile {
        volatile boolean ready = false; // ready being volatile introduces a sync order
        int answer = 0;

        /*
        Only purpose of volatile field is that it introduces sync order.
        sync order introduces ordering between two threads
        ( program order is between two statements in the same thread)

        meaning that if 'thread2' observes ready, it means that it should also
        observe everything in program order in 'thread1'.
        meaning that if I observe 'ready == true' i should also find that the assertion
        answer == 42 should be true.

        all writes before our volatile field was written to have to be visible as reads in the second thread

        When a thread writes to a volatile variable, all of its previous writes are guaranteed
        to be visible to another thread when that thread is reading the same value.

        All threads have to align their values with main memory once you write to a volatile
        field. meaning a thread writing to a volatile field has to flush its processor cache
        to main memory.

        the semantics are guaranteed in 'thread1' only if we are reading from the volatile field

        COST: the jvm cannot do the reordering anymore, if you add volatile to everything
        the jvm would be restrained in the optimizations it could do.
         */
        void thread1() {
            while (!ready) ;
            assert answer == 42;
        }

        void thread2() {
            answer = 42;
            ready = true;
        }
    }

    // Synchronized block semantics : reordering restrictions
    static class FixedDataRaceWithSynchronized {
        boolean ready = false; // ready being volatile introduces a sync order
        int answer = 0;

        /*
        This example assumes that 'thread2' acquires the monitor lock first.

        Whenever we exit a monitor from a synchronized block and you enter the same
        monitor from another thread, we have a synchronization order between the two
        threads. 'thread1' needs to be able to observe everything in program order
        that was written to fields before.

         */

        synchronized void thread1() { // possible dead-lock ! if 'thread1' runs first
            // <enter this>
            while (!ready) ;
            assert answer == 42;
            // <exit this>
        }

        synchronized void thread2() {
            // <enter this>
            answer = 42;
            ready = true;
            // <exit this>
        }
    }

    // Thread life-cycle semantics: reordering restrictions
    static class ThreadLifeCycle {
        int foo = 0; // not volatile.

        /*
        When starting a thread we can expect that all values written before the start method
        was called to be visible to JVM when starting a thread.

        the jvm essentially gives a sync order
        */

        void method() {
            foo = 42;
            new Thread(() -> { /* JVM knows that we are starting a new thread and will
             guarantee ordering */
                // <start>
                assert foo == 42; // this assertion is true
            }).start();
        }
    }

    // Final field semantics
    static class UnsafePublication {
        int foo;

        UnsafePublication() {
            foo = 42;
        }

        static UnsafePublication instance;

        static void thread1() {
            instance = new UnsafePublication();
            /*
            Constructor call basically has the following:
            instance = <allocate UnsafePublication>;
            instance.<init>();
            */
        }

        // we are not guaranteed that the assertion would work
        static void thread2() {
            if (instance != null) {
                assert instance.foo == 42;
            }
        }
    }

    /*
    When a thread creates an instance, the instance's final fields are frozen; the JMM requires
    a field initial value to be visible in the initialized form to other threads.

    This requirement also holds for properties that are dereferenced via a final field, even if
    the field properties are not final themselves (memory-chain order/ memory chain visibility
    / transitive visibility).

    Does not apply for reflective changes outside a constructor/class init.
     */
    static class SafePublication {
        final int foo; // final means also that it needs to be visible to all threads

        //  `final` fields create a happens-before relationship between the constructor's completion and any code that reads the field
        SafePublication() {
            foo = 42;
        }

        static SafePublication instance;

        static void thread1() {
            instance = new SafePublication();
            /*
            Instance = <allocate UnsafePublication>;
            instance.<init>();
            <freeze instance.foo> freeze action meaning that if instance is read, all its final fields should be visible.
            It's added at the end of the constructor because of the final declaration.

            It's called a 'dereference order'.
            */
        }

        // with final declaration, we are guaranteed that the assertion would work.
        static void thread2() {
            if (instance != null) {
                assert instance.foo == 42;
            }
        }
    }

    // External actions
    static class Externalization {
        int foo = 0;

        /*
        Jit compiler cannot determine the side effects of a native operation, so the external
        actions are guaranteed to not be reordered.

        external actions include JNI, socket communications, file system operations or
        interaction with the console ...
         */
        void method() {
            jni(); // this assertion is true on the first call
            foo = 42;
        }

        // jit compiler does not know what happens on native methods, so it cannot reorder them
        native void jni();/* {
        assert foo == 0:
        } */
    }

    // Thread-divergence actions
    static class ThreadDivergence {
        int foo = 0;

        static boolean returnTrue() {
            return true;
        }

        /*
        The jvm detects a thread divergence action (meaning that the thread never exits), and it would never
        reorder the code that happen after the divergence to happen before if.

        meaning that 'foo = 42' would never happen before the while loop
        */
        void thread1() {
            while (returnTrue()) ;
            foo = 42;
        }

        void thread2() {
            assert foo == 0;
        }
    }

    // In practice: recursive final references
    /*
    the freeze action is only added at the end of the constructor,

    The semantics of a final field are only guaranteed for code placed after an object's construction

    since we are doing all this in one thread there is no problem with it
    // TODO explore recursive final references.
     */
    static class Tree {
        final Leaf leaf;

        Tree() {
            this.leaf = new Leaf(this);
        }
    }

    static class Leaf {
        final Tree tree;

        Leaf(Tree tree) {
            this.tree = tree;
        }
    }

    // In practice: double-checked locking
    static class BadDoubleChecked {
        static BadDoubleChecked instance;
        /* not marked as volatile, meaning that a second thread
        might see a partially constructed object, because the jvm
        might reorder the operations

        a happens-before relationship could only be established between
        two threads if they enter the same monitor.
        */

        static BadDoubleChecked getInstance() {
            if (instance == null) {
                synchronized (BadDoubleChecked.class) {
                    if (instance == null) {
                        instance = new BadDoubleChecked();
                    }
                }
            }
            return instance;
        }

        int foo = 0;

        BadDoubleChecked() {
            foo = 42;
        }

        void method() {
            assert foo == 42;
        }
    }

    static class DoubleCheckedVolatile {
        static volatile DoubleCheckedVolatile instance;
        // volatile guarantees the read and write operations get to main memory.
        // synchronization guarantees that only one thread can enter one monitor.

        static DoubleCheckedVolatile getInstance() {
            if (instance == null) {
                synchronized (DoubleCheckedVolatile.class) {
                    if (instance == null) {
                        instance = new DoubleCheckedVolatile();
                    }
                }
            }
            return instance;
        }

        int foo = 0;

        DoubleCheckedVolatile() {
            foo = 42;
        }

        void method() {
            assert foo == 42;
        }
    }

    // In practice: safe initialization and publication
    /*
    -synchronized method is the worst performance wise.
    -double-checked is not the best.
    -enum holder is the best pattern.
    -final wrapper is good too
    */
    static class EnumHolderSingleton {
        // Private constructor prevents direct instantiation
        private EnumHolderSingleton() {
        }

        // Enum provides thread-safe initialization guarantee
        private enum Holder {
            INSTANCE;

            private final EnumHolderSingleton singleton = new EnumHolderSingleton();
        }

        public static EnumHolderSingleton getInstance() {
            return Holder.INSTANCE.singleton;
        }
    }

    static class FinalWrapperSingleton {
        // Private constructor prevents direct instantiation
        private FinalWrapperSingleton() {
        }

        // Static holder class is only loaded when accessed
        private static class Holder {
            // Final field guarantees safe publication
            static final FinalWrapperSingleton INSTANCE = new FinalWrapperSingleton();
        }

        public static FinalWrapperSingleton getInstance() {
            return Holder.INSTANCE;
        }
    }

    // In practice: atomic access
    static class NotWorkingAtomicity {
        volatile int foo = 42;
        volatile int bar = 0;

        void callFromMultipleThreads() {
            while (foo-- > 0) { // foo = foo - 1
                // foo is not decremented atomically
                bar++; // bar = bar + 1
                // bar is not incremented atomically
            }
            assert foo == 0 && bar == 42;
        }
    }

    static class Atomicity {
        final AtomicInteger foo = new AtomicInteger(42);
        final AtomicInteger bar = new AtomicInteger(0);

        /*
        Atomic wrapper types are backed by volatile fields, and invoking the class methods
        imply the guarantees given by the JMM.
        */

        void callFromMultipleThreads() {
            while (foo.getAndDecrement() > 0) {
                bar.incrementAndGet();
            }
            assert foo.get() == 0 && bar.get() == 42;
        }
    }

    // In practice: array elements
    /*
    declaring an array as volatile does not make its elements volatile for such
    volatile element access, use java.util/concurrent.atomic.AtomicIntegerArray
    */
    // TODO add an example for volatility in array elements

    // Memory ordering in the wild: Spring beans
    /*
    An application context stores beans in a volatile field after full construction,
    then guarantees that beans are only exposed viareading from this field to induce
    a restriction.

    within spring there is a lot of manual synchronizations.
    */
    // TODO add a spring example

    /*
    The following code issues memory flushing to the JVM, but this is coding
    against the implementation and NOT the specification ( so don't do it )
    */

    // synchronized (new Object()) { /* empty */ }


}

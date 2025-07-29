package maaochou.virtualthreads.java21.first;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleVirtualThreadExample {

    public static void main(String[] args) {
        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {

            final Runnable task = () -> {
                System.out.println("Running virtual thread");
                try {
                    Thread.sleep(100); // Simulate a task taking time to finish
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            executorService.execute(task);
            executorService.shutdown();
        }
    }
}

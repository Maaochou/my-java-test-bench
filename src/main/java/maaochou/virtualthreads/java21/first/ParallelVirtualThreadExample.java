package maaochou.virtualthreads.java21.first;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class ParallelVirtualThreadExample {

    public static void main(String[] args) {
        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {

            IntStream.range(0, 10).forEach(i -> executorService.submit(() -> {
                System.out.println("Task " + i + " running on thread:" + Thread.currentThread().threadId());
                try {
                    Thread.sleep(1000); // Simulate a task taking time to finish
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));

            executorService.shutdown();
        }
    }
}

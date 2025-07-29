package maaochou.virtualthreads.java21.first;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IOBoundVirtualThreadExample {

    public static void main(String[] args) {
        try (final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {

            final Runnable ioBoundTask = () -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(IOBoundVirtualThreadExample.class.getClassLoader().getResourceAsStream("data/toBeReadTextFile"), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Thread : " + Thread.currentThread().threadId() + " reading line : " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    //throw new RuntimeException(e);
                }
            };

            executorService.submit(ioBoundTask);
            executorService.submit(ioBoundTask); // Simulating multiple io tasks
            executorService.shutdown();
        }
    }
}

package com.rmschots;

import com.rmschots.question.Request;
import com.rmschots.question.Response;
import com.rmschots.question.WebClient;
import com.rmschots.solution.MyWebClient;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        WebClient webClient = new MyWebClient(3, Duration.ofSeconds(10));
        Request fastRequest = new Request() {
            @Override
            public URI getUri() {
                return URI.create("https://www.example.com");
            }

            @Override
            public Response call() {
                try {
                    Thread.sleep(1000 + (long) (Math.random() * 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return new Response(Math.random() > 0.5 ? 200 : 408);
            }
        };

        try (ExecutorService threadPool = Executors.newFixedThreadPool(50)) {
            for (int i = 0; i < 2000; i++) {
                Thread.sleep(100);
                threadPool.submit(() -> {
                    Response response;
                    try {
                        response = webClient.sendRequest(fastRequest);
                        System.out.println("Received response: " + response.code);
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                });
            }
            threadPool.shutdown();
        }
    }
}

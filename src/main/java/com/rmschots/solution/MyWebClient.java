package com.rmschots.solution;

import com.rmschots.question.Request;
import com.rmschots.question.Response;
import com.rmschots.question.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class MyWebClient implements WebClient {
    private final Map<String, TimeoutContext> hostTimeoutMap = new ConcurrentHashMap<>();

    private Function<Request, String> determineRequestThrottleKey = request -> request.getUri().getHost();
    private int maxTimeouts = 3;
    private Duration throttleTime = Duration.ofMinutes(5);

    public MyWebClient() {
    }

    public MyWebClient(int maxTimeouts, Duration throttleTime) {
        this.maxTimeouts = maxTimeouts;
        this.throttleTime = throttleTime;
    }

    public MyWebClient(Function<Request, String> determineRequestThrottleKey, int maxTimeouts, Duration throttleTime) {
        this.determineRequestThrottleKey = determineRequestThrottleKey;
        this.maxTimeouts = maxTimeouts;
        this.throttleTime = throttleTime;
    }

    @Override
    public Response sendRequest(Request request) {
        String throttleKey = determineRequestThrottleKey.apply(request);
        TimeoutContext timeoutContext = hostTimeoutMap.computeIfAbsent(throttleKey, _ -> new TimeoutContext(throttleTime, maxTimeouts));

        timeoutContext.cleanUp();
        timeoutContext.checkIfThresholdExceeded();

        Response response = request.call();

        if (response.code == 408) {
            timeoutContext.addFailure();
        }
        return response;
    }

    public void setDetermineRequestThrottleKey(Function<Request, String> determineRequestThrottleKey) {
        this.determineRequestThrottleKey = determineRequestThrottleKey;
    }

    public void setMaxTimeouts(int maxTimeouts) {
        this.maxTimeouts = maxTimeouts;
        hostTimeoutMap.values().forEach(timeoutContext -> timeoutContext.setMaxTimeouts(maxTimeouts));
    }

    public void setThrottleTime(Duration throttleTime) {
        this.throttleTime = throttleTime;
        hostTimeoutMap.values().forEach(timeoutContext -> timeoutContext.setThrottleTime(throttleTime));
    }

    static class TimeoutContext {
        private final Queue<LocalDateTime> timeOutList = new ConcurrentLinkedQueue<>();
        private Duration throttleTime;
        private int maxTimeouts;

        public TimeoutContext(Duration throttleTime, int maxTimeouts) {
            this.throttleTime = throttleTime;
            this.maxTimeouts = maxTimeouts;
        }

        public void cleanUp() {
            if (timeOutList.size() >= maxTimeouts) {
                clearOldTimeouts();
            }
        }

        public void checkIfThresholdExceeded() {
            if (timeOutList.size() >= maxTimeouts) {
                throw new TooManyTimeoutsException();
            }
        }

        public void addFailure() {
            timeOutList.add(LocalDateTime.now());
        }

        public void setThrottleTime(Duration throttleTime) {
            this.throttleTime = throttleTime;
        }

        public void setMaxTimeouts(int maxTimeouts) {
            this.maxTimeouts = maxTimeouts;
        }

        private void clearOldTimeouts() {
            LocalDateTime cutOffTime = LocalDateTime.now().minus(throttleTime);
            LocalDateTime peekTime;
            while ((peekTime = timeOutList.peek()) != null && peekTime.isBefore(cutOffTime)) {
                timeOutList.remove(peekTime);
            }
        }
    }
}

package com.a207.runkk.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/** 시험용 TaskScheduler: 예약을 모아 두고 advanceTo(시각)로 그 시각까지의 작업을 손으로 돌린다(T-31 스텁). */
class ManualTaskScheduler implements TaskScheduler {

    /** 예약 하나. */
    static final class Task implements ScheduledFuture<Object> {
        final Runnable work;
        final Instant at;
        boolean cancelled;
        boolean done;

        Task(Runnable work, Instant at) {
            this.work = work;
            this.at = at;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done || cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }

    final List<Task> tasks = new ArrayList<>();

    /** at ≤ instant인 살아 있는 예약을 시각 순으로 돌리고 돌린 수를 돌려준다. */
    int advanceTo(Instant instant) {
        int ran = 0;
        for (Task task :
                tasks.stream()
                        .filter(t -> !t.isDone() && !t.at.isAfter(instant))
                        .sorted((a, b) -> a.at.compareTo(b.at))
                        .toList()) {
            task.done = true;
            task.work.run();
            ran++;
        }
        return ran;
    }

    List<Task> pending() {
        return tasks.stream().filter(t -> !t.isDone()).toList();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        Task t = new Task(task, startTime);
        tasks.add(t);
        return t;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant start, Duration period) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant start, Duration delay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        throw new UnsupportedOperationException();
    }
}

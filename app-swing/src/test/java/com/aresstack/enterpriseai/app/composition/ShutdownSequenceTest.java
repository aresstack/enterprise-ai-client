package com.aresstack.enterpriseai.app.composition;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShutdownSequenceTest {

    @Test
    public void stepsRunInOrderExactlyOnceEvenWhenOneFails() throws Exception {
        final List<String> log = new ArrayList<String>();
        ShutdownSequence sequence = new ShutdownSequence()
                .then("a", new Runnable() {
                    @Override
                    public void run() {
                        log.add("a");
                    }
                })
                .then("b", new Runnable() {
                    @Override
                    public void run() {
                        log.add("b");
                        throw new IllegalStateException("b kaputt");
                    }
                })
                .then("c", new Runnable() {
                    @Override
                    public void run() {
                        log.add("c");
                    }
                });
        assertEquals(Arrays.asList("a", "b", "c"), sequence.stepNames());
        sequence.run();
        sequence.run();
        Thread hook = sequence.asShutdownHook();
        hook.start();
        hook.join();
        assertEquals(Arrays.asList("a", "b", "c"), log);
        assertEquals(Arrays.asList("a", "b", "c"), sequence.executedSteps());
        assertTrue(sequence.isFinished());
    }

    @Test
    public void concurrentCallersRunTheSequenceOnlyOnce() throws Exception {
        final List<String> log = java.util.Collections.synchronizedList(new ArrayList<String>());
        final ShutdownSequence sequence = new ShutdownSequence().then("slow", new Runnable() {
            @Override
            public void run() {
                log.add("slow");
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    sequence.run();
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(1, log.size());
    }
}

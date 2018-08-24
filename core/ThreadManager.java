package core;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class ThreadManager {

    // This is a global map (not specific to a single pool)
    // For each thread, we store a "stack" - a history of the evaluation samplers associated with it.
    // (though the stack sizes will only ever be greater than one if a thread from one fork-join pool submits a
    //  task to another fork-join pool)
    private static Map<Thread, Deque<EvalSampler>> threadsToSamplers = new ConcurrentHashMap<>();

    static void setSamplerForThread(EvalSampler sampler) {
        if (!threadsToSamplers.keySet().contains(Thread.currentThread())) {
            threadsToSamplers.put(Thread.currentThread(), new LinkedList<>());
        }
        threadsToSamplers.get(Thread.currentThread()).offerLast(sampler);
        // So the job sampler associated with the pool most recently joined by this thread
        // ... has gone to the top of this thread's stack.
        // (NB no need to synchronize this method, because only this thread will ever try to change its target sampler!)
    }

    static void deleteSamplerForThread() {
        threadsToSamplers.get(Thread.currentThread()).pollLast();
        // So this thread's target sampler is now part of the previous pool that was joined by this thread
        // ... or if this was the first pool ever joined by this thread, then we now remove this thread from the map:
        if (threadsToSamplers.get(Thread.currentThread()).isEmpty()) {
            threadsToSamplers.remove(Thread.currentThread());
        }
    }

    static EvalSampler getSamplerForThread() {
        if (threadsToSamplers.keySet().contains(Thread.currentThread())) {
            return threadsToSamplers.get(Thread.currentThread()).peekLast();
        } else {
            throw new IllegalStateException("Fork/join occurring outside of thread pool.");
        }
    }

}

package core;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// A static map, recording the assignments of threads to EvalSamplers.
class ThreadManager {

    // This is a global map (not specific to a single pool)
    // For each thread, we store a "stack" - a history of the evaluation samplers associated with it
    // - see examples in the extended comments at the bottom of this file.
    private static final Map<Thread, Deque<EvalSampler>> THREADS_TO_SAMPLERS = new ConcurrentHashMap<>();

    // Called when a pool worker is initialised, or when an external thread invokes a task to a pool.
    static void setSamplerForThread(EvalSampler sampler) {

        if (!THREADS_TO_SAMPLERS.keySet().contains(Thread.currentThread())) {
            THREADS_TO_SAMPLERS.put(Thread.currentThread(), new LinkedList<>());
        }
        THREADS_TO_SAMPLERS.get(Thread.currentThread()).offerLast(sampler);

        // So the job sampler associated with the pool that this thread has most recently joined goes to the top of
        // the stack.
        // (NB no need to synchronize this method, because the only thread that will ever try to modify the sampler
        //  assigned to a given thread is itself!)
    }

    // Called when a pool worker is terminated, or when an external thread receives the result of the task it
    // invoked to a pool and is therefore ready to drop out of this pool.
    static void deleteSamplerForThread() {

        THREADS_TO_SAMPLERS.get(Thread.currentThread()).pollLast();

        // So this thread's target sampler is now part of the previous pool that was joined by this thread
        // ... or if this was the first pool ever joined by this thread, then we now remove this thread from the map:

        if (THREADS_TO_SAMPLERS.get(Thread.currentThread()).isEmpty()) {
            THREADS_TO_SAMPLERS.remove(Thread.currentThread());
        }
    }

    // Called during fork/join operations.
    static EvalSampler getSamplerForThread() {

        if (THREADS_TO_SAMPLERS.keySet().contains(Thread.currentThread())) {
            return THREADS_TO_SAMPLERS.get(Thread.currentThread()).peekLast();
        }
        else {
            throw new IllegalStateException("Fork/join occurring outside of thread pool.");
        }
    }

    /*
     Example execution.

     At the start of the program, we have:
     THREADS_TO_SAMPLERS = {}

     Suppose we initialise a pool with 3 workers. We would have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3]}

     Then when the main thread invokes a task to this pool, we have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3],
                            main            : [pool-1-sampler-external]}

     Now suppose, during the computation, a second pool is created (with 2 workers).
     We then have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3],
                            main            : [pool-1-sampler-external],
                            pool-2-worker-1 : [pool-2-sampler-1],
                            pool-2-worker-2 : [pool-2-sampler-2]}

     Suppose pool-1-worker-1 invokes a task to pool-2. Then pool-1-worker-1 gets temporarily transferred to pool-2:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1, pool-2-sampler-external],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3],
                            main            : [pool-1-sampler-external],
                            pool-2-worker-1 : [pool-2-sampler-1],
                            pool-2-worker-2 : [pool-2-sampler-2]}

     When pool-1-worker-1 receives the result of the task that it invoked to pool-2, it returns to pool-1:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                          pool-1-worker-2 : [pool-1-sampler-2],
                          pool-1-worker-3 : [pool-1-sampler-3],
                          main            : [pool-1-sampler-external],
                          pool-2-worker-1 : [pool-2-sampler-1],
                          pool-2-worker-2 : [pool-2-sampler-2]}

     When pool-2 gets terminated, its workers die, and we have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3],
                            main            : [pool-1-sampler-external]}

     When main receives the result of the task that it invoked, it leaves pool-1. So we have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3]}

     Finally, when main calls terminate on pool-1, we have
     THREADS_TO_SAMPLERS = {}
     */

    /*
     Another example execution.

     Assume that initially, we have the main thread plus a non-fork-join thread called thread-1 running in parallel.
     We have:
     THREADS_TO_SAMPLERS = {}

     Suppose we initialise a pool with 3 workers. We would have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3]}

     Then when the main thread invokes a task to this pool, we have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3],
                            main            : [pool-1-sampler-external]}

     Next, a non-fork-join thread called thread-1 invokes a different task to this pool. We have:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                          pool-1-worker-2 : [pool-1-sampler-2],
                          pool-1-worker-3 : [pool-1-sampler-3],
                          main            : [pool-1-sampler-external],
                          thread-1        : [pool-1-sampler-external]}
     (so both main and thread-1 are sharing the external sampler!)

     Thread-1 then receives the result of its task:
     THREADS_TO_SAMPLERS = {pool-1-worker-1 : [pool-1-sampler-1],
                            pool-1-worker-2 : [pool-1-sampler-2],
                            pool-1-worker-3 : [pool-1-sampler-3],
                            main            : [pool-1-sampler-external]}

     ... then thread-1 calls terminate on pool-1:
     THREADS_TO_SAMPLERS = {main            : [pool-1-sampler-external]}
     (Note that, if the evaluation of the task submitted by main relies on completion of subtasks that are stuck in
      queues owned by pool-1 workers that were killed, these subtasks will eventually be completely by the main thread,
      by stealing from the queues of the workers that have been killed.

     Finally, when main receives the result of its task, we have
     THREADS_TO_SAMPLERS = {}
     */

}

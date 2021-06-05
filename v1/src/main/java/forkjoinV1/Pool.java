package forkjoinV1;

/**
 * My simple implementation of a fork-join pool.
 *
 * In this implementation, each worker in the pool maintains its own job queue:
 * - A worker's job queue is the place where the worker dumps the jobs that it forks, to be evaluated later.
 * - A worker's job queue is also the place from where it finds new jobs to work on, either because it has just
 *   completed the previous job that it was working on, or because it is joining on a job that is not yet complete
 *   and so is looking for another job to get on with in the meantime.
 *
 * <p>If a worker is looking for a new job but its job queue is empty, then it will try to steal jobs from other
 * workers' queues. To avoid contention, workers access their own queues at the front, but steal from other queues
 * at the back.
 *
 * <p>There is also an external job queue, owned collectively by all the external threads that have submitted tasks to
 * this pool by calling the invoke method. External threads effectively operate as a part of the pool, just like a
 * genuine pool worker, until the tasks they submitted are complete, at which point they drop out from the pool.
 * In particular, external threads can steal from the queues owned by internal workers.
 *
 * <p>In this implementation, the pool workers are alive from the time the pool is initialised until the time the pool
 * is terminated by calling the terminate method. Between initialisation and termination, the workers continually cycle
 * through the job queues, looking for new jobs to work on, with the exception that, if a worker comes a complete
 * circuit of all job queues and fails to find a job, it sleeps for a specified period of time (the default time being
 * 1 millisecond), before starting its next circuit of the job queues.
 */
public class Pool {

    // Sampler object, used by the external workers to dump forked tasks and find pending tasks.
    private final AsyncEvalSampler externalSampler;

    // Status marker - will become "true" when the terminate method is called.
    private volatile boolean terminationStatus = false;

    // Default duration of sleep
    // - relevant for when a thread fails to find a new job after a completing a full circuit of the queues.
    private static final int DEFAULT_SLEEP_MILLIS = 1;

    boolean isTerminated() {
        return terminationStatus;
    }

    /**
     * Constructor.
     * @param numWorkers  Number of internal workers in pool. (Typically this will be numProcessors - 1, since the
     *                    external calling thread also participates in the pool's activity.)
     * @param sleepMillis The period of time for which a thread will sleep, if it fails to find a new job after
     *                    completing a circuit of all job queues in the pool. Measured in milliseconds.
     * @throws IllegalArgumentException if the number of workers or the sleep time is negative.
     */
    public Pool(int numWorkers, int sleepMillis) {
        if (numWorkers < 0) {
            throw new IllegalArgumentException("Number of workers must be non-negative.");
            // NB a pool with 0 workers is technically possible - the external thread(s) will do all of the work.
        }

        if (sleepMillis < 0) {
            throw new IllegalArgumentException("Sleep time must be non-negative.");
        }

        // Make evaluation queues.
        AsyncEvalQueue[] allQueues = new AsyncEvalQueue[numWorkers + 1]; // the final element is the external thread(s)
        for (int index = 0; index < numWorkers + 1; index++) {
            allQueues[index] = new AsyncEvalQueue();
        }

        // Make evaluation samplers.
        AsyncEvalSampler[] allSamplers = new AsyncEvalSampler[numWorkers + 1]; // the final sampler is the external one

        for (int index = 0; index < numWorkers + 1; index++) {
            AsyncEvalQueue ownQueue = allQueues[index]; // the worker's own queue
            AsyncEvalQueue[] otherQueues = new AsyncEvalQueue[numWorkers]; // for stealing from other workers

            for (int otherIndex = 0; otherIndex < numWorkers; otherIndex++) {
                // adding other workers' queues in "cyclic" order (see extended comment below).
                otherQueues[otherIndex] = allQueues[(index + otherIndex + 1) % (numWorkers + 1)];
            }
            allSamplers[index] = new AsyncEvalSampler(ownQueue, otherQueues, this, sleepMillis);
        }

        // Assign evaluation samplers to workers, and set the workers off.
        for (int index = 0; index < numWorkers; index++) {
            WorkerRunnable workerRunnable = new WorkerRunnable(allSamplers[index], this);
            Thread workerThread = new Thread(workerRunnable);
            workerThread.start();

            // NB (1) workerThread.join() is never called - termination is by calling the terminate() method.
            //    (2) There is no need to save references to the workers in this Pool object - instead,
            //        a reference to this Pool object is stored inside the workers, and this reference is used
            //        by the workers to read the termination status of the pool.
        }

        // Save a reference to the external sampler.
        externalSampler = allSamplers[numWorkers];
    }

    /*
     ^^^ Note on ordering:
     The workers are arranged in a circle. (For the purposes of this exercise, the external threads are
     collectively treated as one single worker, which occupies a single position in the circle.)
     e.g. If numWorkers = 3, then we have the circle,
          worker-1 -> worker-2 -> worker-3 -> external -> worker-1
     Work-retrieval/work-stealing is carried out in cyclic fashion.
     e.g. worker-1 tries worker-1-queue, then worker-2-queue, then worker-3-queue, then external-queue
          worker-2 tries worker-2-queue, then worker-3-queue, then external-queue, then worker-1-queue
          worker-3 tries worker-3-queue, then external-queue, then worker-1-queue, then worker-2-queue
          external tries external-queue, then worker-1-queue, then worker-2-queue, then worker-3-queue
     */

    /**
     * Constructor - using default pool size of numProcessors - 1 and a default sleep time of 1 millisecond.
     */
    public Pool() {
        this(Runtime.getRuntime().availableProcessors() - 1, DEFAULT_SLEEP_MILLIS);
    }

    /**
     * Sends signal to the pool workers telling them to stop, once their current jobs are complete.
     * (However, external threads will continue processing the jobs in the pool, including stealing from the
     *  internal workers' queues, until these external threads have received the answers to the tasks that they
     *  originally submitted to the pool. This ensures that all external tasks will eventually get completed.)
     *
     * <p>If the pool has already been terminated by the time this terminate() call is made, then the terminate call
     * has no effect.
     *
     * <p>Note that this method returns immediately - the method call does not wait for the pool workers to finish
     * their current jobs. Thus, if pool workers are busy when this method is called, the actual time when the
     * worker threads terminate may be later than the time of the terminate() call.
     */
    public void terminate() {
        terminationStatus = true;
    }

    /**
     * Submits task to pool, runs the computation defined by the task's .compute() method within the pool,
     * and returns the result of the computation once it is available.
     *
     * <p>Note that, in between submitting the task and receiving the answer, the calling thread does not sit idle.
     * Rather, it participates in the activity of the pool, just like any other worker thread in the pool, until
     * the result of the computation is available. (In particular, the calling thread may steal tasks from internal
     * pool workers, and vice versa.)
     *
     * <p>This implementation guarantees a happens-before relationship between the calling thread entering the invoke()
     * call and the beginning of the computation of the task. It also guarantees a happens-before relationship between
     * the end of the computation and the calling thread returning from the invoke() call.
     *
     * <p>In typical usage, only one thread will ever call the invoke() method on a given pool at a given time, though
     * it is technically permissible for invoke() to be called on a single pool from multiple threads simultaneously.
     *
     * @param task The task to compute.
     * @param <V> The output type of the task - may be Void if the task does not return a value.
     * @throws IllegalStateException if the pool has already been terminated.
     * @return The result of the computation of the task.
     */
    public <V> V invoke (Task<V> task) {
        if (isTerminated()) {
            throw new IllegalStateException("Cannot receive task - pool has been terminated.");
        }

        // Use calling thread as part of pool until the task is complete.
        // So here, we make a global record of the assignment of this thread to the external sampler of this pool.
        ThreadManager.setSamplerForThread(externalSampler);
        // (Note: If the calling thread was originally part of a different fork-join pool, then it will temporarily
        //  transfer to this pool... until it exits from this invoke method, at which point it will return to its
        //  previous pool.)

        // Begins computation *synchronously*, on the calling thread.
        // (Asynchronous computations only get triggered if the implementation of task.compute() forks sub-tasks
        //  - any such sub-tasks forked by task.compute() would then get placed on the external job queue, from where
        //  they may be stolen by internal pool workers.)
        V answer = task.compute();

        // Once the evaluation is complete, the calling thread then drops out of this pool.
        // So here, we erase the global record of this thread being assigned to the external sampler of this pool.
        ThreadManager.deleteSamplerForThread();

        return answer;
    }

}

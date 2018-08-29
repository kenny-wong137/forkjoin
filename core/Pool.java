package core;

/**
 * My simple implementation of a fork-join pool.
 *
 * In this implementation, each worker in the pool maintains its own job queue:
 * - A worker's job queue is the place where the worker dumps the jobs that it forks, to be evaluated later.
 * - A worker's job queue is also the place from where it finds new jobs to work on, either because it has just
 *   completed the previous job that it was working on, or because it is joining on a job that is not yet complete
 *   and so is looking for another job to get on with in the meantime.
 *
 * If a worker is looking for a new job but its job queue is empty, then it will try to steal jobs from other workers'
 * queues. To avoid contention, workers access their own queues at the front, but steal from other queues at the back.
 *
 * There is also an external job queue, owned collectively by all the external threads that have submitted tasks to this
 * pool by calling the invoke method. External threads effectively operate as a part of the pool, just like a genuine
 * pool worker, until the tasks they submitted are complete, at which point they drop out from the pool. In particular,
 * external threads can steal from the queues owned by internal workers.
 *
 * In this implementation, the pool workers are "semi-alive" from the time the pool is initialised to the time the pool
 * is terminated by calling the terminate method. To be more specific, the workers will continually look for new jobs
 * from the job queues. If a worker comes a complete circuit of all job queues and fails to find a job, it sleeps
 * for a given period of time (currently set to 10 microseconds by default, but can be customised), before starting its
 * next circuit of the job queues.
 */
public class Pool {

    // Sampler object, used by the external workers to dump forked tasks and find pending tasks.
    private final EvalSampler externalSampler;

    // Status marker - will become "true" when the terminate method is called.
    private volatile boolean terminated = false;

    // Default duration of sleep - for when a thread fails to find a new job.
    private static final int DEFAULT_SLEEP_NANOS = 10000;

    // Reads the status marker - only called by the internal workers.
    boolean isTerminated() {
        return terminated;
    }

    /**
     * Constructor.
     * @param numWorkers Number of internal workers in pool. (Typically this will be numProcessors - 1, since the
     *                   external calling thread also participates in the pool's activity.)
     * @param sleepNanos The period of time for which a thread will sleep, if it fails to find a new job after
     *                   completing a circuit of all job queues in the pool. Measured in nanoseconds.
     * @throws IllegalArgumentException if the number of workers is negative.
     */
    public Pool(int numWorkers, int sleepNanos) {

        if (numWorkers < 0) {
            throw new IllegalArgumentException("Number of workers must be non-negative.");
            // NB a pool with 0 workers is technically possible - the external thread(s) will do all of the work.
        }

        if (sleepNanos < 0) {
            throw new IllegalArgumentException("Sleep time must be non-negative.");
        }

        // Make evaluation queues.
        EvalQueue[] allQueues = new EvalQueue[numWorkers + 1]; // the final "worker" is really the external thread(s)
        for (int index = 0; index < numWorkers + 1; index++) {
            allQueues[index] = new EvalQueue();
        }

        // Make evaluation samplers.
        EvalSampler[] allSamplers = new EvalSampler[numWorkers + 1]; // the final sampler is the external one

        for (int index = 0; index < numWorkers + 1; index++) {
            EvalQueue ownQueue = allQueues[index]; // the worker's own queue
            EvalQueue[] otherQueues = new EvalQueue[numWorkers]; // for stealing from other workers

            for (int otherIndex = 0; otherIndex < numWorkers; otherIndex++) {
                // adding other workers' queues in "cyclic" order (see extended comment below).
                otherQueues[otherIndex] = allQueues[(index + otherIndex + 1) % (numWorkers + 1)];
            }
            allSamplers[index] = new EvalSampler(ownQueue, otherQueues, sleepNanos);
        }

        // Assign evaluation samplers to workers, and set the workers off.
        for (int index = 0; index < numWorkers; index++) {
            WorkerRunnable workerRunnable = new WorkerRunnable(allSamplers[index], this);
            Thread workerThread = new Thread(workerRunnable);
            workerThread.start();

            // NB (1) workerThread.join() is never called - termination is by calling the terminate() method.
            //    (2) There is no need to save references to the workers.
        }

        // Save a reference to the external sampler.
        externalSampler = allSamplers[numWorkers];
    }

    /*
     ^^^ Note on ordering:
     The workers are arranged in a circle. (For the purposes of this exercise, the external
     threads are collectively treated as one single worker, which occupies a single position in the circle.)

     e.g. If numWorkers = 3, then we have the circle,
          worker-1 -> worker-2 -> worker-3 -> external -> worker-1

     Work-retrieval/work-stealing is carried out in cyclic fashion.
     e.g. worker-1 tries worker-1-queue, then worker-2-queue, then worker-3-queue, then external-queue
          worker-2 tries worker-2-queue, then worker-3-queue, then external-queue, then worker-1-queue
          worker-3 tries worker-3-queue, then external-queue, then worker-1-queue, then worker-2-queue
          external tries external-queue, then worker-1-queue, then worker-2-queue, then worker-3-queue
     */

    /**
     * Constructor - using default pool size of numProcessors - 1 and a default sleep time of 10 microseconds.
     */
    public Pool() {
        this(Runtime.getRuntime().availableProcessors() - 1, DEFAULT_SLEEP_NANOS);
    }

    /**
     * Sends signal to the pool workers telling them to stop work, once their current jobs are complete.
     * (However, external threads will continue processing the jobs in the pool, including stealing from the
     *  internal workers' queues, until these external threads have received the answers to the tasks that they
     *  originally submitted to the pool. This ensures that all external tasks will eventually get completed.)
     *
     * If the pool has already been terminated by the time this .terminate() call is made, then the terminate call
     * has no effect.
     *
     * Note that this method returns immediately - it does not wait for the pool workers to finish their current jobs.
     */
    public void terminate() {
        terminated = true;
    }

    /**
     * Submits task to pool, runs the computation defined by the task's compute() method within the pool,
     * and returns the result of the computation once it is available.
     *
     * Note that, in between submitting the task and receiving the answer, the calling thread does not sit idle.
     * Rather, it participates in the activity of the pool, just like any other worker thread in the pool, until
     * the result of the computation is available. (In particular, the calling thread may steal tasks from other pool
     * workers.)
     *
     * This implementation guarantees a happens-before relationship between the calling thread entering the "invoke"
     * call and the beginning of the computation of the task. It also guarantees a happens-before relationship between
     * the end of the computation and the calling thread returning from the "invoke" call.
     *
     * @param task The task to compute.
     * @param <V> The output type of the task - may be Void if the task does not return a value.
     * @throws IllegalStateException if the pool has already been terminated.
     * @return The result of the computation of the task.
     */
    public <V> V invoke (Task<V> task) {

        if (terminated) {
            throw new IllegalStateException("Cannot receive task - pool has been terminated.");
        }

        // Use calling thread as part of pool until the task is complete.
        // So here, we make a global record of the assignment of this thread to the external sampler of this pool.
        // (Note: If the calling thread was originally part of a different fork-join pool, then it will temporarily
        //  transfer to this pool until it exits from this invoke method.)
        ThreadManager.setSamplerForThread(externalSampler);

        // Begins computation *synchronously*, on the calling thread.
        // (Asynchronous computations only get triggered if the implementation of task.compute() forks sub-tasks
        //  - any such sub-tasks forked by task.compute() would then get placed on the external job queue, from where
        //  they may be stolen by internal pool workers.)
        V answer = task.compute();

        // Once the evaluation is complete, the calling thread then drops out of this pool
        // So here, we erase the global record of this thread being assigned to the external sampler of this pool.
        ThreadManager.deleteSamplerForThread();

        return answer;
    }

}

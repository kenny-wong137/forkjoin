package core;

// A worker in the fork-join pool.
class Worker implements Runnable {

    private final EvalSampler sampler; // to/from which this worker dumps/finds tasks
    private volatile boolean isTerminated = false;

    Worker(EvalSampler sampler) {
        this.sampler = sampler;
        Thread thread = new Thread(this);
        thread.start();
        // NB thread.join() is never called!
    }

    void terminate() {
        isTerminated = true;
    }

    @Override
    public void run() {

        // Make a global record of the EvalSampler object associated with this worker.
        ThreadManager.setSamplerForThread(sampler);

        // Runs until notified that the pool is terminated.
        while (!isTerminated) {
            // Sample a job from this workers' queue (or, if its queue is empty, then try to steal from another worker.)
            Evaluation<?> evalJob = sampler.get();

            if (evalJob != null) {
                // Case: successfully found a job - proceed with computation of this job.
                evalJob.runComputation();
            }

            /*
             IMPROVE: Ideally the thread should wait here, until either it is notified that a new evaluation job has
             been forked, or until it is notified that the thread-pool has been terminated.
             (At the moment, the thread just cycles the while loop, broken only by the periodic sleeps in
              the sampler.get() method.)
             */
        }

        // Once terminated, removes global record of the EvalSampler object associated with this worker.
        ThreadManager.deleteSamplerForThread();
    }
}

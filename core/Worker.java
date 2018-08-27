package core;

// A worker in the fork-join pool.
class Worker implements Runnable {

    private EvalSampler sampler; // to/from which this worker dumps/finds tasks
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
            Evaluation<?> evalJob = sampler.get();

            if (evalJob != null) {
                evalJob.runComputation();
            }
        }

        // Once terminated, removes global record of the EvalSampler object associated with this worker.
        ThreadManager.deleteSamplerForThread();
    }
}

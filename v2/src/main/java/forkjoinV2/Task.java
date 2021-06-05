package forkjoinV2;

/**
 * Task, to be executed by a fork-join {@link Pool}.
 * <p>
 * This is an abstract class. To use this, override the {@link Task#compute()} method
 * with the implementation of the task to be executed.
 * <p>
 * Usually, a task will spawn sub-tasks. To do this, the implemention of the
 * {@link Task#compute()} method will create new {@link Task} objects associated with
 * these sub-tasks, calling {@link Task#fork()} to run them asynchronously, and calling
 * {@link Task#join()} to obtain the results of these asynchronous computations.
 *
 * @param <V> the type of the computation result; may be Void if no result returned
 */
abstract public class Task<V> {

    private V result;
    private boolean isComplete = false;

    /**
     * Executes the task.
     * This overridable method is a description of the task to be executed.
     * This method may be called explicitly in order to run the task synchronously.
     * <p>
     * (To run the task asynchronously within a {@Pool}, you should instead
     * call {Task#fork()} to schedule it for execution, and call
     * {Task#join()} to obtain the result.)
     *
     * @return result of executing the task
     */
    abstract protected V compute();

    void asyncCompute() {
        result = compute();
        Pool pool = ((Worker) Thread.currentThread()).getPool();
        synchronized (pool.getLock()) {
            isComplete = true;
            pool.getLock().notifyAll();
        }
    }

    V externalInvoke(Pool pool) {
        synchronized (pool.getLock()) {
            pool.addExternalTask(this);
            pool.getLock().notifyAll();
            while (!isComplete) {
                try {
                    pool.getLock().wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                }
            }
            return result;
        }
    }

    /**
     * Executes the task asynchronously.
     * Calling this method places the task on a queue, from where it will be
     * retrieved by pool workers.
     * <p>
     * This method be called from within a fork-join {@Pool}.
     * It must not be called more than once.
     */
    public void fork() {
        Pool pool = ((Worker) Thread.currentThread()).getPool();
        synchronized (pool.getLock()) {
            pool.addTask(this);
            pool.getLock().notifyAll();
        }
    }

    /**
     * Obtains the result of an asynchronous execution of the task.
     * If the result is not available when this method is called,
     * the calling thread will take other tasks from the pool's queue to work on
     * until the result for this task is available.
     * <p>
     * This method must be called from within a fork-join {@link Pool}.
     * It must not be called more than once.
     * The call must happen after the {@link Task#fork()} method has been called,
     * and it must happen from within the same fork-join {@link Pool} where
     * {@link Task#fork()} was called.
     *
     * @return result of executing the task
     */
    public V join() {
        Pool pool = ((Worker) Thread.currentThread()).getPool();
        while (true) {
            Task<?> otherTask;
            synchronized (pool.getLock()) {
                while (!(isComplete || pool.hasTask())) {
                    try {
                        pool.getLock().wait();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                if (isComplete) {
                    return result;
                } else {
                    otherTask = pool.getTask();
                }
            }
            otherTask.asyncCompute();
        }
    }

}

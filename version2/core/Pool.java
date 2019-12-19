package core;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Thread pool, based on the fork-join framework.
 * Executes tasks that extend the abstract {@link Task} class.
 * <p>
 * To use this fork-join pool, instantiate a {@link Pool} object, call
 * {@link Pool#start()} to start the worker threads, submit external tasks
 * using {@link Pool#addExternalTask(Task)} method, and call
 * {@link Pool#terminate()} to shut the pool down once the tasks are complete.
 * <p>
 * This implementation maintains a central task queue, with internal threads
 * taking tasks from and adding tasks to the front, and with external threads
 * submitting tasks to the back.
 * <p>
 * All synchronisation is managed by a single lock. 
 */
public class Pool {

    private final Deque<Task<?>> taskQueue = new LinkedList<>();
    private final Worker[] workers;
    private final Object lock = new Object();
    private boolean isShutdown = false;

    /**
     * Fork-join pool
     * 
     * @param numWorkers number of worker threads in pool
     */
    public Pool(int numWorkers) {
        workers = new Worker[numWorkers];
        for (int workerId = 0; workerId < numWorkers; workerId++) {
            workers[workerId] = new Worker(this);
        }
    }

    Object getLock() {
        return lock;
    }

    boolean hasTask() {
        return !taskQueue.isEmpty();
    }

    void addTask(Task<?> task) {
        taskQueue.offerFirst(task);
    }

    void addExternalTask(Task<?> task) {
        taskQueue.offerLast(task);
    }

    Task<?> getTask() {
        return taskQueue.pollFirst();
    }

    boolean isShutdown() {
        return isShutdown;
    }

    /**
     * Submits external {@link Task} to pool for execution.
     * The thread that calls this method will hang until the task is complete,
     * at which point it will return with the result of the task.
     * 
     * @param task external task to submit
     * @return result of completing task
     */
    public <V> V invoke (Task<V> task) {
        return task.externalInvoke(this);
    }

    /**
     * Starts the worker threads, allowing the thread pool to execute tasks.
     */
    public void start() {
        for (Worker worker : workers) {
            worker.start();
        }
    }

    /**
     * Terminates each worker thread once it has completed its current task.
     */
    public void terminate() {
        synchronized (lock) {
            isShutdown = true;
            lock.notifyAll();
        }
        for (Worker worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }
    }

}

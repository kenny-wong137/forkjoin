package core;

// i.e. an *async* evaluation of a task, triggered by forking
class Evaluation<V> {

    private Task<V> task;
    private volatile boolean completionStatus = false;
    private V answer;

    Evaluation(Task<V> task) {
        this.task = task;
    }

    void runComputation() {
        answer = task.compute();
        completionStatus = true;
    }

    boolean isComplete() {
        return completionStatus;
    }

    V getAnswer() {
        return answer;
    }

    /*
     Note about happens-before relationships:
     (1) Stuff that happens on the thread that forked this Evaluation job prior to forking *happens-before*
         the evaluation of this Evaluation job by the worker thread doing the evaluation.
         This is because the forking thread adds the Evaluation job to its ConcurrentLinkedDeque of tasks during
         the fork. The evaluating thread then gets/steals the job from this ConcurrentLinkedDeque, before
         running the computation. According to the docs for the java.util.concurrent package, all concurrent containers
         are implemented in such a way that there is a happens-before relationship between adding an object to the
         container and retrieving the same object from the container.

     (2) The evaluation of this job and the recording of the answer by the evaluating thread *happens-before*
         the joining of this job by the thread doing the join.
         This is because the evaluating thread does the computation and writes the answer before writing to
         the volatile variable completionStatus; the joining thread then reads this volatile variable verifying that
         the evaluation is complete before retrieving the answer.
     */
}

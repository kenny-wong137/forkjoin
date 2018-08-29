package core;

// An *async* evaluation of a task, triggered by forking.
// (Since we allow a task to be forked multiple times, it is possible for multiple Evaluation objects to refer to
//  a single task.)
class Evaluation<V> {

    private final Task<V> task; // a reference to the description of the task to be evaluated.
    private volatile boolean completionStatus = false; // will become true once the evaluation is complete.
    private V answer; // will equal the result of the evaluation once the evaluation is complete

    // NB no need to mark "answer" as volatile, since "answer" is always read after a reading the volatile variable
    // "completionStatus" - see the extended comment below.


    Evaluation(Task<V> task) {
        this.task = task;
    }

    // Carry out evaluation.
    void runComputation() {
        answer = task.compute();
        completionStatus = true;
    }

    // Checks whether the computation is complete.
    boolean isComplete() {
        return completionStatus;
    }

    // Retrieves answer - should only be called *after* calling isComplete(), and verifying that isComplete() is true.
    V getAnswer() {
        return answer;
    }


    /*
     Note about happens-before relationships:
     (1) Actions done by the forking thread prior to the forking *happen-before* the evaluation of this Evaluation job
         by the evaluating thread.
         [This is because the forking thread adds the Evaluation job to its ConcurrentLinkedDeque of tasks during
         the fork. The evaluating thread then gets/steals the job from this ConcurrentLinkedDeque, before starting to
         run the computation. According to the docs for the java.util.concurrent package, all concurrent containers
         are implemented in such a way that there is a happens-before relationship between adding an object to the
         container and retrieving the same object from the container.]

     (2) The evaluation of this job and the recording of the answer by the evaluating thread *happens-before*
         the joining of this job by the thread doing the join.
         [This is because the evaluating thread performs the computation and writes the answer before writing to
         the volatile variable completionStatus; the joining thread then reads this volatile variable verifying that
         the evaluation is complete before retrieving the answer.]
     */
}

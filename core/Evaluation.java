package core;

// An *async* evaluation of a task, triggered by forking.
// (Since we allow a task to be forked multiple times, it is possible for multiple Evaluation objects to be created from
//  a single task.)
class Evaluation<V> {

    // A reference to the task to be evaluated.
    private final Task<V> task;

    // Status of computation - will become true once the evaluation is complete;
    // also involved in memory synchronisation between thread caches.
    private volatile boolean completionStatus = false;

    // Result of evaluation - will be initialised once the evaluation is complete.
    private V answer;
    // (NB no need to mark "answer" as volatile, since "answer" is always read after a reading
    // the volatile variable "completionStatus" - see the extended comment below.)

    // Constructor.
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

     (1) The forking of the task by the forking thread *happens-before* the evaluation of this Evaluation job
         by the evaluating thread.

         [To spell it out:
         - forking thread entering its fork call
             ... happens-before ...        [due to program order within a single thread)
         - forking thread adding the newly-created Evaluation object to the ConcurrentLinkedDeque inside the EvalQueue
             ... happens-before ...        [see documentation for the containers in the java.util.concurrent package]
         - evaluating thread retrieves the Evaluation object from the ConcurrentLinkedDeque from within the EvalQueue
             ... happens-before ...        [due to program oder within a single thread]
         - evaluating thread entering the call of runComputation
         ]


     (2) The evaluation of this job by the evaluating thread and the recording of the answer *happen-before*
         the joining of this task by the thread doing the join.

         [To spell it out:
         - evaluating thread writing the result of the computation to this.answer
             ... happens-before ...        [due to program order within a single thread]
         - evaluating thread writing this.completionStatus = true
             ... happens-before ...        [due to this.completionStatus being volatile]
         - joining thread reading this.completionStatus
             ... happens-before ...        [due to program order within a single thread]
         - joining thread reading the value of this.answer
             ... happens-before ...        [due to program order within a single thread]
         - joining thread exits its join call
         ]
      */

}

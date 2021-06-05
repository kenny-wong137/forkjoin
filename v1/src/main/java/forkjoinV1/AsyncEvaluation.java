package forkjoinV1;

/**
 * An *asynchronous* evaluation of a task, triggered by forking, and to be evaluated in a fork-join pool.
 */
class AsyncEvaluation<V> {

    // A reference to the task to be evaluated.
    private final Task<V> task;

    // A reference to the fork-join pool used for this evaluation (only used for validation purposes).
    private final Pool poolUsed;

    // Status of computation - will become true once the evaluation is complete/
    // This volatile variable is also used to ensure memory consistency between threads.
    private volatile boolean completionStatus = false;

    // Result of evaluation - will be initialised once the evaluation is complete.
    private V answer;
    // (NB no need to mark answer as volatile, since answer is always read after a reading
    // the volatile variable completionStatus - see the extended comment below.)

    AsyncEvaluation(Task<V> task, Pool poolUsed) {
        this.task = task;
        this.poolUsed = poolUsed;
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

    Pool getPoolUsed() {
        return poolUsed;
    }

    /*
      Note about happens-before relationships:
     (1) The forking of the task by the forking thread *happens-before* the evaluation of this AsyncEvaluation job
         by the evaluating thread.
         [To spell it out:
         - forking thread entering its fork call
             ... happens-before ...    [due to program order within single thread - see task.fork() implementation]
         - forking thread adding the new AsyncEvaluation object to the ConcurrentLinkedDeque inside the AsyncEvalQueue
             ... happens-before ...    [see documentation for the containers in the java.util.concurrent package]
         - evaluating thread getting the AsyncEvaluation object from the ConcurrentLinkedDeque from the AsyncEvalQueue
             ... happens-before ...    [due to program order within single thread - see worker.run() or task.join()]
         - evaluating thread entering the call of runComputation
         ]
     (2) The evaluation of this job by the evaluating thread and the recording of the result in this.answer
         *happen-before* the joining of this task by the thread doing the join.
         [To spell it out:
         - evaluating thread writing the result of the computation to this.answer
             ... happens-before ...    [due to program order within single thread - see evaluation.runComputation()]
         - evaluating thread writing this.completionStatus = true
             ... happens-before ...    [due to this.completionStatus being volatile]
         - joining thread reading this.completionStatus
             ... happens-before ...    [due to program order within single thread - see task.join()]
         - joining thread reading the value of this.answer
             ... happens-before ...    [due to program order within single thread - see task.join()]
         - joining thread exits its join call
         ]
      */

}

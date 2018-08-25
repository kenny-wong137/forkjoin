package core;

// i.e. an *async* evaluation of a task, triggered by forking
class Evaluation<V> {

    private Task<V> task;
    private volatile Boolean completionStatus; // null: not started; false: in progress; true: complete
    private V answer;

    Evaluation(Task<V> task) {
        this.task = task;
        this.completionStatus = null;
    }

    void runComputation() {
        if (completionStatus == null) {
            completionStatus = false;
            answer = task.compute();
            completionStatus = true;
        }
    }

    boolean isComplete() {
        return (completionStatus != null) && completionStatus;
    }

    V getAnswer() {
        return answer;
    }

    /*
     Note about happens-before relationships:
     (1) Stuff that happens on the thread that forked this Evaluation job prior to forking *happens-before*
         the evaluation of this Evaluation job by the worker thread doing the evaluation.
         This is because the forking thread writes to the volatile variable completionStatus in the constructor,
         when forking, and then the evaluating thread reads the completionStatus before starting the evaluation.

     (2) The evaluation of this job and the recording of the answer by the evaluating thread *happens-before*
         the joining of this job by the thread doing the join.
         This is because the evaluating thread does the computation and writes the answer before writing to
         the volatile variable completionStatus, and this in turn precedes the joining thread verifying that
         the evaluation is complete by reading completionStatus and retrieving the answer.
     */
}

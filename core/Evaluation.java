package core;

// i.e. an *async* evaluation of a task, triggered by forking
class Evaluation<V> {

    private Task<V> task;
    private volatile boolean completionStatus = false;
    private volatile V answer;

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
}

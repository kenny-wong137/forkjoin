package examples;

import core.Pool;
import core.Task;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IncrementEach {

    private static class NumberContainer {

        private int value = 0;

        private void increment() {
            value++;
        }

        private int getValue() {
            return value;
        }

    }

    private static class IncrementTask extends Task<Void> {

        private List<NumberContainer> list;
        private int MIN_SPLIT_SIZE = 100000;

        IncrementTask(List<NumberContainer> list) {
            this.list = list;
        }

        @Override
        public Void compute() {

            System.out.println(Thread.currentThread().getName() + " : list size " + list.size());

            if (list.size() == 0) {
                return null;
            }

            if (list.size() > MIN_SPLIT_SIZE) {
                IncrementTask leftSubtask = new IncrementTask(list.subList(0, list.size() / 2));
                IncrementTask rightSubtask = new IncrementTask(list.subList(list.size() / 2, list.size()));
                rightSubtask.fork();
                leftSubtask.compute();
                rightSubtask.join();
                return null;
            }
            else {
                list.forEach(NumberContainer::increment);
                return null;
            }
        }
    }

    public static void main(String[] args) {

        List<NumberContainer> myList = IntStream.range(0, 10000000)
                .boxed()
                .map(i -> new NumberContainer())
                .collect(Collectors.toList());

        IncrementTask fullTask = new IncrementTask(myList);

        Pool pool = new Pool();

        try {
            int numIters = 10;
            for (int i = 0; i < numIters; i++) {
                System.out.println("Iteration: " + i);
                long startTime = System.currentTimeMillis();
                pool.invoke(fullTask);
                long endTime = System.currentTimeMillis();
                System.out.println("Time: " + (endTime - startTime));
                System.out.println("");
            }

            boolean allCorrect = true;
            for (NumberContainer container : myList) {
                if (container.getValue() != numIters) {
                    System.out.println("Problem: found " + container.getValue());
                    allCorrect = false;
                }
            }
            if (allCorrect) {
                System.out.println("All correct");
            }
        }
        finally {
            pool.terminate();
        }
    }

}

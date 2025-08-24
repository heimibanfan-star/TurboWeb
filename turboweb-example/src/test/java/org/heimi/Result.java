package org.heimi;

/**
 * TODO
 */
public class Result <T> {

    private final T data;

    public Result(T data) {
        this.data = data;
    }

    public T getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Result{" +
                "data=" + data +
                '}';
    }
}

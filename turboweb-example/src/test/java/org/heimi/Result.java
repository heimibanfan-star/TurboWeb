package org.heimi;

/**
 * TODO
 */
public class Result <T> {

    private T data;

    public void setData(T data) {
        this.data = data;
    }

    public Result(T data) {
        this.data = data;
    }

    public Result() {
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

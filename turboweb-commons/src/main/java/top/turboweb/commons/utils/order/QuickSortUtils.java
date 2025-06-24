package top.turboweb.commons.utils.order;

/**
 * 基于快速排序算法的排序工具类
 */
public class QuickSortUtils {

    /**
     * 对数组进行排序
     *
     * @param arr 数组
     * @param <T> 只能对实现了Order接口的类进行排序
     */
    public static <T extends Order> void sort(T[] arr) {
        if (arr == null || arr.length <= 1) return;
        quickSort(arr, 0, arr.length - 1);
    }

    private static <T extends Order> void quickSort(T[] arr, int low, int high) {
        if (low < high) {
            int pivot = partition(arr, low, high);
            quickSort(arr, low, pivot - 1);
            quickSort(arr, pivot + 1, high);
        }
    }

    private static <T extends Order> int partition(T[] arr, int low, int high) {
        T pivot = arr[high];
        int i = low;
        for (int j = low; j < high; j++) {
            if (arr[j].order() <= pivot.order()) {
                swap(arr, i++, j);
            }
        }
        swap(arr, i, high);
        return i;
    }

    private static <T> void swap(T[] arr, int i, int j) {
        T tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }
}

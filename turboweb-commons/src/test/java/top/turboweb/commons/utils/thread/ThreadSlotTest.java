package top.turboweb.commons.utils.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F16：{@code ThreadSlot} 只有一个私有 volatile 字段、零方法、零调用方（ThreadSlot.java:6-9）。
 *
 * <p>这条用例唯一的作用是给门禁分母上那个"隐式默认构造器"1 行一个合法归宿：该类没有任何可断言
 * 的行为，构造非空就是它的全部语义。真正的处置是阶段 5 里删掉这个类（连同分母里那 1 行），
 * 届时本用例一并删除——不要给它加字段访问器来"让测试更有意义"。
 */
class ThreadSlotTest {

    @Test
    @DisplayName("钉住 F16：空壳类无任何成员可测（阶段 5 连同本用例一并删除）")
    void hasNoMembersToExercise() {
        assertNotNull(new ThreadSlot());
    }
}

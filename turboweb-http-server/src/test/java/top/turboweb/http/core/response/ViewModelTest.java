package top.turboweb.http.core.response;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link ViewModel} 的视图名与属性读写语义（spec §5：response）。
 */
class ViewModelTest {

    @Test
    void viewNameDefaultsToNullAndRoundTrips() {
        ViewModel model = new ViewModel();
        assertNull(model.getViewName());
        model.setViewName("index");
        assertEquals("index", model.getViewName());
        model.setViewName(null);
        assertNull(model.getViewName());
    }

    @Test
    void attributesAreMutableAndLastWriteWins() {
        ViewModel model = new ViewModel();
        assertNull(model.getAttribute("missing"));
        model.addAttribute("k", "v1");
        assertEquals("v1", model.getAttribute("k"));
        model.addAttribute("k", "v2");
        assertEquals("v2", model.getAttribute("k"), "同名属性后写覆盖先写");
        assertEquals(1, model.getAttributes().size());
    }

    @Test
    void setAttributesReplacesTheWholeMap() {
        ViewModel model = new ViewModel();
        model.addAttribute("keep", 1);
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("only", 2);
        model.setAttributes(replacement);
        assertNull(model.getAttribute("keep"), "setAttributes 是整体替换，不是合并");
        assertEquals(2, model.getAttribute("only"));
        assertSame(replacement, model.getAttributes(), "持有的就是传入的 map 本身");
    }
}

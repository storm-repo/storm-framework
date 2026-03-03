package st.orm.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotationsTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface TestAnnotation {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface RepeatableItems {
        RepeatableItem[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(RepeatableItems.class)
    @interface RepeatableItem {
        String value();
    }

    @TestAnnotation("test")
    record AnnotatedRecord(int id) {}

    @RepeatableItem("first")
    @RepeatableItem("second")
    record MultiAnnotatedRecord(int value) {}

    @RepeatableItem("single")
    record SingleRepeatableRecord(int value) {}

    @Test
    void isAnnotationPresentReturnsTrueForDirectAnnotation() {
        List<Annotation> annotations = List.of(AnnotatedRecord.class.getAnnotations());
        assertTrue(Annotations.isAnnotationPresent(annotations, TestAnnotation.class));
    }

    @Test
    void isAnnotationPresentReturnsFalseWhenAbsent() {
        List<Annotation> annotations = List.of();
        assertFalse(Annotations.isAnnotationPresent(annotations, TestAnnotation.class));
    }

    @Test
    void getAnnotationReturnsSingleAnnotation() {
        List<Annotation> annotations = List.of(AnnotatedRecord.class.getAnnotations());
        TestAnnotation result = Annotations.getAnnotation(annotations, TestAnnotation.class);
        assertNotNull(result);
        assertEquals("test", result.value());
    }

    @Test
    void getAnnotationReturnsNullWhenAbsent() {
        List<Annotation> annotations = List.of();
        TestAnnotation result = Annotations.getAnnotation(annotations, TestAnnotation.class);
        assertNull(result);
    }

    @Test
    void getAnnotationsReturnsEmptyArrayWhenAbsent() {
        List<Annotation> annotations = List.of();
        TestAnnotation[] results = Annotations.getAnnotations(annotations, TestAnnotation.class);
        assertNotNull(results);
        assertEquals(0, results.length);
    }

    @Test
    void getAnnotationsReturnsDirectAnnotations() {
        List<Annotation> annotations = List.of(AnnotatedRecord.class.getAnnotations());
        TestAnnotation[] results = Annotations.getAnnotations(annotations, TestAnnotation.class);
        assertEquals(1, results.length);
    }

    @Test
    void getAnnotationsHandlesRepeatableAnnotations() {
        List<Annotation> annotations = List.of(MultiAnnotatedRecord.class.getAnnotations());
        RepeatableItem[] results = Annotations.getAnnotations(annotations, RepeatableItem.class);
        assertEquals(2, results.length);
        assertEquals("first", results[0].value());
        assertEquals("second", results[1].value());
    }

    @Test
    void getAnnotationReturnsNullForMultipleRepeatableAnnotations() {
        List<Annotation> annotations = List.of(MultiAnnotatedRecord.class.getAnnotations());
        RepeatableItem result = Annotations.getAnnotation(annotations, RepeatableItem.class);
        assertNull(result);
    }

    @Test
    void isAnnotationPresentReturnsFalseForMultipleRepeatableAnnotations() {
        // isAnnotationPresent delegates to getAnnotation, which returns null when multiple are found
        List<Annotation> annotations = List.of(MultiAnnotatedRecord.class.getAnnotations());
        assertFalse(Annotations.isAnnotationPresent(annotations, RepeatableItem.class));
    }

    @Test
    void getAnnotationReturnsSingleRepeatableAnnotation() {
        List<Annotation> annotations = List.of(SingleRepeatableRecord.class.getAnnotations());
        RepeatableItem result = Annotations.getAnnotation(annotations, RepeatableItem.class);
        assertNotNull(result);
        assertEquals("single", result.value());
    }

    @Test
    void getAnnotationsForNonRepeatableReturnsEmpty() {
        List<Annotation> annotations = List.of();
        TestAnnotation[] results = Annotations.getAnnotations(annotations, TestAnnotation.class);
        assertEquals(0, results.length);
    }
}

package st.orm;

import jakarta.annotation.Nonnull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class MetamodelHelper {

    private static final Method ROOT_METHOD;
    private static final Method OF_METHOD;

    static {
        try {
            Class<?> factoryClass = Class.forName("st.orm.core.template.impl.MetamodelFactory");
            ROOT_METHOD = factoryClass.getMethod("root", Class.class);
            OF_METHOD = factoryClass.getMethod("of", Class.class, String.class);
        } catch (ReflectiveOperationException e) {
            var ex = new ExceptionInInitializerError("Failed to initialize Metamodel. Please ensure that storm-core is present in the classpath.");
            ex.initCause(e);
            throw ex;
        }
    }

    private MetamodelHelper() {
        // Prevent instantiation
    }

    @SuppressWarnings("unchecked")
    static <T extends Data> Metamodel<T, T> root(@Nonnull Class<T> rootTable) {
        try {
            try {
                return (Metamodel<T, T>) ROOT_METHOD.invoke(null, rootTable);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Reflection invocation failed for MetamodelFactory.of", e);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Data, E> Metamodel<T, E> of(Class<T> rootTable, String path) {
        try {
            try {
                return (Metamodel<T, E>) OF_METHOD.invoke(null, rootTable, path);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Reflection invocation failed for MetamodelFactory.of", e);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }
}

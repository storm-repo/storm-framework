package st.orm.template.model;

/**
 * Simple domain object representing a person.
 */
public interface Person {

    default String name() {
        return firstName() + " " + lastName();
    }

    String firstName();

    String lastName();
}

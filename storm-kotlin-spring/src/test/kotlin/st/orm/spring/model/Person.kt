package st.orm.spring.model

/**
 * Simple domain object representing a person.
 */
interface Person {
    val name: String
        get() = "$firstName $lastName"

    val firstName: String

    val lastName: String
}
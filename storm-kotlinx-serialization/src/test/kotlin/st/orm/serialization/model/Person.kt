package st.orm.serialization.model

/**
 * Simple domain object representing a person.
 */
internal interface Person {
    val name: String
        get() = "$firstName $lastName"

    val firstName: String

    val lastName: String
}

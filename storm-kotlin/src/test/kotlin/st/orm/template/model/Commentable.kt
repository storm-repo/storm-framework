package st.orm.template.model

import st.orm.Data
import st.orm.DbTable
import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Ref

internal sealed interface Commentable : Data

@DbTable("post")
internal data class Post(
    @PK val id: Int = 0,
    val title: String,
) : Commentable,
    Entity<Int>

@DbTable("photo")
internal data class Photo(
    @PK val id: Int = 0,
    val url: String,
) : Commentable,
    Entity<Int>

@DbTable("comment")
internal data class Comment(
    @PK val id: Int = 0,
    val text: String,
    @FK val target: Ref<Commentable>,
) : Entity<Int>

package st.orm.template.model

import st.orm.Data
import st.orm.DbTable
import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Ref

sealed interface Commentable : Data

@DbTable("post")
data class Post(
    @PK val id: Int = 0,
    val title: String,
) : Commentable,
    Entity<Int>

@DbTable("photo")
data class Photo(
    @PK val id: Int = 0,
    val url: String,
) : Commentable,
    Entity<Int>

@DbTable("comment")
data class Comment(
    @PK val id: Int = 0,
    val text: String,
    @FK val target: Ref<Commentable>,
) : Entity<Int>

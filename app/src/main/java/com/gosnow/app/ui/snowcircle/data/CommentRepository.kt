package com.gosnow.app.ui.snowcircle.data

import com.gosnow.app.ui.snowcircle.model.Comment
import com.gosnow.app.ui.snowcircle.model.User

interface CommentRepository {
    suspend fun getCommentsForPost(postId: String): List<Comment>
    suspend fun addComment(postId: String, body: String, parentId: String?, currentUser: User): Comment
    suspend fun deleteComment(commentId: String, currentUserId: String)
    suspend fun toggleCommentLike(commentId: String, currentUserId: String): Comment?
}

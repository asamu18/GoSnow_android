package com.gosnow.app.ui.snowcircle.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gosnow.app.ui.snowcircle.model.Comment
import com.gosnow.app.util.getResizedImageUrl

@Composable
fun CommentRow(
    comment: Comment,
    onReply: (Comment) -> Unit,
    onLike: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onReport: (Comment) -> Unit,
    modifier: Modifier = Modifier
) {
    // ✅ 修改：为了避免命名冲突，直接在这里获取 LocalContext.current
    // 或者将变量名改为 ctx/localContext，这里直接使用 LocalContext.current 传给 Coil
    val localContext = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onReply(comment) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 头像
        AsyncImage(
            // ✅ 修复：这里使用 localContext，避免编译器混淆
            model = ImageRequest.Builder(localContext)
                .data(getResizedImageUrl(comment.author.avatarUrl, width = 80) ?: comment.author.avatarUrl)
                .crossfade(true)
                .placeholder(android.R.color.darker_gray)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))

                val expanded = remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded.value = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expanded.value,
                        onDismissRequest = { expanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("举报") },
                            onClick = {
                                expanded.value = false
                                onReport(comment)
                            }
                        )
                        if (comment.canDelete) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    expanded.value = false
                                    onDelete(comment)
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = comment.createdAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "回复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onReply(comment) }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLike(comment) }
                ) {
                    Icon(
                        imageVector = if (comment.isLikedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        tint = if (comment.isLikedByMe)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${comment.likeCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
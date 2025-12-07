package com.gosnow.app.ui.snowcircle.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.gosnow.app.ui.snowcircle.model.Post
import com.gosnow.app.ui.snowcircle.model.User
import com.gosnow.app.ui.snowcircle.ui.components.PostCard
import com.gosnow.app.ui.snowcircle.ui.theme.SnowTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("雪圈 / Snow Circle") },
                navigationIcon = {
                    Box {
                        IconButton(onClick = {
                            viewModel.onNotificationsViewed()
                            navController.navigate("notifications")
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notifications"
                            )
                        }
                        if (uiState.hasUnreadNotifications) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("my_posts") }) {
                        Icon(Icons.Outlined.Article, contentDescription = "My posts")
                    }
                    IconButton(onClick = { navController.navigate("compose_post") }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "New post")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("compose_post") }) {
                Icon(Icons.Filled.Add, contentDescription = "New post")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "雪圈",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SearchBar(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                onClear = { viewModel.onQueryChange("") }
            )
            if (uiState.query.isNotBlank() && uiState.selectedResort == null) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    uiState.resortSuggestions.forEach { resort ->
                        AssistChip(
                            onClick = { viewModel.onResortSelected(resort) },
                            label = { Text(resort) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
            uiState.selectedResort?.let { SelectedResortCard(resort = it, onClear = { viewModel.onResortSelected(null) }) }
            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.errorMessage != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.errorMessage ?: "Error")
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
                uiState.posts.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val msg = if (uiState.selectedResort == null) "暂无帖子 / No posts yet" else "This resort has no posts yet, go post one!"
                    Text(msg)
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onClick = { navController.navigate("post_detail/${post.id}") },
                            onToggleLike = { viewModel.onToggleLike(post.id) },
                            onComment = { navController.navigate("post_detail/${post.id}") },
                            onImageClick = { index -> navController.navigate("image_viewer/${post.id}/$index") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("Search posts or resorts") },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            }
        }
    )
}

@Composable
private fun SelectedResortCard(resort: String, onClear: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Selected resort", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(resort, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { /* TODO: open resort detail */ }) { Text("View resort details") }
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear resort")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFeed() {
    SnowTheme {
        val dummyPosts = listOf(
            Post("1", User("1", "Ada", null), "Niseko", "1h ago", "Powder day!", emptyList(), 3, 1, false),
            Post("2", User("2", "Ben", null), null, "3h ago", "Looking for buddies", listOf("https://picsum.photos/200"), 2, 0, false)
        )
        FeedScreen(
            viewModel = FakeFeedViewModel(dummyPosts),
            navController = rememberNavController()
        )
    }
}

private class FakeFeedViewModel(private val posts: List<Post>) : FeedViewModel(
    postRepository = object : com.gosnow.app.ui.snowcircle.data.PostRepository {
        override suspend fun getFeedPosts(resortFilter: String?) = posts
        override suspend fun getMyPosts(currentUserId: String) = emptyList<Post>()
        override suspend fun getPostById(postId: String) = posts.firstOrNull()
        override suspend fun toggleLike(postId: String, currentUserId: String) = posts.firstOrNull()
        override suspend fun deletePost(postId: String, currentUserId: String) {}
        override suspend fun createPost(content: String, resortName: String?, images: List<String>, currentUser: User) = posts.first()
    },
    notificationsRepository = object : com.gosnow.app.ui.snowcircle.data.NotificationsRepository {
        override suspend fun getNotifications(currentUserId: String) = emptyList<com.gosnow.app.ui.snowcircle.model.NotificationItem>()
        override suspend fun markAllRead(currentUserId: String) {}
        override suspend fun markRead(notificationId: Long) {}
    },
    currentUserId = ""
) {
    init {
        onQueryChange("")
        onResortSelected(null)
    }

    override fun refresh() {
        // no-op for preview
    }
}

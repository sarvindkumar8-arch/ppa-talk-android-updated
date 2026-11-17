package com.ppatalk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DateFormat
import java.util.Date

private val Navy = Color(0xFF123A8C)
private val Orange = Color(0xFFFF8500)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PPATalkApp() }
    }
}

@Composable
fun PPATalkApp() {
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Orange)) {
        if (user == null) {
            AuthScreen(onAuthenticated = { user = FirebaseAuth.getInstance().currentUser })
        } else {
            MainSocialApp(
                uid = user!!.uid,
                email = user?.email.orEmpty(),
                displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email?.substringBefore("@").orEmpty(),
                onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    user = null
                }
            )
        }
    }
}

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var isSignUp by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PPA Talk", style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold, color = Navy)
        Text("Create. Connect. Share.", color = Orange)
        Spacer(Modifier.height(28.dp))
        Text(if (isSignUp) "Create your account" else "Welcome back",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (isSignUp) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                message = ""
                if (email.isBlank() || password.length < 6 || (isSignUp && name.isBlank())) {
                    message = "Enter valid details. Password must be at least 6 characters."
                    return@Button
                }
                busy = true
                val auth = FirebaseAuth.getInstance()
                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            val firebaseUser = result.user
                            val profileName = name.trim()
                            firebaseUser?.updateProfile(
                                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(profileName).build()
                            )?.addOnCompleteListener {
                                if (firebaseUser != null) {
                                    val profile = hashMapOf(
                                        "uid" to firebaseUser.uid,
                                        "displayName" to profileName,
                                        "email" to email.trim(),
                                        "visibility" to "public",
                                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    )
                                    FirebaseFirestore.getInstance().collection("users")
                                        .document(firebaseUser.uid).set(profile)
                                        .addOnCompleteListener { task ->
                                            busy = false
                                            if (task.isSuccessful) onAuthenticated()
                                            else message = task.exception?.localizedMessage ?: "Profile setup failed."
                                        }
                                } else {
                                    busy = false
                                    message = "Account created, but profile setup failed."
                                }
                            }
                        }
                        .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Sign up failed." }
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { busy = false; onAuthenticated() }
                        .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Login failed." }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait..." else if (isSignUp) "Sign Up" else "Login")
        }
        TextButton(onClick = { isSignUp = !isSignUp; message = "" }) {
            Text(if (isSignUp) "Already have an account? Login" else "New here? Create account")
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text("Public community • Be respectful and stay safe",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSocialApp(uid: String, email: String, displayName: String, onSignOut: () -> Unit) {
    var selected by remember { mutableStateOf("Home") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PPA Talk", color = Navy, fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onSignOut) {
                    Icon(Icons.Default.Logout, contentDescription = "Sign out")
                } }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    "Home" to Icons.Default.Home,
                    "Search" to Icons.Default.Search,
                    "Create" to Icons.Default.AddCircle,
                    "Notifications" to Icons.Default.Notifications,
                    "Profile" to Icons.Default.Person
                ).forEach { (label, icon) ->
                    NavigationBarItem(
                        selected = selected == label,
                        onClick = { selected = label },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            "Home" -> HomeFeed(Modifier.padding(padding))
            "Profile" -> SimplePage("Public profile", "$displayName\n$email", Modifier.padding(padding))
            "Search" -> SimplePage("Discover", "Find people and public posts", Modifier.padding(padding))
            "Create" -> CreatePostScreen(uid, displayName, email, Modifier.padding(padding))
            else -> SimplePage("Notifications", "Your activity will appear here", Modifier.padding(padding))
        }
    }
}

data class FeedPost(
    val id: String,
    val authorName: String,
    val authorEmail: String,
    val text: String,
    val createdAt: Date?
)

@Composable
fun HomeFeed(modifier: Modifier = Modifier) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val registration = FirebaseFirestore.getInstance().collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage ?: "Could not load posts. Check Firestore rules."
                    loading = false
                } else if (snapshot != null) {
                    posts = snapshot.documents.map { doc ->
                        FeedPost(
                            id = doc.id,
                            authorName = doc.getString("authorName") ?: "PPA Talk user",
                            authorEmail = doc.getString("authorEmail") ?: "",
                            text = doc.getString("text") ?: "",
                            createdAt = doc.getTimestamp("createdAt")?.toDate()
                        )
                    }
                    loading = false
                    error = ""
                }
            }
        onDispose { registration.remove() }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Your Feed", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Navy)
            Text("Latest public posts from the PPA Talk community", color = Color.Gray)
        }
        if (loading) item { CircularProgressIndicator() }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error.isBlank()) {
            item { Text("No posts yet. Be the first to share something!") }
        }
        items(posts, key = { it.id }) { post ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Navy)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(post.authorName, fontWeight = FontWeight.SemiBold)
                            if (post.createdAt != null) {
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(post.createdAt),
                                    style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(post.text)
                }
            }
        }
    }
}

@Composable
fun CreatePostScreen(uid: String, displayName: String, email: String, modifier: Modifier = Modifier) {
    var postText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Create a post", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Navy)
        Text("Share something with the PPA Talk community.", color = Color.Gray)
        OutlinedTextField(
            value = postText,
            onValueChange = { postText = it; success = false; message = "" },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            label = { Text("What's on your mind?") },
            placeholder = { Text("Write your post...") }
        )
        Button(
            enabled = !busy && postText.isNotBlank() && postText.trim().length <= 5000,
            onClick = {
                busy = true
                message = ""
                val post = hashMapOf(
                    "uid" to uid,
                    "authorName" to displayName,
                    "authorEmail" to email,
                    "text" to postText.trim(),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                FirebaseFirestore.getInstance().collection("posts").add(post)
                    .addOnSuccessListener {
                        busy = false
                        postText = ""
                        success = true
                        message = "Your post was published!"
                    }
                    .addOnFailureListener {
                        busy = false
                        message = it.localizedMessage ?: "Post failed. Check Firestore rules and connection."
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Publishing..." else "Publish Post") }
        if (postText.trim().length > 5000) Text("Post must be 5,000 characters or less.", color = MaterialTheme.colorScheme.error)
        if (message.isNotBlank()) Text(message, color = if (success) Color(0xFF16803C) else MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SimplePage(title: String, detail: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Navy)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = Color.Gray)
        }
    }
}

package com.ppatalk.app

import android.os.Bundle
import android.app.Activity
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

private val DarkNavy = Color(0xFF081426)
private val SurfaceNavy = Color(0xFF11243D)
private val ElectricBlue = Color(0xFF168BFF)
private val NeonCyan = Color(0xFF00D5FF)
private val Orange = Color(0xFFFF7A1A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PPATalkApp() }
    }
}

@Composable
fun PPATalkApp() {
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = ElectricBlue, onPrimary = Color.White,
        secondary = Orange, onSecondary = Color.White,
        tertiary = NeonCyan, onTertiary = DarkNavy,
        background = DarkNavy, onBackground = Color.White,
        surface = SurfaceNavy, onSurface = Color.White,
        surfaceVariant = Color(0xFF1A3452), onSurfaceVariant = Color(0xFFD5E5F5),
        outline = NeonCyan, error = Color(0xFFFF6B6B)
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkNavy) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var isSignUp by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var usePhoneLogin by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+91") }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    val countryCodes = listOf(
        "India (+91)" to "+91", "United States (+1)" to "+1", "United Kingdom (+44)" to "+44",
        "United Arab Emirates (+971)" to "+971", "Canada (+1)" to "+1", "Australia (+61)" to "+61",
        "Saudi Arabia (+966)" to "+966", "Pakistan (+92)" to "+92", "Bangladesh (+880)" to "+880",
        "Nepal (+977)" to "+977", "Sri Lanka (+94)" to "+94", "Singapore (+65)" to "+65",
        "Malaysia (+60)" to "+60", "Germany (+49)" to "+49", "France (+33)" to "+33",
        "South Africa (+27)" to "+27", "New Zealand (+64)" to "+64", "Japan (+81)" to "+81",
        "China (+86)" to "+86", "Qatar (+974)" to "+974", "Kuwait (+965)" to "+965",
        "Oman (+968)" to "+968"
    )
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    val googleSignInClient = remember(context) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .build()
        GoogleSignIn.getClient(context, options)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    busy = false
                    message = "Google sign-in token missing. Check Firebase OAuth setup."
                } else {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnSuccessListener { authResult ->
                            val firebaseUser = authResult.user
                            if (firebaseUser == null) {
                                busy = false
                                message = "Google login succeeded but user details were unavailable."
                            } else {
                                val profile = hashMapOf(
                                    "uid" to firebaseUser.uid,
                                    "displayName" to (firebaseUser.displayName ?: account.displayName ?: "PPA Talk user"),
                                    "email" to (firebaseUser.email ?: account.email.orEmpty()),
                                    "photoUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                FirebaseFirestore.getInstance().collection("users")
                                    .document(firebaseUser.uid)
                                    .set(profile, com.google.firebase.firestore.SetOptions.merge())
                                    .addOnSuccessListener {
                                        busy = false
                                        onAuthenticated()
                                    }
                                    .addOnFailureListener { exception ->
                                        busy = false
                                        message = exception.localizedMessage ?: "Could not save Google profile."
                                    }
                            }
                        }
                        .addOnFailureListener { exception ->
                            busy = false
                            message = exception.localizedMessage ?: "Google authentication failed."
                        }
                }
            } catch (exception: ApiException) {
                busy = false
                message = exception.localizedMessage ?: "Google sign-in was cancelled or failed."
            }
        } else {
            busy = false
            message = "Google sign-in cancelled."
        }
    }

    val authFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.LightGray,
        cursorColor = NeonCyan,
        focusedBorderColor = ElectricBlue,
        unfocusedBorderColor = NeonCyan,
        focusedLabelColor = NeonCyan,
        unfocusedLabelColor = Color.LightGray,
        focusedContainerColor = SurfaceNavy,
        unfocusedContainerColor = SurfaceNavy
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PPA Talk", style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold, color = ElectricBlue)
        Text("Create. Connect. Share.", color = Orange)
        Spacer(Modifier.height(28.dp))
        Text(if (isSignUp) "Create your account" else "Welcome back",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (isSignUp) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = authFieldColors)
            Spacer(Modifier.height(8.dp))
        }
        if (!isSignUp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !usePhoneLogin, onClick = {
                    usePhoneLogin = false; verificationId = null; otp = ""; message = ""
                }, label = { Text("Email") })
                FilterChip(selected = usePhoneLogin, onClick = {
                    usePhoneLogin = true; verificationId = null; otp = ""; message = ""
                }, label = { Text("Mobile") })
            }
        }
        if (isSignUp || !usePhoneLogin) {
            OutlinedTextField(email, { email = it }, label = { Text("Email address") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = authFieldColors)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authFieldColors)
        } else {
            ExposedDropdownMenuBox(
                expanded = countryMenuExpanded,
                onExpandedChange = { countryMenuExpanded = !countryMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = countryCodes.firstOrNull { it.second == countryCode }?.first ?: "Country code ($countryCode)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Country code") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true,
                    colors = authFieldColors
                )
                ExposedDropdownMenu(
                    expanded = countryMenuExpanded,
                    onDismissRequest = { countryMenuExpanded = false }
                ) {
                    countryCodes.forEach { (country, code) ->
                        DropdownMenuItem(
                            text = { Text(country) },
                            onClick = { countryCode = code; countryMenuExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(phoneNumber, { phoneNumber = it.filter(Char::isDigit) }, label = { Text("Mobile number") },
                placeholder = { Text("Enter mobile number") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = authFieldColors)
            if (verificationId != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit OTP") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = authFieldColors)
            }
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                message = ""
                val auth = FirebaseAuth.getInstance()
                if (isSignUp && (email.isBlank() || password.length < 6 || name.isBlank())) {
                    message = "Enter valid name, email and password (minimum 6 characters)."
                    return@Button
                }
                if (!isSignUp && usePhoneLogin && verificationId == null) {
                    val fullPhoneNumber = countryCode + phoneNumber.filter(Char::isDigit)
                    if (phoneNumber.filter(Char::isDigit).length !in 6..14 ||
                        !fullPhoneNumber.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
                        message = "Enter a valid mobile number for the selected country."
                        return@Button
                    }
                    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                            auth.signInWithCredential(credential)
                                .addOnSuccessListener { busy = false; onAuthenticated() }
                                .addOnFailureListener { exception -> busy = false; message = exception.localizedMessage ?: "Mobile login failed." }
                        }
                        override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                            busy = false
                            message = exception.localizedMessage ?: "Could not send OTP. Check Firebase Phone sign-in setup."
                        }
                        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                            verificationId = id
                            busy = false
                            message = "OTP sent. Enter the 6-digit code received on your mobile."
                        }
                    }
                    busy = true
                    val options = PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(fullPhoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(context as Activity)
                        .setCallbacks(callbacks)
                        .build()
                    PhoneAuthProvider.verifyPhoneNumber(options)
                    return@Button
                }
                if (!isSignUp && usePhoneLogin && verificationId != null) {
                    if (otp.length != 6) {
                        message = "Enter the 6-digit OTP."
                        return@Button
                    }
                    busy = true
                    val credential = PhoneAuthProvider.getCredential(verificationId!!, otp)
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { busy = false; onAuthenticated() }
                        .addOnFailureListener { exception -> busy = false; message = exception.localizedMessage ?: "Invalid OTP. Try again." }
                    return@Button
                }
                if (!isSignUp && !usePhoneLogin && (email.isBlank() || password.length < 6)) {
                    message = "Enter a valid email and password (minimum 6 characters)."
                    return@Button
                }
                busy = true
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
            Text(if (busy) "Please wait..." else if (isSignUp) "Sign Up" else if (usePhoneLogin && verificationId != null) "Verify OTP" else if (usePhoneLogin) "Send OTP" else "Login")
        }
        OutlinedButton(
            onClick = {
                message = ""
                busy = true
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait..." else "Continue with Google")
        }
        TextButton(onClick = { isSignUp = !isSignUp; message = "" }) {
            Text(if (isSignUp) "Already have an account? Login" else "New here? Create account")
        }
        if (!isSignUp && !usePhoneLogin) {
            TextButton(
                enabled = !busy,
                onClick = {
                    message = ""
                    if (email.isBlank()) {
                        message = "Enter your email address first."
                    } else {
                        busy = true
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim())
                            .addOnSuccessListener {
                                busy = false
                                message = "Password reset link sent to your email. Check your inbox and spam folder."
                            }
                            .addOnFailureListener { exception ->
                                busy = false
                                message = exception.localizedMessage ?: "Could not send password reset email."
                            }
                    }
                }
            ) {
                Text("Forgot password?")
            }
        }
        if (message.isNotBlank()) Text(message, color = if (message.startsWith("Password reset link sent")) NeonCyan else MaterialTheme.colorScheme.error)
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
        containerColor = DarkNavy,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceNavy,
                    titleContentColor = ElectricBlue,
                    actionIconContentColor = NeonCyan
                ),
                title = {
                    Column {
                        Text("PPA Talk", color = ElectricBlue, fontWeight = FontWeight.ExtraBold)
                        Text("Create. Connect. Share.", color = Orange, style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { selected = "Search" }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceNavy, contentColor = NeonCyan) {
                listOf(
                    "Home" to Icons.Default.Home,
                    "Friends" to Icons.Default.PeopleAlt,
                    "Reels" to Icons.Default.PlayCircle,
                    "Notifications" to Icons.Default.Notifications,
                    "Profile" to Icons.Default.Person
                ).forEach { (label, icon) ->
                    NavigationBarItem(
                        selected = selected == label,
                        onClick = { selected = label },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            indicatorColor = ElectricBlue.copy(alpha = 0.20f),
                            unselectedIconColor = Color.LightGray,
                            unselectedTextColor = Color.LightGray
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            "Home" -> HomeFeed(uid, Modifier.padding(padding)) { selected = "Create" }
            "Profile" -> ProfileScreen(uid = uid, email = email, initialName = displayName, modifier = Modifier.padding(padding))
            "Search" -> SimplePage("Search", "Find people and public posts", Modifier.padding(padding))
            "Create" -> CreatePostScreen(uid, displayName, email, Modifier.padding(padding))
            "Friends" -> SimplePage("Friends", "Friend requests and your friends will appear here.", Modifier.padding(padding))
            "Reels" -> SimplePage("Reels", "Short videos from the PPA Talk community.", Modifier.padding(padding))
            else -> SimplePage("Notifications", "Your activity will appear here", Modifier.padding(padding))
        }
    }
}

data class FeedPost(val id: String, val uid: String, val authorName: String, val authorEmail: String, val text: String, val createdAt: Date?)
data class FeedComment(val id: String, val uid: String, val authorName: String, val text: String, val parentId: String, val createdAt: Date?)

@Composable
fun HomeFeed(currentUid: String, modifier: Modifier = Modifier, onCreatePost: () -> Unit = {}) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<FeedPost?>(null) }
    DisposableEffect(Unit) {
        val registration = FirebaseFirestore.getInstance().collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage ?: "Could not load posts. Check Firestore rules."
                    loading = false
                } else if (snapshot != null) {
                    posts = snapshot.documents.map { doc ->
                        FeedPost(doc.id, doc.getString("uid") ?: "",
                            doc.getString("authorName") ?: "PPA Talk user",
                            doc.getString("authorEmail") ?: "", doc.getString("text") ?: "",
                            doc.getTimestamp("createdAt")?.toDate())
                    }
                    loading = false
                    error = ""
                }
            }
        onDispose { registration.remove() }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HomeComposerCard(onCreatePost = onCreatePost)
        }
        item {
            StoriesStrip()
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("News Feed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ElectricBlue)
                Spacer(Modifier.weight(1f))
                Text("For You", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (loading) item { CircularProgressIndicator() }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error.isBlank()) item { Text("No posts yet. Be the first to share something!") }
        items(posts, key = { it.id }) { post ->
            FeedPostCard(post, currentUid) { selectedPost = post }
        }
    }
    selectedPost?.let { PostCommentsDialog(it, currentUid) { selectedPost = null } }
}


@Composable
fun HomeComposerCard(onCreatePost: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceNavy)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = DarkNavy,
                    onClick = onCreatePost
                ) {
                    Text("What's on your mind?", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Color.LightGray)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCreatePost) {
                    Icon(Icons.Default.Photo, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(5.dp))
                    Text("Photo")
                }
                TextButton(onClick = onCreatePost) {
                    Icon(Icons.Default.VideoCall, contentDescription = null, tint = Orange)
                    Spacer(Modifier.width(5.dp))
                    Text("Video")
                }
                TextButton(onClick = onCreatePost) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ElectricBlue)
                    Spacer(Modifier.width(5.dp))
                    Text("Write")
                }
            }
        }
    }
}

@Composable
fun StoriesStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f).height(104.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceNavy)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                    Text("Create story", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            listOf("Friends", "Community", "Explore").forEachIndexed { index, title ->
                Card(
                    modifier = Modifier.weight(1f).height(104.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = listOf(ElectricBlue.copy(alpha = 0.28f), Orange.copy(alpha = 0.24f), NeonCyan.copy(alpha = 0.18f))[index]
                    )
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                        Text(title, modifier = Modifier.padding(8.dp), color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedPostCard(post: FeedPost, currentUid: String, onCommentsClick: () -> Unit) {
    var liked by remember(post.id, currentUid) { mutableStateOf(false) }
    var likeCount by remember(post.id) { mutableIntStateOf(0) }
    var commentCount by remember(post.id) { mutableIntStateOf(0) }
    var actionBusy by remember(post.id) { mutableStateOf(false) }
    val db = remember { FirebaseFirestore.getInstance() }
    DisposableEffect(post.id, currentUid) {
        val likesRef = db.collection("posts").document(post.id).collection("likes")
        val likeListener = likesRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                likeCount = snapshot.size()
                liked = snapshot.documents.any { it.id == currentUid }
            }
        }
        val commentsListener = db.collection("posts").document(post.id).collection("comments")
            .addSnapshotListener { snapshot, _ -> if (snapshot != null) commentCount = snapshot.size() }
        onDispose { likeListener.remove(); commentsListener.remove() }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceNavy, contentColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = ElectricBlue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorName, fontWeight = FontWeight.SemiBold)
                    if (post.createdAt != null) Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(post.createdAt),
                        style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(post.text)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !actionBusy, onClick = {
                    actionBusy = true
                    val ref = db.collection("posts").document(post.id).collection("likes").document(currentUid)
                    if (liked) ref.delete().addOnCompleteListener { actionBusy = false }
                    else ref.set(mapOf("uid" to currentUid, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                        .addOnCompleteListener { actionBusy = false }
                }) {
                    Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like", tint = if (liked) Orange else NeonCyan)
                    Spacer(Modifier.width(5.dp)); Text(if (liked) "Liked" else "Like"); Spacer(Modifier.width(5.dp)); Text(likeCount.toString())
                }
                TextButton(onClick = onCommentsClick) {
                    Icon(Icons.Default.ModeComment, contentDescription = "Comments", tint = NeonCyan)
                    Spacer(Modifier.width(5.dp)); Text("Comment"); Spacer(Modifier.width(5.dp)); Text(commentCount.toString())
                }
            }
        }
    }
}

@Composable
fun PostCommentsDialog(post: FeedPost, currentUid: String, onDismiss: () -> Unit) {
    var comments by remember(post.id) { mutableStateOf<List<FeedComment>>(emptyList()) }
    var commentText by remember(post.id) { mutableStateOf("") }
    var replyTo by remember(post.id) { mutableStateOf<FeedComment?>(null) }
    var sending by remember(post.id) { mutableStateOf(false) }
    var message by remember(post.id) { mutableStateOf("") }
    var currentName by remember(currentUid) { mutableStateOf("PPA Talk user") }
    val db = remember { FirebaseFirestore.getInstance() }
    DisposableEffect(post.id) {
        val registration = db.collection("posts").document(post.id).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) comments = snapshot.documents.map { doc ->
                    FeedComment(doc.id, doc.getString("uid") ?: "", doc.getString("authorName") ?: "PPA Talk user",
                        doc.getString("text") ?: "", doc.getString("parentId") ?: "", doc.getTimestamp("createdAt")?.toDate())
                }
            }
        onDispose { registration.remove() }
    }
    LaunchedEffect(currentUid) {
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener { currentName = it.getString("displayName") ?: currentName }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                Text(post.text, style = MaterialTheme.typography.bodySmall, color = Color.LightGray, maxLines = 3)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val topLevel = comments.filter { it.parentId.isBlank() }
                    if (topLevel.isEmpty()) item { Text("No comments yet. Start the conversation!") }
                    items(topLevel, key = { it.id }) { comment ->
                        Column(Modifier.fillMaxWidth()) {
                            Card(colors = CardDefaults.cardColors(containerColor = SurfaceNavy)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(comment.authorName, fontWeight = FontWeight.Bold, color = NeonCyan)
                                    Text(comment.text)
                                    TextButton(onClick = { replyTo = comment }) { Text("Reply") }
                                }
                            }
                            comments.filter { it.parentId == comment.id }.forEach { reply ->
                                Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 5.dp)) {
                                    Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = Orange,
                                        modifier = Modifier.padding(end = 6.dp))
                                    Column {
                                        Text(reply.authorName, fontWeight = FontWeight.SemiBold, color = NeonCyan)
                                        Text(reply.text)
                                    }
                                }
                            }
                        }
                    }
                }
                if (replyTo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to " + replyTo!!.authorName, modifier = Modifier.weight(1f),
                            color = Orange, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { replyTo = null }) { Text("Cancel") }
                    }
                }
                OutlinedTextField(value = commentText, onValueChange = { commentText = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text(if (replyTo == null) "Write a comment" else "Write a reply") }, maxLines = 3)
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !sending && commentText.isNotBlank() && commentText.trim().length <= 1000, onClick = {
                sending = true; message = ""
                val comment = hashMapOf("uid" to currentUid, "authorName" to currentName,
                    "text" to commentText.trim(), "parentId" to (replyTo?.id ?: ""),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                db.collection("posts").document(post.id).collection("comments").add(comment)
                    .addOnSuccessListener { commentText = ""; replyTo = null; sending = false }
                    .addOnFailureListener { exception ->
                        sending = false; message = exception.localizedMessage ?: "Comment/reply failed. Check Firestore rules."
                    }
            }) { Text(if (sending) "Sending..." else "Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun CreatePostScreen(uid: String, displayName: String, email: String, modifier: Modifier = Modifier) {
    var postText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Create a post", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = ElectricBlue)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(uid: String, email: String, initialName: String, modifier: Modifier = Modifier) {
    var displayName by remember { mutableStateOf(initialName) }
    var bio by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var hobby by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    var favoritePlace by remember { mutableStateOf("") }
    var hometown by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var coverPhotoUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        val registration = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    message = error.localizedMessage ?: "Profile load nahi ho paya."
                    isError = true
                    loading = false
                } else if (snapshot != null) {
                    displayName = snapshot.getString("displayName") ?: initialName
                    bio = snapshot.getString("bio") ?: ""
                    dateOfBirth = snapshot.getString("dateOfBirth") ?: ""
                    gender = snapshot.getString("gender") ?: ""
                    phoneNumber = snapshot.getString("phoneNumber") ?: ""
                    hobby = snapshot.getString("hobby") ?: ""
                    school = snapshot.getString("school") ?: ""
                    favoritePlace = snapshot.getString("favoritePlace") ?: ""
                    hometown = snapshot.getString("hometown") ?: ""
                    photoUrl = snapshot.getString("photoUrl") ?: ""
                    coverPhotoUrl = snapshot.getString("coverPhotoUrl") ?: ""
                    loading = false
                }
            }
        onDispose { registration.remove() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            saving = true
            message = "Profile photo upload ho rahi hai..."
            isError = false
            val imageRef = FirebaseStorage.getInstance().reference.child("profile_photos/$uid/profile.jpg")
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                            .set(mapOf("photoUrl" to downloadUri.toString()), com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                saving = false
                                photoUrl = downloadUri.toString()
                                message = "Profile photo update ho gayi."
                            }
                            .addOnFailureListener { exception ->
                                saving = false
                                isError = true
                                message = exception.localizedMessage ?: "Photo URL save nahi hua."
                            }
                    }.addOnFailureListener { exception ->
                        saving = false
                        isError = true
                        message = exception.localizedMessage ?: "Photo URL nahi mil paya."
                    }
                }
                .addOnFailureListener { exception ->
                    saving = false
                    isError = true
                    message = exception.localizedMessage ?: "Photo upload failed. Firebase Storage rules check karein."
                }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            saving = true
            message = "Cover photo upload ho rahi hai..."
            isError = false
            val imageRef = FirebaseStorage.getInstance().reference.child("cover_photos/$uid/cover.jpg")
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                            .set(mapOf("coverPhotoUrl" to downloadUri.toString()), com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                saving = false
                                coverPhotoUrl = downloadUri.toString()
                                message = "Cover photo update ho gayi."
                            }
                            .addOnFailureListener { exception ->
                                saving = false
                                isError = true
                                message = exception.localizedMessage ?: "Cover photo URL save nahi hua."
                            }
                    }.addOnFailureListener { exception ->
                        saving = false
                        isError = true
                        message = exception.localizedMessage ?: "Cover photo URL nahi mil paya."
                    }
                }
                .addOnFailureListener { exception ->
                    saving = false
                    isError = true
                    message = exception.localizedMessage ?: "Cover upload failed. Firebase Storage rules check karein."
                }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("My Profile", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = ElectricBlue,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp))

        // Facebook-style cover banner with an upload/change control.
        Box(
            modifier = Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = SurfaceNavy)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (coverPhotoUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverPhotoUrl,
                            contentDescription = "Profile cover photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Photo, contentDescription = null,
                                tint = NeonCyan, modifier = Modifier.size(38.dp))
                            Text("Your cover photo", color = Color.LightGray)
                        }
                    }
                }
            }
            FilledIconButton(
                onClick = { coverPicker.launch("image/*") },
                enabled = !saving,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = ElectricBlue, contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Change cover photo")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(model = photoUrl, contentDescription = "Profile photo",
                        modifier = Modifier.size(104.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profile photo placeholder",
                        modifier = Modifier.size(104.dp), tint = NeonCyan)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName.ifBlank { "PPA Talk user" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text("PPA Talk member", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !saving) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit profile photo")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (loading) CircularProgressIndicator()
            OutlinedTextField(value = displayName, onValueChange = { displayName = it },
                label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = email, onValueChange = {}, readOnly = true,
                label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") },
                placeholder = { Text("Tell people about yourself") }, modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 4)
            Text("Personal details", style = MaterialTheme.typography.titleMedium,
                color = Orange, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = dateOfBirth, onValueChange = { dateOfBirth = it },
                label = { Text("Date of birth") }, placeholder = { Text("DD/MM/YYYY") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            var genderMenuExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = genderMenuExpanded,
                onExpandedChange = { genderMenuExpanded = !genderMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = gender, onValueChange = {}, readOnly = true,
                    label = { Text("Gender") }, placeholder = { Text("Select gender") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
                )
                ExposedDropdownMenu(expanded = genderMenuExpanded,
                    onDismissRequest = { genderMenuExpanded = false }) {
                    listOf("Male", "Female", "Other", "Prefer not to say").forEach { option ->
                        DropdownMenuItem(text = { Text(option) },
                            onClick = { gender = option; genderMenuExpanded = false })
                    }
                }
            }
            OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it },
                label = { Text("Phone number") }, placeholder = { Text("Enter phone number") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(value = hobby, onValueChange = { hobby = it },
                label = { Text("Hobby") }, placeholder = { Text("Your hobbies") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = school, onValueChange = { school = it },
                label = { Text("School / College") }, placeholder = { Text("School or college name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = favoritePlace, onValueChange = { favoritePlace = it },
                label = { Text("Favorite place") }, placeholder = { Text("Your favorite place") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = hometown, onValueChange = { hometown = it },
                label = { Text("Hometown") }, placeholder = { Text("Your hometown") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Email is linked to your login account. Avoid sharing private details publicly.",
                style = MaterialTheme.typography.bodySmall, color = Color.LightGray,
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val cleanName = displayName.trim()
                    if (cleanName.isBlank()) {
                        message = "Display name khali nahi ho sakta."
                        isError = true
                    } else {
                        saving = true
                        message = ""
                        isError = false
                        val updates = mapOf(
                            "displayName" to cleanName,
                            "bio" to bio.trim(),
                            "dateOfBirth" to dateOfBirth.trim(),
                            "gender" to gender,
                            "phoneNumber" to phoneNumber.trim(),
                            "hobby" to hobby.trim(),
                            "school" to school.trim(),
                            "favoritePlace" to favoritePlace.trim(),
                            "hometown" to hometown.trim(),
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                FirebaseAuth.getInstance().currentUser
                                    ?.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(cleanName).build())
                                saving = false
                                message = "Profile save ho gayi."
                            }
                            .addOnFailureListener { exception ->
                                saving = false
                                isError = true
                                message = exception.localizedMessage ?: "Profile save nahi hui."
                            }
                    }
                },
                enabled = !saving && !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saving) "Saving..." else "Save Profile") }
            if (message.isNotBlank()) {
                Text(message, color = if (isError) MaterialTheme.colorScheme.error else NeonCyan)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SimplePage(title: String, detail: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = ElectricBlue)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = Color.Gray)
        }
    }
}

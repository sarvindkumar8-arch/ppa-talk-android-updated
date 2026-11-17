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

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var isSignUp by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var usePhoneLogin by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
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
            OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text("Mobile number") },
                placeholder = { Text("+91XXXXXXXXXX") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
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
                    if (!phoneNumber.trim().matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
                        message = "Enter mobile number with country code, e.g. +919876543210."
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
                        .setPhoneNumber(phoneNumber.trim())
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceNavy, titleContentColor = ElectricBlue, actionIconContentColor = NeonCyan),
                title = { Text("PPA Talk", color = ElectricBlue, fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onSignOut) {
                    Icon(Icons.Default.Logout, contentDescription = "Sign out")
                } }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceNavy, contentColor = NeonCyan) {
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
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonCyan, selectedTextColor = NeonCyan, indicatorColor = ElectricBlue.copy(alpha = 0.20f), unselectedIconColor = Color.LightGray, unselectedTextColor = Color.LightGray)
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            "Home" -> HomeFeed(Modifier.padding(padding))
            "Profile" -> ProfileScreen(uid = uid, email = email, initialName = displayName, modifier = Modifier.padding(padding))
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
                fontWeight = FontWeight.Bold, color = ElectricBlue)
            Text("Latest public posts from the PPA Talk community", color = Color.Gray)
        }
        if (loading) item { CircularProgressIndicator() }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error.isBlank()) {
            item { Text("No posts yet. Be the first to share something!") }
        }
        items(posts, key = { it.id }) { post ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceNavy, contentColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = ElectricBlue)
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

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("My Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ElectricBlue)
        Box(contentAlignment = Alignment.Center) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(model = photoUrl, contentDescription = "Profile photo",
                    modifier = Modifier.size(116.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.AccountCircle, contentDescription = "Profile photo placeholder",
                    modifier = Modifier.size(116.dp), tint = NeonCyan)
            }
        }
        OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !saving) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Change profile photo")
        }
        if (loading) CircularProgressIndicator()
        OutlinedTextField(value = displayName, onValueChange = { displayName = it },
            label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = email, onValueChange = {}, readOnly = true,
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") },
            placeholder = { Text("Tell people about yourself") }, modifier = Modifier.fillMaxWidth(),
            minLines = 3, maxLines = 4)
        Text("Personal details", style = MaterialTheme.typography.titleMedium,
            color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
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
                value = gender,
                onValueChange = {},
                readOnly = true,
                label = { Text("Gender") },
                placeholder = { Text("Select gender") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = genderMenuExpanded,
                onDismissRequest = { genderMenuExpanded = false }
            ) {
                listOf("Male", "Female", "Other", "Prefer not to say").forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { gender = option; genderMenuExpanded = false }
                    )
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

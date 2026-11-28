// PPA Talk launcher branding: supplied logo icon and white-orange-yellow slogan.
package com.ppatalk.app

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.app.Activity
import android.net.Uri
import android.content.ContentUris
import android.provider.MediaStore
import android.Manifest
import android.media.MediaPlayer
import android.content.Intent
import android.widget.Toast
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
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
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

private val RoyalBlue = Color(0xFF2457D6)
private val Orange = Color(0xFFF27A21)

private data class ProfileGalleryItem(
    val id: String, val url: String, val thumbnailUrl: String,
    val type: String, val category: String, val caption: String,
    val securePath: String? = null
)

private data class DeviceGalleryMedia(
    val uri: Uri,
    val type: String,
    val dateAdded: Long,
    val durationMs: Long = 0L
)

private fun loadDeviceGallery(context: android.content.Context): List<DeviceGalleryMedia> {
    val result = mutableListOf<DeviceGalleryMedia>()
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Video.VideoColumns.DURATION
    )
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val args = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )
    context.contentResolver.query(collection, projection, selection, args,
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val kind = if (cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) "video" else "image"
            val uri = ContentUris.withAppendedId(collection, id)
            val duration = if (kind == "video" && durationCol >= 0 && !cursor.isNull(durationCol)) cursor.getLong(durationCol) else 0L
            result.add(DeviceGalleryMedia(uri, kind, cursor.getLong(dateCol), duration))
        }
    }
    return result
}

private fun firebaseStorageObjectPath(value: String): String? {
    if (value.startsWith("profile_photos/") || value.startsWith("cover_photos/")) return value
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    val segments = uri.pathSegments
    val objectIndex = segments.indexOf("o")
    if (objectIndex < 0 || objectIndex + 1 >= segments.size) return null
    return runCatching { Uri.decode(segments[objectIndex + 1]) }.getOrNull()
}

@Composable
private fun rememberSecureMediaUrl(
    mediaType: String,
    documentId: String,
    storagePath: String,
    legacyUrl: String
): String {
    val legacyObjectPath = remember(legacyUrl) { firebaseStorageObjectPath(legacyUrl).orEmpty() }
    val legacyProtectedMedia = remember(legacyObjectPath) {
        legacyObjectPath.startsWith("post_photos/") ||
            legacyObjectPath.startsWith("stories/") ||
            legacyObjectPath.startsWith("reels/")
    }
    var resolvedUrl by remember(mediaType, documentId, storagePath, legacyUrl) {
        mutableStateOf(if (storagePath.isBlank() && !legacyProtectedMedia) legacyUrl else "")
    }
    LaunchedEffect(mediaType, documentId, storagePath, legacyUrl, legacyProtectedMedia) {
        if (documentId.isBlank() || storagePath.isBlank()) {
            // Never expose legacy Firebase content download-token URLs. The owner migration
            // must backfill storagePath before this media can be loaded through a signed URL.
            resolvedUrl = if (legacyProtectedMedia) "" else legacyUrl
        } else {
            // Never fall back to a token URL when authorization fails for a path-backed record.
            resolvedUrl = "";
            val authUser = FirebaseAuth.getInstance().currentUser
            if (authUser == null) {
                resolvedUrl = "MEDIA_ERROR:No signed-in Firebase user."
            } else {
                // Force refresh before callable invocation to ensure the callable receives a current ID token.
                authUser.getIdToken(true)
                    .addOnSuccessListener {
                        FirebaseFunctions.getInstance("asia-south1")
                            .getHttpsCallable("getSecureMediaUrl")
                            .call(mapOf(
                                "mediaType" to mediaType,
                                "documentId" to documentId,
                                "objectPath" to storagePath
                            ))
                            .addOnSuccessListener { result ->
                                val data = result.getData() as? Map<*, *>
                                val secureUrl = data?.get("url") as? String
                                if (!secureUrl.isNullOrBlank()) resolvedUrl = secureUrl
                                else resolvedUrl = "MEDIA_ERROR:Function returned an empty URL."
                            }
                            .addOnFailureListener { ex ->
                                android.util.Log.e("PpaSecureMedia", "Callable failed type=$mediaType post=$documentId path=$storagePath", ex)
                                resolvedUrl = "MEDIA_ERROR:" + (ex.localizedMessage ?: ex.toString())
                            }
                    }
                    .addOnFailureListener { ex ->
                        android.util.Log.e("PpaSecureMedia", "ID token refresh failed", ex)
                        resolvedUrl = "MEDIA_ERROR:Token refresh failed: " + (ex.localizedMessage ?: ex.toString())
                    }
            }
        }
    }
    return resolvedUrl
}

class MainActivity : ComponentActivity() {
    private fun createIncomingCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val channel = NotificationChannel(
                "ppa_incoming_calls_v2",
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ringtone and alerts for incoming PPA Talk calls"
                setSound(ringtone, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(NotificationChannel(
                "ppa_general_notifications",
                "PPA Talk notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Friend requests and social activity"
                enableVibration(true)
            })
            manager.createNotificationChannel(NotificationChannel(
                "ppa_chat_messages",
                "Chat messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New messages from your PPA Talk friends"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Install App Check before any Auth, Firestore, Storage, or Functions calls.
        val appCheckFactory = if (BuildConfig.DEBUG) {
            // Keep the debug-only provider out of release compilation and packaging.
            Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                .getMethod("getInstance")
                .invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory)
        createIncomingCallNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
        setContent { PPATalkApp() }
    }
}

@Composable
fun PPATalkApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("ppa_talk_preferences", android.content.Context.MODE_PRIVATE) }
    var isDarkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember {
        mutableStateOf(auth.currentUser?.takeIf {
            it.phoneNumber != null || it.providerData.any { provider -> provider.providerId == "phone" } || it.isEmailVerified
        })
    }

    DisposableEffect(auth) {
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val current = firebaseAuth.currentUser
            user = current?.takeIf {
                it.phoneNumber != null || it.providerData.any { provider -> provider.providerId == "phone" } || it.isEmailVerified
            }
        }
        auth.addAuthStateListener(authListener)
        onDispose {
            auth.removeAuthStateListener(authListener)
        }
    }
    val lightColors = lightColorScheme(
        primary = RoyalBlue, onPrimary = Color.White,
        secondary = Orange, onSecondary = Color.White,
        tertiary = Color(0xFF345FCB), onTertiary = Color.White,
        background = Color(0xFFF3F5F9), onBackground = Color(0xFF172033),
        surface = Color.White, onSurface = Color(0xFF172033),
        surfaceVariant = Color(0xFFE8ECF3), onSurfaceVariant = Color(0xFF667085),
        outline = Color(0xFFD5DCE7), error = Color(0xFFB3261E)
    )
    val darkColors = darkColorScheme(
        primary = Color(0xFF8AA8FF), onPrimary = Color(0xFF10265C),
        secondary = Color(0xFFFFA45C), onSecondary = Color(0xFF4A2100),
        tertiary = Color(0xFFB8C8FF), onTertiary = Color(0xFF17264A),
        background = Color(0xFF101827), onBackground = Color(0xFFF3F5FA),
        surface = Color(0xFF1B2638), onSurface = Color(0xFFF3F5FA),
        surfaceVariant = Color(0xFF29364B), onSurfaceVariant = Color(0xFFB8C2D3),
        outline = Color(0xFF46546B), error = Color(0xFFFFB4AB)
    )
    MaterialTheme(colorScheme = if (isDarkMode) darkColors else lightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (user == null) {
                var authEntry by remember { mutableStateOf("splash") }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1400)
                    authEntry = "welcome"
                }
                when (authEntry) {
                    "splash" -> PpaConnectSplash()
                    "welcome" -> PpaConnectWelcome(onLogin = { authEntry = "login" }, onCreate = { authEntry = "signup" })
                    else -> AuthScreen(initialIsSignUp = authEntry == "signup", onBackToWelcome = { authEntry = "welcome" }, onAuthenticated = { user = FirebaseAuth.getInstance().currentUser })
                }
            } else {
                MainSocialApp(
                    uid = user!!.uid,
                    email = user?.email.orEmpty(),
                    displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email?.substringBefore("@").orEmpty(),
                    isDarkMode = isDarkMode,
                    onThemeModeChange = { enabled -> isDarkMode = enabled; preferences.edit().putBoolean("dark_mode", enabled).apply() },
                    onSignOut = {
                        FirebaseAuth.getInstance().signOut()
                        user = null
                    }
                )
            }
        }
    }
}


private fun finishPhoneSignup(
    auth: FirebaseAuth,
    credential: com.google.firebase.auth.PhoneAuthCredential,
    phoneNumber: String,
    password: String,
    displayName: String,
    onResult: (Boolean, String) -> Unit
) {
    auth.signInWithCredential(credential)
        .addOnSuccessListener {
            val payload = hashMapOf(
                "phoneNumber" to phoneNumber,
                "password" to password,
                "displayName" to displayName
            )
            FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("registerWithPhonePassword").call(payload)
                .addOnSuccessListener { result ->
                    val token = (result.getData() as? Map<*, *>)?.get("token") as? String
                    if (token.isNullOrBlank()) onResult(false, "Authentication server returned an invalid response.")
                    else auth.signInWithCustomToken(token)
                        .addOnSuccessListener { onResult(true, "") }
                        .addOnFailureListener { onResult(false, it.localizedMessage ?: "Mobile account sign-in failed.") }
                }
                .addOnFailureListener { onResult(false, it.localizedMessage ?: "Mobile account registration failed.") }
        }
        .addOnFailureListener { onResult(false, it.localizedMessage ?: "OTP verification failed.") }
}

@Composable
private fun PpaConnectSplash() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(
            id = R.drawable.ppa_splash_art
        ),
        contentDescription = "PPA CONNECT splash screen",
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
    )
}

@Composable
private fun PpaConnectWelcome(onLogin: () -> Unit, onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF123B78)))
                    append("PPA ")
                    pop()
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Orange))
                    append("CONNECT")
                    pop()
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            Text(
                "Create. Connect. Share.",
                color = Color(0xFF182238),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            Spacer(Modifier.height(4.dp))

            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ppa_welcome_community),
                contentDescription = "Friends sharing moments on PPA CONNECT",
                modifier = Modifier.fillMaxWidth().height(290.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )

            Text(
                "Connect with friends, share your moments and be part of our community.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color(0xFF172033),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0878F9),
                    contentColor = Color.White
                )
            ) {
                Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }

            OutlinedButton(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(2.dp, Orange),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF172033)
                )
            ) {
                Text("Create Account", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(initialIsSignUp: Boolean = false, onBackToWelcome: () -> Unit = {}, onAuthenticated: () -> Unit) {
    var isSignUp by remember { mutableStateOf(initialIsSignUp) }
    var usePhone by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+91") }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var phoneVerificationId by remember { mutableStateOf<String?>(null) }
    var phoneOtp by remember { mutableStateOf("") }
    var phoneOtpSent by remember { mutableStateOf(false) }
    var phoneResendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var phoneResetMode by remember { mutableStateOf(false) }
    var resetOtpSent by remember { mutableStateOf(false) }
    var resetPhoneVerified by remember { mutableStateOf(false) }
    var resetVerificationId by remember { mutableStateOf<String?>(null) }
    var resetOtp by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }
    var resetConfirmPassword by remember { mutableStateOf("") }
    var resetResendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keep an unverified email account signed in while the user opens the
    // verification email. When the app resumes, refresh Firebase Auth and
    // automatically enter PPA Talk once Firebase confirms the email.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val auth = FirebaseAuth.getInstance()
                val pendingUser = auth.currentUser
                if (pendingUser?.email != null && !pendingUser.isEmailVerified) {
                    pendingUser.reload().addOnSuccessListener {
                        val verifiedUser = auth.currentUser
                        if (verifiedUser?.isEmailVerified == true) {
                            val db = FirebaseFirestore.getInstance()
                            val profileName = verifiedUser.displayName?.takeIf { it.isNotBlank() }
                                ?: verifiedUser.email?.substringBefore("@") ?: "PPA Talk user"
                            val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                            val batch = db.batch()
                            batch.set(db.collection("users").document(verifiedUser.uid), mapOf(
                                "uid" to verifiedUser.uid,
                                "email" to (verifiedUser.email ?: ""),
                                "updatedAt" to timestamp
                            ), com.google.firebase.firestore.SetOptions.merge())
                            batch.set(db.collection("publicProfiles").document(verifiedUser.uid), mapOf(
                                "uid" to verifiedUser.uid,
                                "displayName" to profileName,
                                "updatedAt" to timestamp
                            ), com.google.firebase.firestore.SetOptions.merge())
                            batch.commit().addOnSuccessListener { onAuthenticated() }
                                .addOnFailureListener { exception ->
                                    message = exception.localizedMessage ?: "Email verified, but profile setup failed. Please sign in again."
                                }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                                // Do not block successful Firebase Authentication on a profile
                                // Firestore write. MainSocialApp safely initializes profile data
                                // after entering the app; legacy profile documents may need migration.
                                busy = false
                                onAuthenticated()
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

    val authNavy = Color(0xFF07182D)
    val authField = Color(0xFF102A46)
    val authText = Color(0xFFF3F7FC)
    val authMuted = Color(0xFFB8C7D9)
    val authCyan = Color(0xFF22D3EE)
    // Unified dark PPA CONNECT theme for both Sign In and Create Account screens.
    val authFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = authText, unfocusedTextColor = authText,
        focusedPlaceholderColor = authMuted, unfocusedPlaceholderColor = authMuted,
        disabledTextColor = authMuted, cursorColor = authCyan,
        focusedBorderColor = authCyan, unfocusedBorderColor = Color(0xFF54718D),
        focusedLabelColor = authCyan, unfocusedLabelColor = authMuted,
        focusedContainerColor = authField, unfocusedContainerColor = authField
    )

    Column(
        modifier = Modifier.fillMaxSize()
            .background(authNavy)
            .padding(horizontal = 22.dp, vertical = 18.dp).imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isSignUp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBackToWelcome, enabled = !busy) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = authCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Back", color = authCyan)
                }
                Spacer(Modifier.weight(1f))
            }
            Box(Modifier.size(66.dp).background(RoyalBlue, CircleShape), contentAlignment = Alignment.Center) {
                Text("P", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }
        if (!isSignUp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PPA ", style = MaterialTheme.typography.headlineMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("CONNECT", style = MaterialTheme.typography.headlineMedium.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFD21F), Color(0xFFFF8A00)))
                ), fontWeight = FontWeight.ExtraBold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PPA ", style = MaterialTheme.typography.headlineMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("CONNECT", style = MaterialTheme.typography.headlineMedium.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFD21F), Color(0xFFFF8A00)))
                ), fontWeight = FontWeight.ExtraBold)
            }
        }
        Text("Create. Connect. Share.", style = MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(if (!isSignUp) 12.dp else 26.dp))
        Text(if (isSignUp) "Create Account" else "Sign In",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(if (isSignUp) "Join PPA CONNECT in a few steps." else "Welcome back! Sign in to continue.",
            style = MaterialTheme.typography.bodyMedium, color = authMuted)
        Spacer(Modifier.height(if (!isSignUp) 14.dp else 16.dp))

        if (isSignUp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !usePhone, onClick = { usePhone = false; message = "" },
                    label = { Text("✉ Email", color = Color.White) }, modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(containerColor = authField, selectedContainerColor = RoyalBlue, labelColor = Color.White, selectedLabelColor = Color.White))
                FilterChip(selected = usePhone, onClick = { usePhone = true; message = "" },
                    label = { Text("☎ Phone", color = Color.White) }, modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(containerColor = authField, selectedContainerColor = RoyalBlue, labelColor = Color.White, selectedLabelColor = Color.White))
            }
        }
        Spacer(Modifier.height(if (!isSignUp) 4.dp else 10.dp))

        if (isSignUp) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") }, placeholder = { Text("Enter your full name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = authText),
                shape = RoundedCornerShape(14.dp), colors = authFieldColors
            )
            Spacer(Modifier.height(8.dp))
        }

        if (usePhone) {
            ExposedDropdownMenuBox(
                expanded = countryMenuExpanded,
                onExpandedChange = { countryMenuExpanded = !countryMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = countryCodes.firstOrNull { it.second == countryCode }?.first ?: "Country code ($countryCode)",
                    onValueChange = {}, readOnly = true, label = { Text("Country code") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true, colors = authFieldColors
                )
                ExposedDropdownMenu(expanded = countryMenuExpanded,
                    onDismissRequest = { countryMenuExpanded = false }) {
                    countryCodes.forEach { (country, code) ->
                        DropdownMenuItem(text = { Text(country) },
                            onClick = { countryCode = code; countryMenuExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter(Char::isDigit).take(14) },
                label = { Text("Mobile number") }, placeholder = { Text("Enter mobile number") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(color = authText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = authFieldColors
            )
        } else {
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text(if (!isSignUp && !usePhone) "Email or Username" else "Email Address") }, placeholder = { Text(if (!isSignUp && !usePhone) "Email or Username" else "you@example.com") }, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = authMuted) },
                singleLine = true, shape = RoundedCornerShape(if (!isSignUp) 24.dp else 14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = authFieldColors
            )
        }
        Spacer(Modifier.height(8.dp))
        if (usePhone && isSignUp && phoneOtpSent) {
            Spacer(Modifier.height(8.dp))
            Text("Enter the 6-digit SMS code sent to your mobile number", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = phoneOtp, onValueChange = { phoneOtp = it.filter(Char::isDigit).take(6) },
                label = { Text("SMS OTP") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = authFieldColors
            )
        }
        // For mobile signup, reveal password only after the OTP has been sent.
        // Mobile login and email signup/login continue to show password normally.
        if (!phoneResetMode && (!usePhone || !isSignUp || phoneOtpSent)) {
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(if (isSignUp) "Password" else "Password") },
            placeholder = { Text(if (isSignUp) "Create a password (8+ characters)" else "Enter your password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(if (!isSignUp) 24.dp else 14.dp), textStyle = LocalTextStyle.current.copy(color = authText),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = authMuted) },
            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showPassword) "Hide password" else "Show password", tint = authMuted) } },
            visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = authFieldColors
        )
        }
        if (phoneResetMode && resetOtpSent) {
            OutlinedTextField(
                value = resetOtp, onValueChange = { resetOtp = it.filter(Char::isDigit).take(6) },
                label = { Text("SMS OTP") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = authFieldColors
            )
            OutlinedTextField(
                value = resetPassword, onValueChange = { resetPassword = it },
                label = { Text("New password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authFieldColors
            )
            OutlinedTextField(
                value = resetConfirmPassword, onValueChange = { resetConfirmPassword = it },
                label = { Text("Confirm new password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authFieldColors
            )
        }
        if (!isSignUp && !usePhone && !phoneResetMode) {
            TextButton(
                enabled = !busy,
                onClick = {
                    message = ""
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        message = "Enter your email address first."
                    } else {
                        busy = true
                        authSendPasswordReset(email.trim()) { success, detail ->
                            busy = false
                            message = detail
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
            ) { Text("Forgot Password?", color = authCyan) }
        }
        Spacer(Modifier.height(if (!isSignUp) 10.dp else 14.dp))

        if (phoneResetMode) {
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    message = ""
                    val fullPhone = countryCode + phoneNumber.filter(Char::isDigit)
                    if (!fullPhone.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
                        message = "Enter a valid mobile number."
                        return@Button
                    }
                    if (!resetOtpSent) {
                        val activity = context as? Activity
                        if (activity == null) {
                            message = "Unable to start phone verification on this screen."
                            return@Button
                        }
                        busy = true
                        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                                resetVerificationId = null
                                busy = true
                                FirebaseAuth.getInstance().signInWithCredential(credential)
                                    .addOnSuccessListener {
                                        busy = false
                                        resetPhoneVerified = true
                                        resetOtpSent = true
                                        message = "Mobile verified. Enter a new password and tap Reset Password."
                                    }
                                    .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Phone verification failed." }
                            }
                            override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                                busy = false
                                message = exception.localizedMessage ?: "Could not send SMS OTP."
                            }
                            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                resetVerificationId = id
                                resetResendToken = token
                                resetOtpSent = true
                                busy = false
                                message = "OTP sent. Verify your mobile number."
                            }
                        }
                        val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                            .setPhoneNumber(fullPhone).setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity).setCallbacks(callbacks)
                        resetResendToken?.let { builder.setForceResendingToken(it) }
                        PhoneAuthProvider.verifyPhoneNumber(builder.build())
                    } else {
                        if (!resetPhoneVerified && (resetOtp.length != 6 || resetVerificationId.isNullOrBlank())) {
                            message = "Enter the 6-digit SMS OTP."
                            return@Button
                        }
                        if (resetPassword.length < 8 || resetPassword.length > 128) {
                            message = "New password must be 8 to 128 characters long."
                            return@Button
                        }
                        if (resetPassword != resetConfirmPassword) {
                            message = "New passwords do not match."
                            return@Button
                        }
                        busy = true
                        val applyPasswordReset: () -> Unit = {
                            val verifiedPhone = FirebaseAuth.getInstance().currentUser?.phoneNumber
                            if (verifiedPhone != fullPhone) {
                                FirebaseAuth.getInstance().signOut()
                                busy = false
                                message = "Verified mobile number does not match."
                            } else {
                                val payload = hashMapOf("phoneNumber" to fullPhone, "newPassword" to resetPassword)
                                FirebaseFunctions.getInstance("asia-south1")
                                    .getHttpsCallable("resetPhonePassword").call(payload)
                                    .addOnSuccessListener {
                                        busy = false
                                        resetOtpSent = false
                                        resetPhoneVerified = false
                                        resetVerificationId = null
                                        resetOtp = ""
                                        password = resetPassword
                                        resetPassword = ""
                                        resetConfirmPassword = ""
                                        phoneResetMode = false
                                        message = "Password reset successful. You are now signed in."
                                        onAuthenticated()
                                    }
                                    .addOnFailureListener { exception ->
                                        FirebaseAuth.getInstance().signOut()
                                        busy = false
                                        message = exception.localizedMessage ?: "Password reset failed."
                                    }
                            }
                        }
                        if (resetPhoneVerified) {
                            applyPasswordReset()
                        } else {
                            val credential = PhoneAuthProvider.getCredential(resetVerificationId!!, resetOtp)
                            FirebaseAuth.getInstance().signInWithCredential(credential)
                                .addOnSuccessListener { applyPasswordReset() }
                                .addOnFailureListener { exception ->
                                    busy = false
                                    message = exception.localizedMessage ?: "OTP verification failed."
                                }
                        }
                    }
                }
            ) { Text(if (busy) "Please wait..." else if (resetOtpSent) "Reset Password" else "Send OTP") }
            TextButton(onClick = {
                phoneResetMode = false
                resetOtpSent = false
                resetPhoneVerified = false
                resetVerificationId = null
                resetOtp = ""
                resetPassword = ""
                resetConfirmPassword = ""
                message = ""
            }) { Text("Back to Phone Login") }
        } else {
        Button(
            onClick = {
                message = ""
                val auth = FirebaseAuth.getInstance()
                val requirePasswordNow = !usePhone || !isSignUp || phoneOtpSent
                if (requirePasswordNow && password.length < 8) {
                    message = "Password must be at least 8 characters."
                    return@Button
                }
                if (isSignUp && name.isBlank()) {
                    message = "Enter your full name."
                    return@Button
                }
                if (usePhone) {
                    val fullPhone = countryCode + phoneNumber.filter(Char::isDigit)
                    if (!fullPhone.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
                        message = "Enter a valid mobile number."
                        return@Button
                    }
                    if (isSignUp) {
                        if (phoneOtpSent) {
                            if (phoneOtp.length != 6 || phoneVerificationId.isNullOrBlank()) {
                                message = "Enter the 6-digit SMS OTP."
                                return@Button
                            }
                            busy = true
                            val credential = PhoneAuthProvider.getCredential(phoneVerificationId!!, phoneOtp)
                            finishPhoneSignup(auth, credential, fullPhone, password, name.trim()) { success, detail ->
                                busy = false
                                if (success) onAuthenticated() else message = detail
                            }
                        } else {
                            val activity = context as? Activity
                            if (activity == null) {
                                message = "Unable to start phone verification on this screen."
                                return@Button
                            }
                            busy = true
                            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                                    finishPhoneSignup(auth, credential, fullPhone, password, name.trim()) { success, detail ->
                                        busy = false
                                        if (success) onAuthenticated() else message = detail
                                    }
                                }
                                override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                                    busy = false
                                    message = exception.localizedMessage ?: "Could not send SMS OTP. Check Firebase Phone Auth setup."
                                }
                                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                    phoneVerificationId = id
                                    phoneResendToken = token
                                    phoneOtpSent = true
                                    busy = false
                                    message = "OTP sent. Please verify your mobile number."
                                }
                            }
                            val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
                                .setPhoneNumber(fullPhone)
                                .setTimeout(60L, TimeUnit.SECONDS)
                                .setActivity(activity)
                                .setCallbacks(callbacks)
                            phoneResendToken?.let { optionsBuilder.setForceResendingToken(it) }
                            PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
                        }
                    } else {
                        busy = true
                        val payload = hashMapOf("phoneNumber" to fullPhone, "password" to password)
                        // Verify that the installed Debug/Release App Check provider can supply a token
                        // before calling the App Check-enforced phone-login function. Never display the token.
                        FirebaseAppCheck.getInstance().getAppCheckToken(false)
                            .addOnSuccessListener { appCheckToken ->
                                if (appCheckToken.token.isBlank()) {
                                    busy = false
                                    message = "LOGIN_STAGE: App Check returned an empty token. Check this build's App Check provider."
                                } else {
                                    FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("loginWithPhonePassword").call(payload)
                                        .addOnSuccessListener { result ->
                                            val data = result.getData() as? Map<*, *>
                                            val token = data?.get("token") as? String
                                            if (token.isNullOrBlank()) {
                                                busy = false
                                                message = "LOGIN_STAGE: Cloud Function succeeded but returned no custom token."
                                            } else {
                                                message = "LOGIN_STAGE: Cloud Function succeeded; Firebase token sign-in is next."
                                                auth.signInWithCustomToken(token)
                                                    .addOnSuccessListener {
                                                        busy = false
                                                        message = ""
                                                        onAuthenticated()
                                                    }
                                                    .addOnFailureListener { exception ->
                                                        busy = false
                                                        message = "LOGIN_STAGE: Firebase custom-token sign-in failed: ${exception.javaClass.simpleName}: ${exception.localizedMessage ?: "No details"}"
                                                    }
                                            }
                                        }
                                        .addOnFailureListener { exception ->
                                            busy = false
                                            val code = (exception as? com.google.firebase.functions.FirebaseFunctionsException)?.code?.name
                                                ?: exception.javaClass.simpleName
                                            message = "LOGIN_STAGE: Cloud Function call failed ($code): ${exception.localizedMessage ?: "No details"}"
                                        }
                                }
                            }
                            .addOnFailureListener { exception ->
                                busy = false
                                message = "LOGIN_STAGE: App Check token unavailable: ${exception.javaClass.simpleName}: ${exception.localizedMessage ?: "No details"}"
                            }
                    }
                } else {
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        message = "Enter a valid email address."
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
                                        firebaseUser.sendEmailVerification()
                                            .addOnSuccessListener {
                                                busy = false
                                                message = "Verification email sent. Link par click karein; verify hone ke baad PPA Talk app mein wapas aate hi auto login ho jayega."
                                            }
                                            .addOnFailureListener { exception ->
                                                busy = false
                                                message = exception.localizedMessage ?: "Could not send verification email. Please try again."
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
                            .addOnSuccessListener { result ->
                                val signedInUser = result.user
                                if (signedInUser == null) {
                                    busy = false
                                    message = "Login succeeded but account details were unavailable."
                                } else if (!signedInUser.isEmailVerified) {
                                    signedInUser.sendEmailVerification()
                                        .addOnCompleteListener {
                                            auth.signOut()
                                            busy = false
                                            message = "Please verify your email first. A fresh verification link has been sent."
                                        }
                                } else {
                                    // Firebase Authentication has succeeded. Do not make
                                    // login depend on a profile Firestore batch; legacy profile
                                    // documents are initialized/migrated after entering the app.
                                    busy = false
                                    onAuthenticated()
                                }
                            }
                            .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Login failed." }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087BFF)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(if (busy) "Please wait..." else if (usePhone && isSignUp && !phoneOtpSent) "Send OTP" else if (usePhone && isSignUp && phoneOtpSent) "Verify OTP & Sign Up" else if (isSignUp) "Sign Up" else "Sign In")
        }
        }

        if (!isSignUp && !usePhone && !phoneResetMode) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Divider(Modifier.weight(1f), color = Color(0xFF54718D))
                Text("Or continue with", color = authMuted, modifier = Modifier.padding(horizontal = 12.dp))
                Divider(Modifier.weight(1f), color = Color(0xFF54718D))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { usePhone = true; message = "" },
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0xFF54718D))
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Sign in with Phone", color = Color.White)
            }
        }
        OutlinedButton(
            onClick = {
                message = ""
                busy = true
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFF54718D))
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "G",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFF4285F4))
                        )
                    )
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(if (busy) "Please wait..." else "Continue with Google")
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isSignUp) "Already have an account? " else "Don't have an account? ", color = Color.White)
            TextButton(onClick = { isSignUp = !isSignUp; message = "" }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
                Text(if (isSignUp) "Sign In" else "Create Account", color = authCyan, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!isSignUp && usePhone && !phoneResetMode) {
            TextButton(enabled = !busy, onClick = {
                phoneResetMode = true
                resetOtpSent = false
                resetVerificationId = null
                resetOtp = ""
                resetPassword = ""
                resetConfirmPassword = ""
                message = "Enter your registered mobile number to reset your password."
            }) { Text("Forgot password?") }
        }
        if (message.startsWith("Verification email sent")) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(76.dp).background(RoyalBlue.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(44.dp))
                    }
                    Text("Verify Your Email", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("We've sent a verification link to your email address.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (email.isNotBlank()) Text(email, color = RoyalBlue, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Please check your inbox and spam folder, then tap the link to verify your account.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_APP_EMAIL))
                        } catch (_: Exception) {
                            message = "Email app unavailable. Open your mailbox manually and tap the verification link."
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                        Text("Open Email App")
                    }
                    OutlinedButton(onClick = {
                        val pending = FirebaseAuth.getInstance().currentUser
                        if (pending != null && !pending.isEmailVerified) {
                            busy = true
                            pending.sendEmailVerification().addOnCompleteListener { task ->
                                busy = false
                                message = if (task.isSuccessful) "Verification email sent again. Check your inbox and spam folder."
                                    else task.exception?.localizedMessage ?: "Could not resend verification email."
                            }
                        } else message = "Please sign up again or sign in to request a verification link."
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(2.dp, Orange)) {
                        Text(if (busy) "Please wait..." else "Resend Link", color = Orange)
                    }
                }
            }
        } else if (message.isNotBlank()) Text(message, color = if (message.startsWith("Password reset link sent")) RoyalBlue else MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text("Public community • Be respectful and stay safe",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(26.dp))
        Spacer(Modifier.height(8.dp))
    }
}

private fun authSendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
        .addOnSuccessListener { onResult(true, "Password reset link sent to your email. Check your inbox and spam folder.") }
        .addOnFailureListener { exception -> onResult(false, exception.localizedMessage ?: "Could not send password reset email.") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSocialApp(uid: String, email: String, displayName: String, isDarkMode: Boolean, onThemeModeChange: (Boolean) -> Unit, onSignOut: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var canonicalProfileName by remember(uid) { mutableStateOf(displayName) }
    DisposableEffect(uid) {
        val nameRegistration = FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getString("displayName")?.takeIf { it.isNotBlank() }?.let {
                    canonicalProfileName = it
                }
            }
        onDispose { nameRegistration.remove() }
    }
    val effectiveDisplayName = canonicalProfileName.ifBlank { displayName }
    DisposableEffect(uid, lifecycleOwner) {
        val db = FirebaseFirestore.getInstance()
        val publicProfileRef = db.collection("publicProfiles").document(uid)
        // Existing accounts created before profile visibility was added may have no
        // visibility field. Default those legacy profiles to public exactly once.
        val directoryRef = db.collection("profileDirectory").document(uid)
        val presenceRef = db.collection("profilePresence").document(uid)
        publicProfileRef.get().addOnSuccessListener { profile ->
            if (!profile.exists()) {
                publicProfileRef.set(mapOf(
                    "uid" to uid, "displayName" to effectiveDisplayName.ifBlank { "PPA Talk user" },
                    "profileVisibility" to "public",
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ), com.google.firebase.firestore.SetOptions.merge())
                directoryRef.set(mapOf("uid" to uid, "displayName" to effectiveDisplayName.ifBlank { "PPA Talk user" },
                    "profileVisibility" to "public",
                    "isOnline" to com.google.firebase.firestore.FieldValue.delete(),
                    "lastSeen" to com.google.firebase.firestore.FieldValue.delete(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
                    com.google.firebase.firestore.SetOptions.merge())
            } else {
                if (!profile.contains("profileVisibility")) {
                    publicProfileRef.set(mapOf("profileVisibility" to "public",
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
                        com.google.firebase.firestore.SetOptions.merge())
                }
                // Remove legacy presence fields from the broadly readable profile documents.
                publicProfileRef.set(mapOf(
                    "isOnline" to com.google.firebase.firestore.FieldValue.delete(),
                    "lastSeen" to com.google.firebase.firestore.FieldValue.delete()
                ), com.google.firebase.firestore.SetOptions.merge())
                val isPublicProfile = (profile.getString("profileVisibility") ?: "public") == "public"
                directoryRef.set(mapOf(
                    "uid" to uid,
                    "displayName" to (profile.getString("displayName") ?: effectiveDisplayName.ifBlank { "PPA Talk user" }),
                    "username" to profile.getString("username").orEmpty(),
                    // Private profile media URLs are bearer links. Keep them out of
                    // the directory, which is readable by every signed-in account.
                    "photoUrl" to if (isPublicProfile) profile.getString("photoUrl").orEmpty()
                        else com.google.firebase.firestore.FieldValue.delete(),
                    "coverPhotoUrl" to if (isPublicProfile) profile.getString("coverPhotoUrl").orEmpty()
                        else com.google.firebase.firestore.FieldValue.delete(),
                    "profileVisibility" to (profile.getString("profileVisibility") ?: "public"),
                    "isOnline" to com.google.firebase.firestore.FieldValue.delete(),
                    "lastSeen" to com.google.firebase.firestore.FieldValue.delete(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ), com.google.firebase.firestore.SetOptions.merge())
            }
        }
        fun setPresence(online: Boolean) {
            val presence = mapOf(
                "uid" to uid,
                "isOnline" to online,
                "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            presenceRef.set(presence, com.google.firebase.firestore.SetOptions.merge())
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> setPresence(true)
                Lifecycle.Event.ON_STOP -> setPresence(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) setPresence(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            setPresence(false)
        }
    }
    var selected by remember { mutableStateOf("Home") }
    var reelUploadStatus by remember { mutableStateOf("") }
    var reelUploadProgress by remember { mutableIntStateOf(0) }
    var activeChatUid by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    var viewedProfileUid by remember { mutableStateOf(uid) }
    var returnToProfileAfterChat by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val backStack = remember { mutableStateListOf<String>() }

    fun navigateTo(destination: String) {
        if (selected != destination) {
            backStack.add(selected)
            selected = destination
        }
        activeChatUid = null
    }

    fun handleBack() {
        if (activeChatUid != null && selected == "Profile" && backStack.isNotEmpty()) {
            selected = backStack.removeAt(backStack.lastIndex)
        } else if (activeChatUid != null) {
            activeChatUid = null
        } else if (backStack.isNotEmpty()) {
            selected = backStack.removeAt(backStack.lastIndex)
        } else if (selected != "Home") {
            selected = "Home"
        }
    }

    BackHandler(enabled = activeChatUid != null || selected != "Home" || backStack.isNotEmpty()) {
        handleBack()
    }

    DisposableEffect(uid) {
        val db = FirebaseFirestore.getInstance()
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .set(mapOf("token" to token, "platform" to "android",
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        }

        // Keep an app-wide listener so an incoming call can open its chat even
        // when the receiver was on another tab when the push notification arrived.
        val incomingRegistration = db.collection("callSessions")
            .whereEqualTo("calleeUid", uid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val incoming = snapshot?.documents?.firstOrNull()
                    ?: return@addSnapshotListener
                val callerUid = incoming.getString("callerUid") ?: return@addSnapshotListener
                activeChatUid = callerUid
                db.collection("publicProfiles").document(callerUid).get()
                    .addOnSuccessListener { profile ->
                        activeChatName = profile.getString("displayName") ?: "PPA Talk user"
                    }
            }
        onDispose { incomingRegistration.remove() }
    }

    Scaffold(
        modifier = Modifier.pointerInput(selected, activeChatUid) {
            if (activeChatUid == null) {
                val mainTabs = listOf("Home", "Friends", "Reels", "Marketplace", "Notifications", "Menu")
                var horizontalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDrag += dragAmount
                    },
                    onDragEnd = {
                        val currentIndex = mainTabs.indexOf(selected)
                        if (currentIndex >= 0 && kotlin.math.abs(horizontalDrag) > 80f) {
                            val nextIndex = if (horizontalDrag < 0f) {
                                (currentIndex + 1).coerceAtMost(mainTabs.lastIndex)
                            } else {
                                (currentIndex - 1).coerceAtLeast(0)
                            }
                            if (nextIndex != currentIndex) navigateTo(mainTabs[nextIndex])
                        }
                        horizontalDrag = 0f
                    },
                    onDragCancel = { horizontalDrag = 0f }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (activeChatUid == null) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF06182B),
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("PPA ", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                                Text("CONNECT", color = Orange, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                            }
                            Text("Create. Connect. Share.", color = Orange, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigateTo("Create") }) { Icon(Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(30.dp)) }
                        IconButton(onClick = { navigateTo("Search") }) { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(28.dp)) }
                        IconButton(onClick = { navigateTo("Notifications") }) { Icon(Icons.Default.Notifications, contentDescription = "Notifications", modifier = Modifier.size(28.dp)) }
                    }
                )
                HorizontalDivider(color = Color(0xFF234C72))
            }
        },
        bottomBar = {
            if (activeChatUid == null) {
                NavigationBar(containerColor = Color(0xFF06182B), contentColor = Color.White) {
                    val homeTabs = listOf(
                        "Home" to Icons.Default.Home,
                        "Friends" to Icons.Default.PeopleAlt,
                        "Messenger" to Icons.Default.Forum,
                        "Reels" to Icons.Default.PlayCircle,
                        "Notifications" to Icons.Default.Notifications,
                        "Menu" to Icons.Default.Menu
                    )
                    homeTabs.forEach { (label, icon) ->
                        NavigationBarItem(
                            selected = (selected == label) || (label == "Messenger" && selected == "Chat"),
                            onClick = { navigateTo(label) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(if (label == "Messenger") "Chat" else label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF21C5F5),
                                selectedTextColor = Color(0xFF21C5F5),
                                indicatorColor = Color(0xFF123D67),
                                unselectedIconColor = Color.White,
                                unselectedTextColor = Color.White
                            )
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (activeChatUid != null && selected != "Profile") {
            ChatScreen(
                currentUid = uid,
                friendUid = activeChatUid!!,
                friendName = activeChatName,
                modifier = Modifier.padding(padding),
                onBack = {
                    activeChatUid = null
                    if (returnToProfileAfterChat) {
                        selected = "Profile"
                        returnToProfileAfterChat = false
                    }
                },
                onOpenProfile = {
                    viewedProfileUid = activeChatUid!!
                    backStack.add(selected)
                    selected = "Profile"
                }
            )
        } else when (selected) {
            "Home" -> HomeFeed(
                uid,
                Modifier.padding(padding),
                onCreatePost = { navigateTo("Create") },
                onCreatePhoto = { navigateTo("CreatePhoto") },
                onCreateVideo = { navigateTo("CreateReel") },
                onOpenProfile = { viewedProfileUid = uid; navigateTo("Profile") },
                onOpenUserProfile = { profileUid -> viewedProfileUid = profileUid; navigateTo("Profile") },
                displayName = displayName,
                reelUploadStatus = reelUploadStatus,
                reelUploadProgress = reelUploadProgress,
                onDismissReelUploadStatus = {
                    reelUploadStatus = ""
                    reelUploadProgress = 0
                }
            )
            "Profile" -> if (viewedProfileUid == uid) {
                ProfileScreen(uid = uid, email = email, initialName = effectiveDisplayName, modifier = Modifier.padding(padding))
            } else {
                PublicProfileScreen(viewedProfileUid, currentUid = uid, currentName = effectiveDisplayName, onOpenChat = { friendUid, friendName ->
                    returnToProfileAfterChat = true
                    activeChatUid = friendUid
                    activeChatName = friendName
                    selected = "Messenger"
                }, modifier = Modifier.padding(padding))
            }
            "Menu" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Menu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RoyalBlue)
                listOf("Profile", "Settings", "Search").forEach { item ->
                    OutlinedButton(onClick = { navigateTo(item) }, modifier = Modifier.fillMaxWidth()) { Text(item) }
                }
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
            }
            "Marketplace" -> SimplePage("Marketplace", "Discover products and community listings", Modifier.padding(padding))
            "Search" -> FriendsScreen(uid, effectiveDisplayName, { friendUid, friendName ->
                activeChatUid = friendUid
                activeChatName = friendName
            }, Modifier.padding(padding), onOpenProfile = { profileUid ->
                viewedProfileUid = profileUid
                navigateTo("Profile")
            }, onBack = { handleBack() }, screenTitle = "Search Friends")
            "Messenger" -> MessengerScreen(uid, onOpenChat = { friendUid, friendName -> activeChatUid = friendUid; activeChatName = friendName }, modifier = Modifier.padding(padding))
            "Create" -> CreatePostScreen(uid, effectiveDisplayName, email, Modifier.padding(padding), onPublished = { backStack.clear(); selected = "Home" })
            "CreatePhoto" -> CreatePostScreen(uid, effectiveDisplayName, email, Modifier.padding(padding), initialPicker = "image", onPublished = { backStack.clear(); selected = "Home" })
            "CreateVideo" -> CreatePostScreen(uid, effectiveDisplayName, email, Modifier.padding(padding), initialPicker = "video", onPublished = { backStack.clear(); selected = "Home" })
            "CreateReel" -> CreatePostScreen(
                uid, displayName, email, Modifier.padding(padding), initialPicker = "media",
                onPublished = {
                    reelUploadStatus = ""
                    reelUploadProgress = 0
                    backStack.clear()
                    selected = "Home"
                },
                onClose = {
                    reelUploadStatus = ""
                    reelUploadProgress = 0
                    backStack.clear()
                    selected = "Home"
                },
                onReelUploadProgress = { status, percent ->
                    reelUploadStatus = status
                    reelUploadProgress = percent.coerceIn(0, 100)
                    if (status.startsWith("Uploading") || status.startsWith("Saving")) {
                        backStack.clear()
                        selected = "Home"
                    }
                }
            )
            "Settings" -> SettingsScreen(isDarkMode, onThemeModeChange, Modifier.padding(padding))
            "Friends" -> FriendsScreen(uid, effectiveDisplayName, { friendUid, friendName ->
                activeChatUid = friendUid
                activeChatName = friendName
            }, Modifier.padding(padding), onOpenProfile = { profileUid ->
                viewedProfileUid = profileUid
                navigateTo("Profile")
            }, onBack = { handleBack() })
            "Reels" -> ReelsScreen(uid, Modifier.padding(padding), onOpenUserProfile = { profileUid ->
                viewedProfileUid = profileUid
                navigateTo("Profile")
            }, onCloseViewer = { handleBack() })
            "Notifications" -> NotificationsScreen(uid, Modifier.padding(padding), onOpenFriends = { navigateTo("Friends") })
            else -> SimplePage("Notifications", "Your activity will appear here", Modifier.padding(padding))
        }
    }
}

private fun sendFriendRequestSafely(
    db: FirebaseFirestore, fromUid: String, toUid: String, fromName: String, toName: String,
    onResult: (Boolean, String) -> Unit
) {
    val requestRef = db.collection("friendRequests").document(fromUid + "_" + toUid)
    requestRef.get().addOnSuccessListener { existing ->
        when (existing.getString("status")) {
            "pending" -> onResult(false, "Friend request already sent.")
            "accepted" -> onResult(false, "You are already friends.")
            "rejected" -> requestRef.update("status", "pending")
                .addOnSuccessListener { onResult(true, "Friend request sent again.") }
                .addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not resend request.") }
            else -> requestRef.set(hashMapOf(
                "fromUid" to fromUid, "toUid" to toUid, "fromName" to fromName, "toName" to toName,
                "status" to "pending",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )).addOnSuccessListener { onResult(true, "Friend request sent.") }
                .addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not send request.") }
        }
    }.addOnFailureListener {
        // A missing deterministic request document may be unreadable under
        // participant-only rules. Try the permitted create operation directly.
        requestRef.set(hashMapOf(
            "fromUid" to fromUid, "toUid" to toUid, "fromName" to fromName, "toName" to toName,
            "status" to "pending",
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )).addOnSuccessListener { onResult(true, "Friend request sent.") }
            .addOnFailureListener { createError ->
                onResult(false, createError.localizedMessage ?: "Could not send friend request. Check Firestore rules.")
            }
    }
}

private fun cancelFriendRequestSafely(
    db: FirebaseFirestore, currentUid: String, friendUid: String,
    onResult: (Boolean, String) -> Unit
) {
    db.collection("friendRequests").document(currentUid + "_" + friendUid).delete()
        .addOnSuccessListener { onResult(true, "Friend request cancelled.") }
        .addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not cancel friend request.") }
}

private fun unfriendSafely(
    db: FirebaseFirestore, currentUid: String, friendUid: String,
    onResult: (Boolean, String) -> Unit
) {
    val forwardRequest = db.collection("friendRequests").document(currentUid + "_" + friendUid)
    val reverseRequest = db.collection("friendRequests").document(friendUid + "_" + currentUid)

    // The original request can have been sent by either friend. Read both
    // directions first, then revoke whichever accepted request actually exists.
    forwardRequest.get().addOnSuccessListener { forward ->
        reverseRequest.get().addOnSuccessListener { reverse ->
            val batch = db.batch()
            batch.delete(db.collection("friends").document(currentUid).collection("list").document(friendUid))
            batch.delete(db.collection("friends").document(friendUid).collection("list").document(currentUid))
            if (forward.exists() && forward.getString("status") == "accepted") {
                batch.update(forwardRequest, "status", "rejected")
            }
            if (reverse.exists() && reverse.getString("status") == "accepted") {
                batch.update(reverseRequest, "status", "rejected")
            }
            batch.commit()
                .addOnSuccessListener { onResult(true, "Friend removed. You can send a new friend request.") }
                .addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not remove friend. Check Firestore rules.") }
        }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not check friend request.") }
    }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not check friend request.") }
}

private fun acceptFriendRequestSafely(
    db: FirebaseFirestore, currentUid: String, friendUid: String, currentName: String, friendName: String,
    onResult: (Boolean, String) -> Unit
) {
    val requestRef = db.collection("friendRequests").document(friendUid + "_" + currentUid)
    // Use profileDirectory (signed-in readable) instead of publicProfiles:
    // the sender's profile may be private until this request is accepted.
    db.collection("profileDirectory").document(currentUid).get().addOnSuccessListener { mine ->
        db.collection("profileDirectory").document(friendUid).get().addOnSuccessListener { theirs ->
            val batch = db.batch()
            batch.update(requestRef, "status", "accepted")
            batch.set(db.collection("friends").document(currentUid).collection("list").document(friendUid),
                mapOf("uid" to friendUid, "displayName" to (theirs.getString("displayName") ?: friendName),
                    "photoUrl" to (theirs.getString("photoUrl") ?: "")))
            batch.set(db.collection("friends").document(friendUid).collection("list").document(currentUid),
                mapOf("uid" to currentUid, "displayName" to (mine.getString("displayName") ?: currentName),
                    "photoUrl" to (mine.getString("photoUrl") ?: "")))
            batch.commit().addOnSuccessListener { onResult(true, "Friend request accepted. You are now friends.") }
                .addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not accept request or update friend lists.") }
        }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not load friend's directory entry.") }
    }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not load your directory entry.") }
}

data class FeedPost(val id: String, val uid: String, val authorName: String, val text: String, val createdAt: Date?, val mediaUrl: String = "", val mediaType: String = "", val postType: String = "post", val storagePath: String = "")
data class FeedComment(val id: String, val uid: String, val authorName: String, val text: String, val parentId: String, val createdAt: Date?)

private fun observeVisibleContentOwners(
    db: FirebaseFirestore,
    currentUid: String,
    onOwnersChanged: (Map<String, String>) -> Unit
): List<com.google.firebase.firestore.ListenerRegistration> {
    var friendUids: Set<String> = emptySet()
    var publicUids: Set<String> = emptySet()
    fun publish() {
        val access = mutableMapOf<String, String>()
        publicUids.forEach { access[it] = "public" }
        friendUids.forEach { access[it] = "friend" }
        access[currentUid] = "owner"
        onOwnersChanged(access)
    }
    val friendsRegistration = db.collection("friends").document(currentUid)
        .collection("list").addSnapshotListener { snapshot, _ ->
            friendUids = snapshot?.documents.orEmpty().map { it.id }.toSet()
            publish()
        }
    val publicRegistration = db.collection("profileDirectory")
        .whereEqualTo("profileVisibility", "public")
        .addSnapshotListener { snapshot, _ ->
            publicUids = snapshot?.documents.orEmpty().map { it.id }.toSet()
            publish()
        }
    publish()
    return listOf(friendsRegistration, publicRegistration)
}
@Composable
fun ReelsScreen(
    currentUid: String,
    modifier: Modifier = Modifier,
    onOpenUserProfile: (String) -> Unit = {},
    onCloseViewer: () -> Unit = {}
) {
    val context = LocalContext.current
    var reels by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedReelIndex by remember { mutableIntStateOf(-1) }
    var hiddenReelIds by remember { mutableStateOf(setOf<String>()) }

    DisposableEffect(currentUid) {
        val db = FirebaseFirestore.getInstance()
        val ownerRegistrations = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
        val ownerAccessModes = mutableMapOf<String, String>()
        val audienceRegistrations = observeVisibleContentOwners(db, currentUid) { ownerAccess ->
            (ownerRegistrations.keys - ownerAccess.keys).forEach { ownerUid ->
                ownerRegistrations.remove(ownerUid)?.remove()
                ownerAccessModes.remove(ownerUid)
                reels = reels.filterNot { it.uid == ownerUid }
            }
            ownerAccess.forEach { (ownerUid, accessMode) ->
                if (ownerAccessModes[ownerUid] != null && ownerAccessModes[ownerUid] != accessMode) {
                    ownerRegistrations.remove(ownerUid)?.remove()
                    reels = reels.filterNot { it.uid == ownerUid }
                }
                if (ownerUid !in ownerRegistrations) {
                    ownerAccessModes[ownerUid] = accessMode;
                    val ownerQuery = db.collection("posts").whereEqualTo("uid", ownerUid)
                    val scopedQuery = when (accessMode) {
                        "owner" -> ownerQuery
                        "friend" -> ownerQuery.whereIn("audience", listOf("public", "friends"))
                        else -> ownerQuery.whereEqualTo("audience", "public")
                    }
                    ownerRegistrations[ownerUid] = scopedQuery.addSnapshotListener { snapshot, exception ->
                            if (exception != null) {
                                error = exception.localizedMessage ?: "Reels load nahi ho paayi."
                            } else if (snapshot != null) {
                                val updated = snapshot.documents.mapNotNull { doc ->
                                    val mediaType = doc.getString("mediaType").orEmpty()
                                    val postType = doc.getString("postType").orEmpty()
                                    if (mediaType != "video" && postType != "reel") return@mapNotNull null
                                    FeedPost(doc.id, doc.getString("uid").orEmpty(),
                                        doc.getString("authorName") ?: "PPA Talk user",
                                        doc.getString("text").orEmpty(), doc.getTimestamp("createdAt")?.toDate(),
                                        doc.getString("mediaUrl").orEmpty(), mediaType, postType, doc.getString("storagePath").orEmpty())
                                }
                                reels = (reels.filterNot { it.uid == ownerUid } + updated)
                                    .sortedByDescending { it.createdAt?.time ?: 0L }
                                loading = false
                                error = ""
                            }
                        }
                }
            }
            loading = ownerRegistrations.isEmpty()
        }
        onDispose {
            audienceRegistrations.forEach { it.remove() }
            ownerRegistrations.values.forEach { it.remove() }
        }
    }

    val visibleReels = reels.filterNot { it.id in hiddenReelIds }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            error.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error, color = Color.White, modifier = Modifier.padding(16.dp))
            }
            visibleReels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Abhi koi Reel nahi hai. Create Post mein Video / Reel select karke share karein.",
                    color = Color.White, modifier = Modifier.padding(24.dp))
            }
            else -> ReelFullscreenViewer(visibleReels, 0, currentUid,
                onClose = onCloseViewer,
                onOpenUserProfile = onOpenUserProfile,
                onHideReel = { id -> hiddenReelIds = hiddenReelIds + id })
        }
    }

}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun ReelPreviewPlayer(mediaUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var previewError by remember(mediaUrl) { mutableStateOf("") }
    val player = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                previewError = error.localizedMessage ?: error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(mediaUrl, player) {
        previewError = ""
        player.stop()
        player.clearMediaItems()
        when {
            mediaUrl.startsWith("MEDIA_ERROR:") -> previewError = mediaUrl.removePrefix("MEDIA_ERROR:")
            mediaUrl.isBlank() -> Unit
            mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://") -> {
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(mediaUrl))
                player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                player.prepare()
                player.playWhenReady = true
            }
            else -> previewError = "Invalid media URL"
        }
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(factory = { viewContext ->
            androidx.media3.ui.PlayerView(viewContext).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
        if (previewError.isNotBlank()) Text("Reel playback error: $previewError", color = Color.White,
            modifier = Modifier.align(Alignment.Center).padding(12.dp))
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun ReelFullscreenViewer(
    reels: List<FeedPost>, initialIndex: Int, currentUid: String,
    onClose: () -> Unit, onOpenUserProfile: (String) -> Unit, onHideReel: (String) -> Unit
) {
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, reels.lastIndex)) }
    var showComments by remember { mutableStateOf(false) }
    var liked by remember(reels.getOrNull(index)?.id) { mutableStateOf(false) }
    var likeCount by remember(reels.getOrNull(index)?.id) { mutableIntStateOf(0) }
    var commentCount by remember(reels.getOrNull(index)?.id) { mutableIntStateOf(0) }
    var saved by remember(reels.getOrNull(index)?.id) { mutableStateOf(false) }
    var dragAmount by remember { mutableFloatStateOf(0f) }
    var reelMuted by remember(reels.getOrNull(index)?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val reel = reels.getOrNull(index) ?: return
    val secureMediaUrl = rememberSecureMediaUrl("reel", reel.id, reel.storagePath, reel.mediaUrl)

    DisposableEffect(reel.id, currentUid) {
        val likeListener = db.collection("posts").document(reel.id).collection("likes")
            .addSnapshotListener { snap, _ ->
                likeCount = snap?.size() ?: 0
                liked = snap?.documents?.any { it.id == currentUid } == true
            }
        val commentListener = db.collection("posts").document(reel.id).collection("comments")
            .addSnapshotListener { snap, _ -> commentCount = snap?.size() ?: 0 }
        val savedListener = db.collection("users").document(currentUid).collection("savedPosts")
            .document(reel.id).addSnapshotListener { snap, _ -> saved = snap?.exists() == true }
        onDispose { likeListener.remove(); commentListener.remove(); savedListener.remove() }
    }

    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(index, reels.size) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> dragAmount += amount },
                    onDragEnd = {
                        if (dragAmount < -55f && index < reels.lastIndex) index++
                        else if (dragAmount > 55f && index > 0) index--
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f }
                )
            }) {
            var playerError by remember(reel.id) { mutableStateOf("") }
            var playerLoading by remember(reel.id) { mutableStateOf(true) }
            val reelPlayer = remember(reel.id) {
                androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                    volume = 1f
                    setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(), true)
                }
            }
            DisposableEffect(reelPlayer) {
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playerLoading = false
                        playerError = "Playback failed: " + (error.localizedMessage ?: error.errorCodeName)
                        android.util.Log.e("PpaReelPlayer", "Reel playback error id=${reel.id} code=${error.errorCodeName}", error)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_READY) {
                            playerLoading = false
                            playerError = ""
                        } else if (state == androidx.media3.common.Player.STATE_BUFFERING) {
                            playerLoading = true
                        }
                    }
                }
                reelPlayer.addListener(listener)
                onDispose { reelPlayer.removeListener(listener); reelPlayer.release() }
            }
            LaunchedEffect(reelPlayer, secureMediaUrl) {
                playerError = ""
                playerLoading = true
                reelPlayer.stop()
                reelPlayer.clearMediaItems()
                when {
                    secureMediaUrl.startsWith("MEDIA_ERROR:") -> {
                        playerLoading = false
                        playerError = secureMediaUrl.removePrefix("MEDIA_ERROR:")
                    }
                    secureMediaUrl.isBlank() -> {
                        playerLoading = true
                    }
                    secureMediaUrl.startsWith("http://") || secureMediaUrl.startsWith("https://") -> {
                        try {
                            reelPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(secureMediaUrl))
                            reelPlayer.prepare()
                            reelPlayer.playWhenReady = true
                        } catch (e: Exception) {
                            playerLoading = false
                            playerError = "Could not load video: ${e.localizedMessage ?: e.javaClass.simpleName}"
                            android.util.Log.e("PpaReelPlayer", "Media setup failed id=${reel.id}", e)
                        }
                    }
                    else -> {
                        playerLoading = false
                        playerError = "Invalid video URL."
                    }
                }
            }
            AndroidView(factory = { viewContext ->
                androidx.media3.ui.PlayerView(viewContext).apply {
                    player = reelPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            }, update = { view -> view.player = reelPlayer },
                modifier = Modifier.fillMaxSize())
            if (playerLoading && playerError.isBlank()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            if (playerError.isNotBlank()) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth(0.85f)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Text("Video unavailable\\n$playerError", color = Color.White)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close reels", tint = Color.White)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 34.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                IconButton(onClick = {
                    reelMuted = !reelMuted
                    reelPlayer.volume = if (reelMuted) 0f else 1f
                }) {
                    Icon(if (reelMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (reelMuted) "Unmute reel" else "Mute reel", tint = Color.White,
                        modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = {
                        val ref = db.collection("posts").document(reel.id).collection("likes").document(currentUid)
                        if (liked) ref.delete() else ref.set(mapOf("uid" to currentUid, "reaction" to "👍",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                    }) { Icon(Icons.Default.ThumbUp, contentDescription = "Like reel",
                        tint = if (liked) RoyalBlue else Color.White, modifier = Modifier.size(32.dp)) }
                    Text(likeCount.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { showComments = true }) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment on reel",
                            tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Text(commentCount.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, secureMediaUrl.ifBlank { "PPA Talk Reel" })
                    }
                    context.startActivity(Intent.createChooser(send, "Share reel"))
                }) { Icon(Icons.Default.Share, contentDescription = "Share reel", tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = {
                    val ref = db.collection("users").document(currentUid).collection("savedPosts").document(reel.id)
                    if (saved) ref.delete() else ref.set(mapOf("postId" to reel.id, "ownerUid" to reel.uid,
                        "savedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                }) { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save reel", tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = { onHideReel(reel.id) }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "More reel options", tint = Color.White)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.82f)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(38.dp).clickable { onOpenUserProfile(reel.uid) },
                        shape = CircleShape, color = Color.DarkGray) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Open creator profile",
                            tint = Color.White, modifier = Modifier.padding(3.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(reel.authorName, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).clickable { onOpenUserProfile(reel.uid) }, maxLines = 1)
                    if (reel.uid != currentUid) {
                        OutlinedButton(onClick = { onOpenUserProfile(reel.uid) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)) {
                            Text("Follow", color = Color.White)
                        }
                    }
                }
                if (reel.text.isNotBlank()) Text(reel.text, color = Color.White,
                    modifier = Modifier.padding(top = 9.dp), maxLines = 3)
            }

        }
    }
    if (showComments) PostCommentsDialog(reel, currentUid) { showComments = false }
}

@Composable
fun HomeFeed(
    currentUid: String,
    modifier: Modifier = Modifier,
    onCreatePost: () -> Unit = {},
    onCreatePhoto: () -> Unit = onCreatePost,
    onCreateVideo: () -> Unit = onCreatePost,
    onOpenProfile: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    displayName: String = "PPA Talk user",
    reelUploadStatus: String = "",
    reelUploadProgress: Int = 0,
    onDismissReelUploadStatus: () -> Unit = {}
) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<FeedPost?>(null) }
    var selectedFeedReel by remember { mutableStateOf<FeedPost?>(null) }
    var hiddenPostIds by remember { mutableStateOf(setOf<String>()) }
    DisposableEffect(currentUid) {
        val db = FirebaseFirestore.getInstance()
        val ownerRegistrations = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
        val ownerAccessModes = mutableMapOf<String, String>()
        // Subscribe to the current user's posts independently from directory visibility.
        ownerRegistrations[currentUid] = db.collection("posts").whereEqualTo("uid", currentUid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage ?: "Could not load your posts."
                } else if (snapshot != null) {
                    val ownPosts = snapshot.documents.map { doc ->
                        FeedPost(doc.id, doc.getString("uid") ?: currentUid,
                            doc.getString("authorName") ?: "PPA Talk user",
                            doc.getString("text") ?: "", doc.getTimestamp("createdAt")?.toDate(),
                            doc.getString("mediaUrl") ?: "", doc.getString("mediaType") ?: "",
                            doc.getString("postType") ?: "post", doc.getString("storagePath").orEmpty())
                    }
                    posts = (posts.filterNot { it.uid == currentUid } + ownPosts)
                        .sortedByDescending { it.createdAt?.time ?: 0L }
                    loading = false
                    error = ""
                }
            }
        ownerAccessModes[currentUid] = "owner"
        val audienceRegistrations = observeVisibleContentOwners(db, currentUid) { ownerAccess ->
            (ownerRegistrations.keys - ownerAccess.keys - currentUid).forEach { ownerUid ->
                ownerRegistrations.remove(ownerUid)?.remove()
                ownerAccessModes.remove(ownerUid)
                posts = posts.filterNot { it.uid == ownerUid }
            }
            ownerAccess.forEach { (ownerUid, accessMode) ->
                if (ownerUid == currentUid) return@forEach
                if (ownerAccessModes[ownerUid] != null && ownerAccessModes[ownerUid] != accessMode) {
                    ownerRegistrations.remove(ownerUid)?.remove()
                    posts = posts.filterNot { it.uid == ownerUid }
                }
                if (ownerUid !in ownerRegistrations) {
                    ownerAccessModes[ownerUid] = accessMode;
                    val ownerQuery = db.collection("posts").whereEqualTo("uid", ownerUid)
                    val scopedQuery = when (accessMode) {
                        "owner" -> ownerQuery
                        "friend" -> ownerQuery.whereIn("audience", listOf("public", "friends"))
                        else -> ownerQuery.whereEqualTo("audience", "public")
                    }
                    ownerRegistrations[ownerUid] = scopedQuery.addSnapshotListener { snapshot, exception ->
                            if (exception != null) {
                                error = exception.localizedMessage ?: "Could not load posts. Check Firestore rules."
                            } else if (snapshot != null) {
                                val updated = snapshot.documents.map { doc ->
                                    FeedPost(doc.id, doc.getString("uid") ?: "",
                                        doc.getString("authorName") ?: "PPA Talk user",
                                        doc.getString("text") ?: "",
                                        doc.getTimestamp("createdAt")?.toDate(), doc.getString("mediaUrl") ?: "",
                                        doc.getString("mediaType") ?: "", doc.getString("postType") ?: "post", doc.getString("storagePath").orEmpty())
                                }
                                posts = (posts.filterNot { it.uid == ownerUid } + updated)
                                    .sortedByDescending { it.createdAt?.time ?: 0L }
                                loading = false
                                error = ""
                            }
                        }
                }
            }
            loading = ownerRegistrations.isEmpty()
        }
        onDispose {
            audienceRegistrations.forEach { it.remove() }
            ownerRegistrations.values.forEach { it.remove() }
        }
    }
    LazyColumn(modifier.fillMaxSize().background(Color(0xFF06182B)), contentPadding = PaddingValues(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0C2238)).padding(6.dp)) {
                StoriesStrip(uid = currentUid, displayName = displayName)
            }
        }
        item {
            HomeComposerCard(
                uid = currentUid,
                onCreatePost = onCreatePost,
                onCreatePhoto = onCreatePhoto,
                onCreateVideo = onCreateVideo,
                onOpenProfile = onOpenProfile
            )
        }
        if (reelUploadStatus.isNotBlank()) {
            item {
                val uploadFailed = reelUploadStatus.contains("failed", ignoreCase = true) ||
                    reelUploadStatus.contains("could not", ignoreCase = true)
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (uploadFailed) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Reel upload failed",
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        } else {
                            CircularProgressIndicator(
                                progress = { reelUploadProgress / 100f },
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(reelUploadStatus, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            color = if (uploadFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if (uploadFailed) {
                            IconButton(onClick = onDismissReelUploadStatus, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss upload error",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text("$reelUploadProgress%", style = MaterialTheme.typography.labelMedium, color = RoyalBlue)
                        }
                    }
                    if (!uploadFailed) {
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(progress = { reelUploadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (loading) item { CircularProgressIndicator() }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error.isBlank()) item { Text("No posts yet. Be the first to share something!") }
        items(posts.filterNot { it.id in hiddenPostIds }, key = { it.id }) { post ->
            FeedPostCard(post, currentUid, onOpenAuthorProfile = { onOpenUserProfile(post.uid) },
                onOpenReel = { selectedFeedReel = post },
                onHidePost = { hiddenPostIds = hiddenPostIds + post.id }) { selectedPost = post }
        }
    }
    selectedPost?.let { PostCommentsDialog(it, currentUid) { selectedPost = null } }
    selectedFeedReel?.let { feedReel ->
        ReelFullscreenViewer(listOf(feedReel), 0, currentUid,
            onClose = { selectedFeedReel = null },
            onOpenUserProfile = onOpenUserProfile,
            onHideReel = { selectedFeedReel = null })
    }
}


@Composable
fun HomeComposerCard(uid: String, onCreatePost: () -> Unit, onCreatePhoto: () -> Unit, onCreateVideo: () -> Unit, onOpenProfile: () -> Unit) {
    var profilePhotoUrl by remember(uid) { mutableStateOf("") }

    DisposableEffect(uid) {
        val db = FirebaseFirestore.getInstance()
        // Resolve the avatar from the lightweight directory first, then profile/private
        // documents as fallbacks so the composer never stays on the placeholder
        // when one profile document is missing or still syncing.
        val directoryRegistration = db.collection("profileDirectory").document(uid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it }
            }
        val profileRegistration = db.collection("publicProfiles").document(uid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it }
            }
        val userRegistration = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it }
            }
        onDispose {
            directoryRegistration.remove()
            profileRegistration.remove()
            userRegistration.remove()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF234C72)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2238))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = Color(0xFF173650),
                    onClick = onOpenProfile
                ) {
                    if (profilePhotoUrl.isNotBlank()) {
                        AsyncImage(
                            model = profilePhotoUrl,
                            contentDescription = "Open your profile",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Open your profile",
                            tint = RoyalBlue,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = Color(0xFF102D49),
                    onClick = onCreatePost
                ) {
                    Text("What's on your mind?", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Color(0xFFB9CCDF))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCreatePhoto) {
                    Icon(Icons.Default.Photo, contentDescription = null, tint = Color(0xFF36C5E5))
                    Spacer(Modifier.width(5.dp))
                    Text("Photo")
                }
                TextButton(onClick = onCreateVideo) {
                    Icon(Icons.Default.VideoCall, contentDescription = null, tint = Color(0xFFFFA51E))
                    Spacer(Modifier.width(5.dp))
                    Text("Video")
                }
                TextButton(onClick = onCreatePost) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF36C5E5))
                    Spacer(Modifier.width(5.dp))
                    Text("Write")
                }
            }
        }
    }
}

@Composable
fun StoriesStrip(uid: String, displayName: String) {
    val shareContext = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var stories by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var profilePhotoUrl by remember(uid) { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var viewingStory by remember { mutableStateOf<Map<String, Any>?>(null) }

    DisposableEffect(uid) {
        val storyOwnerRegistrations = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
        val storyResults = mutableMapOf<String, List<Map<String, Any>>>()
        val audienceRegistrations = observeVisibleContentOwners(db, uid) { ownerAccess ->
            (storyOwnerRegistrations.keys - ownerAccess.keys).forEach { ownerUid ->
                storyOwnerRegistrations.remove(ownerUid)?.remove()
                storyResults.remove(ownerUid)
            }
            stories = storyResults.values.flatten()
                .sortedByDescending { (it["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L }
            ownerAccess.keys.forEach { ownerUid ->
                if (ownerUid !in storyOwnerRegistrations) {
                    storyOwnerRegistrations[ownerUid] = db.collection("stories")
                        .whereEqualTo("uid", ownerUid)
                        .addSnapshotListener { snapshot, _ ->
                            val now = System.currentTimeMillis()
                            storyResults[ownerUid] = snapshot?.documents.orEmpty().mapNotNull { doc ->
                                val expires = doc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                                if (expires > now) doc.data?.plus("id" to doc.id) else null
                            }
                            stories = storyResults.values.flatten()
                                .sortedByDescending { (it["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L }
                        }
                }
            }
        }
        val directoryListener = db.collection("profileDirectory").document(uid)
            .addSnapshotListener { snap, _ -> snap?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it } }
        val publicProfileListener = db.collection("publicProfiles").document(uid)
            .addSnapshotListener { snap, _ -> snap?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it } }
        val userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ -> snap?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it } }
        onDispose {
            audienceRegistrations.forEach { it.remove() }
            storyOwnerRegistrations.values.forEach { it.remove() }
            directoryListener.remove()
            publicProfileListener.remove()
            userListener.remove()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(178.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                onClick = { showCreate = true },
                modifier = Modifier.width(116.dp).fillMaxHeight(),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFF27B9F4)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2238))
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (profilePhotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = profilePhotoUrl,
                                contentDescription = "Your profile photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Your profile photo",
                                    tint = RoyalBlue, modifier = Modifier.size(64.dp))
                            }
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 19.dp).size(44.dp),
                            shape = CircleShape,
                            color = RoyalBlue,
                            border = BorderStroke(3.dp, Color.White),
                            shadowElevation = 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Create story",
                                    tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Create story", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            stories.forEach { story ->
                StoryPreviewCard(story = story, onClick = { viewingStory = story })
            }
        }
    }

    if (showCreate) CreateStoryDialog(uid, displayName, onDismiss = { showCreate = false })
    viewingStory?.let { story ->
        var replyText by remember(story["id"]) { mutableStateOf("") }
        var showStoryMenu by remember(story["id"]) { mutableStateOf(false) }
        var showAllStoryIcons by remember(story["id"]) { mutableStateOf(false) }
        var actionMessage by remember(story["id"]) { mutableStateOf("") }
        val storyOwnerUid = story["uid"] as? String ?: ""
        val storyId = story["id"] as? String ?: ""
        val secureStoryUrl = rememberSecureMediaUrl(
            "story", storyId, story["storagePath"] as? String ?: "", story["mediaUrl"] as? String ?: ""
        )
        val storyProgress = remember(storyId) { androidx.compose.animation.core.Animatable(0f) }
        val orderedStories = remember(stories) {
            stories.groupBy { it["uid"] as? String ?: "" }.values.flatten()
        }
        val advanceStory = {
            val currentIndex = orderedStories.indexOfFirst { (it["id"] as? String) == storyId }
            viewingStory = orderedStories.getOrNull(currentIndex + 1)
        }
        LaunchedEffect(storyId) {
            storyProgress.snapTo(0f)
            storyProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 10_000, easing = androidx.compose.animation.core.LinearEasing)
            )
            advanceStory()
        }
        val storyPhotoUrl = remember(storyOwnerUid) { mutableStateOf("") }
        fun sendStoryReply() {
            val reply = replyText.trim()
            if (reply.isBlank()) return
            if (storyOwnerUid.isBlank() || storyOwnerUid == uid) {
                actionMessage = if (storyOwnerUid == uid) "You cannot message yourself about your own story." else "Story owner could not be identified."
                return
            }
            val chatId = listOf(uid, storyOwnerUid).sorted().joinToString("_")
            val chatRef = db.collection("chats").document(chatId)
            val chatMessage = "↩️ Replied to your story: $reply"
            replyText = ""
            actionMessage = "Sending..."
            chatRef.set(
                mapOf(
                    "participantIds" to listOf(uid, storyOwnerUid).sorted(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).onSuccessTask {
                chatRef.collection("messages").add(hashMapOf(
                    "uid" to uid,
                    "text" to chatMessage,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            }.addOnSuccessListener {
                chatRef.set(
                    mapOf(
                        "lastMessage" to chatMessage,
                        "lastMessageAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "lastSenderUid" to uid,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                actionMessage = "Message sent to chat"
            }.addOnFailureListener {
                replyText = reply
                actionMessage = it.localizedMessage ?: "Message could not be sent to chat."
            }
        }
        DisposableEffect(storyOwnerUid) {
            fun updatePhoto(snapshot: com.google.firebase.firestore.DocumentSnapshot?) {
                snapshot?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { storyPhotoUrl.value = it }
            }
            val directoryReg = db.collection("profileDirectory").document(storyOwnerUid)
                .addSnapshotListener { snap, _ -> updatePhoto(snap) }
            val publicReg = db.collection("publicProfiles").document(storyOwnerUid)
                .addSnapshotListener { snap, _ -> updatePhoto(snap) }
            val userReg = db.collection("users").document(storyOwnerUid)
                .addSnapshotListener { snap, _ -> updatePhoto(snap) }
            onDispose {
                directoryReg.remove()
                publicReg.remove()
                userReg.remove()
            }
        }
        Dialog(
            onDismissRequest = { viewingStory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (story["mediaType"] == "video") {
                    AndroidView(
                        factory = { viewContext ->
                            VideoView(viewContext).apply {
                                setVideoURI(Uri.parse(secureStoryUrl))
                                setOnPreparedListener { player -> player.isLooping = true; start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = secureStoryUrl,
                        contentDescription = "Full-screen story photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                // Tap anywhere on the story canvas to advance; controls drawn above remain interactive.
                Box(
                    Modifier.fillMaxSize().clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { advanceStory() }
                )
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.20f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (showAllStoryIcons) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            stories.forEach { item ->
                                StoryAvatarButton(
                                    story = item,
                                    selected = (item["id"] as? String) == storyId,
                                    onClick = { viewingStory = item }
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.48f))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(storyProgress.value).fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF168CFF))
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(38.dp), shape = CircleShape, color = Color.DarkGray) {
                            if (storyPhotoUrl.value.isNotBlank()) AsyncImage(model = storyPhotoUrl.value,
                                contentDescription = "Story author profile photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.AccountCircle, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(story["authorName"] as? String ?: "PPA Talk user", color = Color.White,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(story["createdAt"]?.let { value ->
                            val date = (value as? com.google.firebase.Timestamp)?.toDate()
                            date?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(it) } ?: ""
                        } ?: "", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = { showAllStoryIcons = !showAllStoryIcons }) {
                            Icon(
                                if (showAllStoryIcons) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showAllStoryIcons) "Hide all stories" else "Show all stories",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Box {
                            IconButton(onClick = { showStoryMenu = true }) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = "Story options", tint = Color.White)
                            }
                            DropdownMenu(expanded = showStoryMenu, onDismissRequest = { showStoryMenu = false }) {
                                DropdownMenuItem(text = { Text("Share story") }, onClick = {
                                    showStoryMenu = false
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, secureStoryUrl.ifBlank { "PPA Talk Story" })
                                    }
                                    shareContext.startActivity(Intent.createChooser(send, "Share story"))
                                })
                                DropdownMenuItem(text = { Text("Close") }, onClick = { showStoryMenu = false; viewingStory = null })
                            }
                        }
                    }
                }
                val musicTitle = story["musicTitle"] as? String ?: story["music"] as? String ?: ""
                if (musicTitle.isNotBlank()) {
                    Surface(Modifier.align(Alignment.TopStart).padding(top = 92.dp, start = 14.dp),
                        color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(musicTitle, color = Color.White, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(54.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, story["mediaUrl"] as? String ?: "")
                                }
                                shareContext.startActivity(Intent.createChooser(send, "Share story"))
                            },
                            modifier = Modifier.size(54.dp).background(Color(0xFF484848), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share story",
                                tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier.weight(1f).height(54.dp),
                            placeholder = { Text("Send message...", color = Color.White.copy(alpha = 0.85f), maxLines = 1) },
                            singleLine = true,
                            shape = RoundedCornerShape(30.dp),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                                sendStoryReply()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                cursorColor = Color.White, focusedContainerColor = Color(0xFF484848),
                                unfocusedContainerColor = Color(0xFF484848)
                            )
                        )
                        listOf(Icons.Default.Favorite, Icons.Default.ThumbUp, Icons.Default.EmojiEmotions).forEachIndexed { index, icon ->
                            IconButton(
                                onClick = {
                                    val emoji = listOf("❤️", "👍", "😂")[index]
                                    db.collection("storyReactions").add(hashMapOf(
                                        "storyId" to storyId, "storyOwnerUid" to storyOwnerUid,
                                        "fromUid" to uid, "emoji" to emoji,
                                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    )).addOnSuccessListener { actionMessage = "Reaction sent" }
                                        .addOnFailureListener { actionMessage = it.localizedMessage ?: "Reaction send nahi hui." }
                                },
                                modifier = Modifier.size(54.dp).background(
                                    listOf(Color(0xFFE93672), Color(0xFF087FE5), Color(0xFFFFC84A))[index],
                                    CircleShape
                                )
                            ) {
                                Icon(icon, contentDescription = listOf("Love reaction", "Like reaction", "Laugh reaction")[index],
                                    tint = if (index == 2) Color(0xFF38220A) else Color.White,
                                    modifier = Modifier.size(31.dp))
                            }
                        }
                    }
                    if (actionMessage.isNotBlank()) Text(actionMessage, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun StoryPreviewCard(story: Map<String, Any>, onClick: () -> Unit) {
    val secureStoryUrl = rememberSecureMediaUrl(
        "story", story["id"] as? String ?: "", story["storagePath"] as? String ?: "",
        story["mediaUrl"] as? String ?: ""
    )
    var profilePhotoUrl by remember(story["uid"]) { mutableStateOf("") }
    val storyUid = story["uid"] as? String ?: ""
    DisposableEffect(storyUid) {
        if (storyUid.isBlank()) return@DisposableEffect onDispose { }
        val db = FirebaseFirestore.getInstance()
        fun updatePhoto(profile: com.google.firebase.firestore.DocumentSnapshot?) {
            profile?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it }
        }
        val directoryReg = db.collection("profileDirectory").document(storyUid)
            .addSnapshotListener { profile, _ -> updatePhoto(profile) }
        val publicReg = db.collection("publicProfiles").document(storyUid)
            .addSnapshotListener { profile, _ -> updatePhoto(profile) }
        val userReg = db.collection("users").document(storyUid)
            .addSnapshotListener { profile, _ -> updatePhoto(profile) }
        onDispose {
            directoryReg.remove()
            publicReg.remove()
            userReg.remove()
        }
    }
    Card(onClick = onClick, modifier = Modifier.width(116.dp).fillMaxHeight(),
        shape = RoundedCornerShape(22.dp), border = BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF12D4F5), Color(0xFF8A2BE2), Color(0xFFFFA51E)))),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2238))) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
            AsyncImage(model = secureStoryUrl, contentDescription = "Story",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(42.dp),
                shape = CircleShape, color = RoyalBlue, border = BorderStroke(3.dp, Brush.linearGradient(listOf(Color.Cyan, Color.Magenta, Color(0xFFFFA51E))))
            ) {
                if (profilePhotoUrl.isNotBlank()) AsyncImage(model = profilePhotoUrl,
                    contentDescription = "Story author profile photo",
                    modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.AccountCircle, contentDescription = "Profile photo",
                    tint = Color.White, modifier = Modifier.padding(3.dp))
            }
            Text(story["authorName"] as? String ?: "PPA Talk user",
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f)).padding(5.dp),
                color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun StoryAvatarButton(story: Map<String, Any>, selected: Boolean, onClick: () -> Unit) {
    var photoUrl by remember(story["uid"]) { mutableStateOf("") }
    val storyUid = story["uid"] as? String ?: ""
    DisposableEffect(storyUid) {
        if (storyUid.isBlank()) return@DisposableEffect onDispose { }
        val db = FirebaseFirestore.getInstance()
        fun update(snapshot: com.google.firebase.firestore.DocumentSnapshot?) {
            snapshot?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { photoUrl = it }
        }
        val a = db.collection("profileDirectory").document(storyUid).addSnapshotListener { snap, _ -> update(snap) }
        val b = db.collection("publicProfiles").document(storyUid).addSnapshotListener { snap, _ -> update(snap) }
        val c = db.collection("users").document(storyUid).addSnapshotListener { snap, _ -> update(snap) }
        onDispose { a.remove(); b.remove(); c.remove() }
    }
    Box(
        Modifier.size(76.dp).clip(CircleShape)
            .border(if (selected) 3.dp else 2.dp, Color(0xFF168CFF), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(model = photoUrl, contentDescription = "Story profile photo",
                modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Color.DarkGray, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AccountCircle, contentDescription = "Profile photo", tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }
    }
}

@Composable
fun CreateStoryDialog(uid: String, displayName: String, onDismiss: () -> Unit, publishAsReel: Boolean = false) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedRemoteUrl by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("") }
    var selectedStoryFilter by remember { mutableStateOf("Original") }
    var storyBrightness by remember { mutableFloatStateOf(0f) }
    var storyContrast by remember { mutableFloatStateOf(1f) }
    var storySaturation by remember { mutableFloatStateOf(1f) }
    val storyContext = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showSourceChooser by remember { mutableStateOf(true) }
    var showGalleryOptions by remember { mutableStateOf(true) }
    var showProfileMedia by remember { mutableStateOf(false) }
    var showCameraOptions by remember { mutableStateOf(false) }
    var showTextStoryDialog by remember { mutableStateOf(false) }
    var textStoryValue by remember { mutableStateOf("") }
    var profileMediaLoading by remember { mutableStateOf(false) }
    var profileMedia by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var profileMediaFilter by remember { mutableStateOf("All") }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            selectedUri = uri
            selectedRemoteUrl = ""
            val mimeType = storyContext.contentResolver.getType(uri).orEmpty().lowercase()
            mediaType = if (mimeType.startsWith("video/")) "video" else "image"
            if (publishAsReel && mediaType != "video") {
                selectedUri = null
                message = "Reel ke liye video select karein."
                return@rememberLauncherForActivityResult
            }
            selectedStoryFilter = "Original"
            storyBrightness = 0f; storyContrast = 1f; storySaturation = 1f
            message = ""
            showSourceChooser = false
        }
    }
    val openGallery = {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = if (publishAsReel) "video/*" else "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            if (!publishAsReel) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        galleryPicker.launch(intent)
    }
    LaunchedEffect(Unit) { openGallery() }
    val photoCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val file = java.io.File(storyContext.cacheDir, "story_camera_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            selectedUri = Uri.fromFile(file)
            selectedRemoteUrl = ""
            mediaType = "image"
            selectedStoryFilter = "Original"
            storyBrightness = 0f; storyContrast = 1f; storySaturation = 1f
            showSourceChooser = false
        }
    }
    val videoCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            selectedUri = uri
            selectedRemoteUrl = ""
            mediaType = "video"
            showSourceChooser = false
            message = ""
        }
    }

    fun loadProfileMedia() {
        profileMediaLoading = true
        profileMedia = emptyList()
        val db = FirebaseFirestore.getInstance()
        val items = mutableListOf<Map<String, String>>()
        db.collection("publicProfiles").document(uid).get()
            .addOnSuccessListener { profile ->
                profile.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let {
                    items.add(mapOf("url" to it, "type" to "image", "title" to "Profile photo", "source" to "Profile"))
                }
                profile.getString("coverPhotoUrl")?.takeIf { it.isNotBlank() }?.let {
                    items.add(mapOf("url" to it, "type" to "image", "title" to "Cover photo", "source" to "Profile"))
                }
                db.collection("posts").whereEqualTo("uid", uid).get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { doc ->
                            val url = doc.getString("mediaUrl").orEmpty()
                            if (url.isNotBlank()) {
                                val type = doc.getString("mediaType").orEmpty().lowercase()
                                val postType = doc.getString("postType").orEmpty().lowercase()
                                val isVideo = type.contains("video") || postType == "reel" || postType == "reels"
                                items.add(mapOf(
                                    "url" to url,
                                    "type" to if (isVideo) "video" else "image",
                                    "title" to (if (isVideo) "Reel" else "Photo"),
                                    "source" to if (isVideo) "Reels" else "Photos"
                                ))
                            }
                        }
                        profileMedia = items.distinctBy { it["url"] }
                        profileMediaLoading = false
                    }
                    .addOnFailureListener { error ->
                        profileMedia = items.distinctBy { it["url"] }
                        profileMediaLoading = false
                        message = error.localizedMessage ?: "Profile media load nahi ho paayi."
                    }
            }
            .addOnFailureListener { error ->
                db.collection("posts").whereEqualTo("uid", uid).get()
                    .addOnSuccessListener { snapshot ->
                        profileMedia = snapshot.documents.mapNotNull { doc ->
                            val url = doc.getString("mediaUrl").orEmpty()
                            if (url.isBlank()) null else {
                                val type = doc.getString("mediaType").orEmpty().lowercase()
                                val postType = doc.getString("postType").orEmpty().lowercase()
                                val isVideo = type.contains("video") || postType == "reel" || postType == "reels"
                                mapOf("url" to url, "type" to if (isVideo) "video" else "image",
                                    "title" to if (isVideo) "Reel" else "Photo",
                                    "source" to if (isVideo) "Reels" else "Photos")
                            }
                        }.distinctBy { it["url"] }
                        profileMediaLoading = false
                    }
                    .addOnFailureListener { profileMediaLoading = false; message = error.localizedMessage ?: "Profile media load nahi ho paayi." }
            }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (!busy) onDismiss() }, enabled = !busy) {
                        Icon(Icons.Default.Close, contentDescription = "Close story creator")
                    }
                    Text(if (publishAsReel) "Create Reel" else "Create story", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showGalleryOptions = !showGalleryOptions }, enabled = !busy) {
                        Icon(Icons.Default.Settings, contentDescription = "Story options")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!publishAsReel) {
                        OutlinedButton(onClick = { showTextStoryDialog = true }, enabled = !busy) {
                            Icon(Icons.Default.TextFields, null); Spacer(Modifier.width(6.dp)); Text("Text")
                        }
                    }
                    OutlinedButton(onClick = { openGallery() }, enabled = !busy) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(if (publishAsReel) "Video Gallery" else "Gallery")
                    }
                    if (!publishAsReel) {
                        OutlinedButton(onClick = { showGalleryOptions = false; showProfileMedia = true; showSourceChooser = false; loadProfileMedia() }, enabled = !busy) {
                            Icon(Icons.Default.Collections, null); Spacer(Modifier.width(6.dp)); Text("PPA Photos / Reels")
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message.isNotBlank()) {
                    Text(
                        message,
                        color = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedUri == null && selectedRemoteUrl.isBlank()) {
                    Text(if (publishAsReel) "Choose a video from your gallery to publish as a Reel."
                        else "Your story will be visible for 24 hours.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(if (selectedRemoteUrl.isNotBlank()) "Selected from PPA Profile" else "Selected from Gallery")
                    if (mediaType == "image") {
                        AsyncImage(
                            model = selectedUri ?: selectedRemoteUrl,
                            contentDescription = "Selected story photo preview",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix(
                                    photoFilterMatrix(selectedStoryFilter, storyBrightness, storyContrast, storySaturation)
                                )
                            )
                        )
                        Text("Edit photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Original", "Natural", "Cinematic", "Portrait", "Vivid", "Warm", "Cool", "B&W").forEach { filter ->
                                Column(
                                    Modifier.width(68.dp).clickable { selectedStoryFilter = filter },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = selectedUri ?: selectedRemoteUrl,
                                        contentDescription = "$filter story filter",
                                        modifier = Modifier.size(58.dp).clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                            androidx.compose.ui.graphics.ColorMatrix(
                                                photoFilterMatrix(filter, storyBrightness, storyContrast, storySaturation)
                                            )
                                        )
                                    )
                                    Text(
                                        filter,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedStoryFilter == filter) RoyalBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Text("Brightness  ${storyBrightness.toInt()}", style = MaterialTheme.typography.labelMedium)
                        Slider(value = storyBrightness, onValueChange = { storyBrightness = it }, valueRange = -0.5f..0.5f)
                        Text("Contrast  ${(storyContrast * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        Slider(value = storyContrast, onValueChange = { storyContrast = it }, valueRange = 0.5f..1.8f)
                        Text("Saturation  ${(storySaturation * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        Slider(value = storySaturation, onValueChange = { storySaturation = it }, valueRange = 0f..2f)
                        TextButton(onClick = {
                            selectedStoryFilter = "Original"
                            storyBrightness = 0f; storyContrast = 1f; storySaturation = 1f
                        }) { Text("Reset edits") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, null, tint = RoyalBlue, modifier = Modifier.size(42.dp))
                            Text("Video selected")
                        }
                    }
                    TextButton(onClick = {
                        selectedUri = null
                        selectedRemoteUrl = ""
                        mediaType = ""
                        selectedStoryFilter = "Original"
                        storyBrightness = 0f; storyContrast = 1f; storySaturation = 1f
                        showSourceChooser = true
                    }, enabled = !busy) { Text("Choose another") }
                }
                }
            }
        }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = { showCameraOptions = true },
                            modifier = Modifier.size(58.dp).background(RoyalBlue, CircleShape),
                            enabled = !busy
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture story photo or video",
                                tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        DropdownMenu(expanded = showCameraOptions, onDismissRequest = { showCameraOptions = false }) {
                            if (!publishAsReel) {
                                DropdownMenuItem(text = { Text("Take photo") }, onClick = {
                                    showCameraOptions = false
                                    photoCaptureLauncher.launch(null)
                                })
                            }
                            DropdownMenuItem(text = { Text("Record video") }, onClick = {
                                showCameraOptions = false
                                videoCaptureLauncher.launch(Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE))
                            })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
Button(
                enabled = (selectedUri != null || selectedRemoteUrl.isNotBlank()) && !busy,
                onClick = {
                    if (publishAsReel && mediaType != "video") {
                        message = "Reel ke liye gallery se video select karein."
                        return@Button
                    }
                    busy = true
                    message = if (publishAsReel) "Preparing Reel upload..." else "Share button received. Preparing story..."
                    val authenticatedUid = FirebaseAuth.getInstance().currentUser?.uid
                    if (authenticatedUid.isNullOrBlank()) {
                        busy = false
                        message = "Session expire ho gaya hai. Dobara login karke story share karein."
                        return@Button
                    }
                    fun saveStory(url: String, storagePath: String = "") {
                        val now = System.currentTimeMillis()
                        // Firestore posts/stories rules require a non-empty author name (max 80 chars).
                        val storyAuthorName = displayName.trim().ifBlank { "PPA Talk user" }.take(80)
                        val reelData = hashMapOf(
                            "uid" to authenticatedUid,
                            "authorName" to storyAuthorName,
                            "text" to "",
                            "mediaUrl" to if (storagePath.isBlank()) url else "",
                            "storagePath" to storagePath,
                            "mediaType" to mediaType,
                            "postType" to "reel",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        val storyData = hashMapOf(
                            "uid" to authenticatedUid,
                            "authorName" to storyAuthorName,
                            "mediaUrl" to if (storagePath.isBlank()) url else "",
                            "storagePath" to storagePath,
                            "mediaType" to mediaType,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "expiresAt" to com.google.firebase.Timestamp(Date(now + 24L * 60L * 60L * 1000L))
                        )
                        val collection = if (publishAsReel) "posts" else "stories"
                        val payload: Map<String, Any> = if (publishAsReel) reelData else storyData
                        FirebaseFirestore.getInstance().collection(collection).add(payload)
                            .addOnSuccessListener { busy = false; onDismiss() }
                            .addOnFailureListener { e ->
                                busy = false
                                message = (if (publishAsReel) "Reel" else "Story") + " save failed: " + (e.localizedMessage ?: "Permission denied")
                            }
                    }
                    fun uploadStoryFile(uri: Uri) {
                        val resolverMime = storyContext.contentResolver.getType(uri)
                        val mimeType = when {
                            mediaType == "image" && uri.scheme == "file" -> "image/jpeg"
                            !resolverMime.isNullOrBlank() -> resolverMime
                            mediaType == "image" -> "image/jpeg"
                            else -> "video/mp4"
                        }
                        val ext = when {
                            mimeType == "image/png" -> "png"
                            mimeType == "image/webp" -> "webp"
                            mimeType.startsWith("video/") -> mimeType.substringAfter("/").substringBefore(";").ifBlank { "mp4" }
                            else -> "jpg"
                        }
                        val storageRef = FirebaseStorage.getInstance().reference
                            .child("${if (publishAsReel) "reels" else "stories"}/$authenticatedUid/${System.currentTimeMillis()}.$ext")
                        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                            .setContentType(mimeType)
                            .build()
                        message = "Uploading media to Firebase Storage..."
                        val uploadTask = storageRef.putFile(uri, metadata)
                        uploadTask.addOnProgressListener { snapshot ->
                            val total = snapshot.totalByteCount.coerceAtLeast(1L)
                            val percent = (100L * snapshot.bytesTransferred / total).toInt()
                            message = "Uploading media... $percent%"
                        }
                        uploadTask.addOnSuccessListener {
                                message = "Media uploaded. Saving story details..."
                                storageRef.downloadUrl.addOnSuccessListener { download ->
                                    saveStory(download.toString(), storageRef.path)
                                }.addOnFailureListener { e ->
                                    busy = false
                                    message = e.localizedMessage ?: "Could not get media URL."
                                }
                            }
                            .addOnFailureListener { e ->
                                busy = false
                                message = e.localizedMessage ?: "Upload failed. Check Storage rules."
                            }
                    }
                    val hasPhotoEdits = mediaType == "image" &&
                        (selectedStoryFilter != "Original" || storyBrightness != 0f ||
                            storyContrast != 1f || storySaturation != 1f)
                    if (selectedRemoteUrl.isNotBlank()) {
                        if (hasPhotoEdits) {
                            FirebaseStorage.getInstance().getReferenceFromUrl(selectedRemoteUrl)
                                .getBytes(20L * 1024L * 1024L)
                                .addOnSuccessListener { bytes ->
                                    val sourceFile = java.io.File(storyContext.cacheDir, "story_source_${System.currentTimeMillis()}.jpg")
                                    sourceFile.writeBytes(bytes)
                                    val edited = createFilteredPhotoUri(
                                        storyContext, Uri.fromFile(sourceFile), selectedStoryFilter,
                                        storyBrightness, storyContrast, storySaturation
                                    )
                                    if (edited == null) {
                                        busy = false
                                        message = "Photo edit apply nahi ho paaya. Original photo try karein."
                                    } else uploadStoryFile(edited)
                                }
                                .addOnFailureListener { e ->
                                    busy = false
                                    message = e.localizedMessage ?: "Profile photo edit ke liye load nahi ho paayi."
                                }
                        } else saveStory(selectedRemoteUrl)
                    } else {
                        val sourceUri = selectedUri
                        if (sourceUri == null) { busy = false; return@Button }
                        if (hasPhotoEdits) {
                            val edited = createFilteredPhotoUri(
                                storyContext, sourceUri, selectedStoryFilter,
                                storyBrightness, storyContrast, storySaturation
                            )
                            if (edited == null) {
                                busy = false
                                message = "Photo edit apply nahi ho paaya. Original photo try karein."
                            } else uploadStoryFile(edited)
                        } else uploadStoryFile(sourceUri)
                    }
                }
            ) { Text(if (busy) "Uploading..." else if (publishAsReel) "Share Reel" else "Share story") }
        }
                }
            }
        }
    if (showTextStoryDialog) {
        AlertDialog(
            onDismissRequest = { showTextStoryDialog = false },
            title = { Text("Text story") },
            text = {
                OutlinedTextField(
                    value = textStoryValue,
                    onValueChange = { textStoryValue = it },
                    placeholder = { Text("What's on your mind?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = textStoryValue.trim()
                    if (text.isNotEmpty()) {
                        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.rgb(35, 73, 150))
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            textSize = 68f
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                        }
                        val lines = text.chunked(24)
                        val lineHeight = 90f
                        val startY = 960f - (lines.size - 1) * lineHeight / 2f
                        lines.forEachIndexed { index, line -> canvas.drawText(line, 540f, startY + index * lineHeight, paint) }
                        val file = java.io.File(storyContext.cacheDir, "story_text_${System.currentTimeMillis()}.jpg")
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                        bitmap.recycle()
                        selectedUri = Uri.fromFile(file)
                        selectedRemoteUrl = ""
                        mediaType = "image"
                        showSourceChooser = false
                        showTextStoryDialog = false
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showTextStoryDialog = false }) { Text("Cancel") } }
        )
    }

    if (showProfileMedia) {
        val visibleItems = profileMedia.filter {
            profileMediaFilter == "All" ||
                (profileMediaFilter == "Photos" && it["type"] == "image") ||
                (profileMediaFilter == "Reels" && it["type"] == "video")
        }
        AlertDialog(
            onDismissRequest = { showProfileMedia = false; showSourceChooser = true },
            title = { Text("Choose from PPA Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Photos", "Reels").forEach { filter ->
                            FilterChip(
                                selected = profileMediaFilter == filter,
                                onClick = { profileMediaFilter = filter },
                                label = { Text(filter) }
                            )
                        }
                    }
                    when {
                        profileMediaLoading -> Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        visibleItems.isEmpty() -> Text("Is section mein abhi koi photo ya reel nahi hai.")
                        else -> LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visibleItems.size) { index ->
                                val item = visibleItems[index]
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selectedRemoteUrl = item["url"].orEmpty()
                                        mediaType = item["type"].orEmpty()
                                        selectedStoryFilter = "Original"
                                        storyBrightness = 0f; storyContrast = 1f; storySaturation = 1f
                                        selectedUri = null
                                        showProfileMedia = false
                                        showSourceChooser = false
                                        message = ""
                                    }.padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = item["url"],
                                        contentDescription = item["title"],
                                        modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item["title"].orEmpty(), fontWeight = FontWeight.SemiBold)
                                        Text(item["source"].orEmpty(), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.AddCircle, contentDescription = "Select for story", tint = RoyalBlue)
                                }
                            }
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { showProfileMedia = false; showSourceChooser = true }) { Text("Back") } }
        )
    }
}


@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
fun FeedPostCard(post: FeedPost, currentUid: String, onOpenAuthorProfile: () -> Unit = {}, onOpenReel: () -> Unit = {},
    onHidePost: () -> Unit = {}, onCommentsClick: () -> Unit) {
    var liked by remember(post.id, currentUid) { mutableStateOf(false) }
    var selectedReaction by remember(post.id, currentUid) { mutableStateOf("") }
    var showReactionPicker by remember(post.id, currentUid) { mutableStateOf(false) }
    var likeCount by remember(post.id) { mutableIntStateOf(0) }
    var commentCount by remember(post.id) { mutableIntStateOf(0) }
    var actionBusy by remember(post.id) { mutableStateOf(false) }
    var friendState by remember(post.uid, currentUid) { mutableStateOf("Add Friend") }
    var friendBusy by remember(post.uid, currentUid) { mutableStateOf(false) }
    var friendMessage by remember(post.uid, currentUid) { mutableStateOf("") }
    var postMenuExpanded by remember(post.id) { mutableStateOf(false) }
    var showPhotoViewer by remember(post.id) { mutableStateOf(false) }
    var showLikesDialog by remember(post.id) { mutableStateOf(false) }
    var feedNotice by remember(post.id) { mutableStateOf("") }
    val db = remember { FirebaseFirestore.getInstance() }
    val secureMediaUrl = rememberSecureMediaUrl(
        if (post.postType == "reel") "reel" else "post", post.id, post.storagePath, post.mediaUrl
    )

    DisposableEffect(post.uid, currentUid) {
        if (post.uid == currentUid) return@DisposableEffect onDispose { }
        val friendRef = db.collection("friends").document(currentUid).collection("list").document(post.uid)
        val sentRef = db.collection("friendRequests").document("${currentUid}_${post.uid}")
        val receivedRef = db.collection("friendRequests").document("${post.uid}_${currentUid}")
        val friendListener = friendRef.addSnapshotListener { snap, _ ->
            if (snap?.exists() == true) friendState = "Friends"
        }
        val sentListener = sentRef.addSnapshotListener { snap, _ ->
            if (snap?.getString("status") == "pending") friendState = "Requested"
            else if (snap?.getString("status") == "accepted") friendState = "Friends"
            else if (friendState != "Friends") friendState = "Add Friend"
        }
        val receivedListener = receivedRef.addSnapshotListener { snap, _ ->
            if (snap?.getString("status") == "pending" && friendState != "Friends") friendState = "Respond"
        }
        onDispose { friendListener.remove(); sentListener.remove(); receivedListener.remove() }
    }

    DisposableEffect(post.id, currentUid) {
        val likesRef = db.collection("posts").document(post.id).collection("likes")
        val likeListener = likesRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                likeCount = snapshot.size()
                val myLike = snapshot.documents.firstOrNull { it.id == currentUid }
                liked = myLike != null
                selectedReaction = myLike?.getString("reaction") ?: if (myLike != null) "👍" else ""
            }
        }
        val commentsListener = db.collection("posts").document(post.id).collection("comments")
            .addSnapshotListener { snapshot, _ -> if (snapshot != null) commentCount = snapshot.size() }
        onDispose { likeListener.remove(); commentsListener.remove() }
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 5.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF173D60)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF06182B), contentColor = Color.White)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant, onClick = onOpenAuthorProfile
                ) {
                    var authorPhotoUrl by remember(post.uid) { mutableStateOf("") }
                    DisposableEffect(post.uid) {
                        val directoryRegistration = db.collection("profileDirectory").document(post.uid)
                            .addSnapshotListener { profile, _ ->
                                profile?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { authorPhotoUrl = it }
                            }
                        val profileRegistration = db.collection("publicProfiles").document(post.uid)
                            .addSnapshotListener { profile, _ ->
                                profile?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { authorPhotoUrl = it }
                            }
                        onDispose { directoryRegistration.remove(); profileRegistration.remove() }
                    }
                    if (authorPhotoUrl.isNotBlank()) AsyncImage(model = authorPhotoUrl,
                        contentDescription = "Open author profile",
                        modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AccountCircle, contentDescription = "Open profile",
                        tint = RoyalBlue, modifier = Modifier.padding(2.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = onOpenAuthorProfile, contentPadding = PaddingValues(0.dp)) {
                        Text(post.authorName, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    if (post.createdAt != null) Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(post.createdAt),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFB9CCDF))
                }
                if (post.uid != currentUid && post.postType == "reel") {
                    OutlinedButton(onClick = onOpenAuthorProfile, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Follow")
                    }
                } else if (post.uid != currentUid && friendState != "Friends") {
                    OutlinedButton(
                        enabled = !friendBusy && friendState in listOf("Add Friend", "Respond", "Requested"),
                        onClick = {
                            friendBusy = true
                            if (friendState == "Requested") {
                                cancelFriendRequestSafely(db, currentUid, post.uid) { ok, detail ->
                                    friendState = if (ok) "Add Friend" else "Requested"
                                    friendMessage = detail
                                    friendBusy = false
                                }
                            } else if (friendState == "Respond") {
                                acceptFriendRequestSafely(db, currentUid, post.uid, "PPA Talk user", post.authorName) { ok, detail ->
                                    friendState = if (ok) "Friends" else "Respond"
                                    friendMessage = detail
                                    friendBusy = false
                                }
                            } else {
                                db.collection("publicProfiles").document(currentUid).get()
                                    .addOnSuccessListener { profile ->
                                        sendFriendRequestSafely(db, currentUid, post.uid,
                                            profile.getString("displayName") ?: "PPA Talk user", post.authorName) { ok, detail ->
                                            if (ok) friendState = "Requested"
                                            friendMessage = detail
                                            friendBusy = false
                                        }
                                    }.addOnFailureListener { error ->
                                        friendMessage = error.localizedMessage ?: "Could not load your profile"
                                        friendBusy = false
                                    }
                            }
                        }
                    ) {
                        if (friendBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(when (friendState) {
                            "Requested" -> "Cancel Request"
                            "Friends" -> "Friends"
                            "Respond" -> "Accept"
                            else -> "Add Friend"
                        })
                    }
                }
                Box {
                    IconButton(onClick = { postMenuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Post options") }
                    DropdownMenu(expanded = postMenuExpanded, onDismissRequest = { postMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Interested") }, leadingIcon = { Icon(Icons.Default.AddCircleOutline, null) },
                            onClick = { postMenuExpanded = false; feedNotice = "We'll show you more posts like this." })
                        DropdownMenuItem(text = { Text("Not interested") }, leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
                            onClick = { postMenuExpanded = false; onHidePost() })
                        DropdownMenuItem(text = { Text(if (post.postType == "reel") "Save reel" else "Save post") },
                            leadingIcon = { Icon(Icons.Default.BookmarkBorder, null) },
                            onClick = {
                                postMenuExpanded = false
                                db.collection("users").document(currentUid).collection("savedPosts").document(post.id)
                                    .set(mapOf("postId" to post.id, "ownerUid" to post.uid,
                                        "savedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                                    .addOnSuccessListener { feedNotice = "Saved." }
                                    .addOnFailureListener { feedNotice = it.localizedMessage ?: "Save failed. Rules update required." }
                            })
                        DropdownMenuItem(text = { Text("Report post or reel") }, leadingIcon = { Icon(Icons.Default.Report, null) },
                            onClick = {
                                postMenuExpanded = false
                                db.collection("postReports").add(mapOf("postId" to post.id, "postOwnerUid" to post.uid,
                                    "reporterUid" to currentUid, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                                    .addOnSuccessListener { feedNotice = "Report submitted." }
                                    .addOnFailureListener { feedNotice = it.localizedMessage ?: "Report failed. Rules update required." }
                            })
                    }
                }
                IconButton(onClick = onHidePost) { Icon(Icons.Default.Close, contentDescription = "Hide post from my feed") }
            }
            if (friendMessage.isNotBlank()) Text(friendMessage, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            if (feedNotice.isNotBlank()) Text(feedNotice, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            if (post.text.isNotBlank()) Text(post.text, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            // Path-backed uploads intentionally store an empty mediaUrl; use storagePath as the media-presence signal.
            if ((post.mediaUrl.isNotBlank() || post.storagePath.isNotBlank()) && post.mediaType == "image") {
                Spacer(Modifier.height(10.dp))
                if (secureMediaUrl.startsWith("MEDIA_ERROR:")) {
                    Text("Photo could not load: " + secureMediaUrl.removePrefix("MEDIA_ERROR:"),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.error)
                } else if (secureMediaUrl.isBlank()) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    AsyncImage(
                        model = secureMediaUrl,
                        contentDescription = "Open post photo full screen",
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                            .clickable { showPhotoViewer = true },
                        contentScale = ContentScale.Fit
                    )
                }
            } else if ((post.mediaUrl.isNotBlank() || post.storagePath.isNotBlank()) && (post.mediaType == "video" || post.postType == "reel")) {
                Spacer(Modifier.height(8.dp))
                var reelMuted by remember(post.id) { mutableStateOf(true) }
                var inlineVideoError by remember(post.id) { mutableStateOf("") }
                var inlineVideoLoading by remember(post.id) { mutableStateOf(true) }
                val inlineVideoContext = LocalContext.current
                val inlinePlayer = remember(post.id) {
                    androidx.media3.exoplayer.ExoPlayer.Builder(inlineVideoContext).build().apply {
                        repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                        volume = 0f
                    }
                }
                DisposableEffect(inlinePlayer) {
                    val listener = object : androidx.media3.common.Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            inlineVideoLoading = false
                            inlineVideoError = error.localizedMessage ?: error.errorCodeName
                            android.util.Log.e("PpaInlineVideo", "Inline video playback error", error)
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == androidx.media3.common.Player.STATE_READY) {
                                inlineVideoLoading = false
                                inlineVideoError = ""
                            } else if (state == androidx.media3.common.Player.STATE_BUFFERING) inlineVideoLoading = true
                        }
                    }
                    inlinePlayer.addListener(listener)
                    onDispose { inlinePlayer.removeListener(listener); inlinePlayer.release() }
                }
                LaunchedEffect(inlinePlayer, secureMediaUrl) {
                    inlineVideoError = ""
                    inlineVideoLoading = true
                    inlinePlayer.stop()
                    inlinePlayer.clearMediaItems()
                    when {
                        secureMediaUrl.startsWith("MEDIA_ERROR:") -> {
                            inlineVideoLoading = false
                            inlineVideoError = secureMediaUrl.removePrefix("MEDIA_ERROR:")
                        }
                        secureMediaUrl.isBlank() -> inlineVideoLoading = true
                        secureMediaUrl.startsWith("https://") || secureMediaUrl.startsWith("http://") -> {
                            inlinePlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(secureMediaUrl))
                            inlinePlayer.prepare()
                            inlinePlayer.playWhenReady = true
                        }
                        else -> { inlineVideoLoading = false; inlineVideoError = "Invalid video URL." }
                    }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).background(Color.Black)
                    .clickable(enabled = post.postType == "reel") {
                        inlinePlayer.pause()
                        onOpenReel()
                    }) {
                    AndroidView(
                        factory = { viewContext ->
                            androidx.media3.ui.PlayerView(viewContext).apply {
                                player = inlinePlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { view -> view.player = inlinePlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (inlineVideoLoading && inlineVideoError.isBlank()) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    if (inlineVideoError.isNotBlank()) {
                        Text("Video unavailable: $inlineVideoError", color = Color.White,
                            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.9f)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp)).padding(12.dp))
                    }
                    IconButton(
                        onClick = {
                            reelMuted = !reelMuted
                            inlinePlayer.volume = if (reelMuted) 0f else 1f
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(if (reelMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (reelMuted) "Unmute reel" else "Mute reel", tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (showReactionPicker) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("👍" to "Like", "❤️" to "Love", "😂" to "Haha", "😢" to "Sad", "😡" to "Angry").forEach { (emoji, label) ->
                        TextButton(
                            enabled = !actionBusy,
                            onClick = {
                                actionBusy = true
                                val ref = db.collection("posts").document(post.id).collection("likes").document(currentUid)
                                if (selectedReaction == emoji) {
                                    ref.delete().addOnCompleteListener { actionBusy = false; showReactionPicker = false }
                                } else {
                                    ref.set(
                                        mapOf(
                                            "uid" to currentUid,
                                            "reaction" to emoji,
                                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                        )
                                    ).addOnCompleteListener { actionBusy = false; showReactionPicker = false }
                                }
                            }
                        ) {
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !actionBusy, onClick = { showReactionPicker = !showReactionPicker }) {
                        if (liked) {
                            Text(selectedReaction.ifBlank { "👍" }, style = MaterialTheme.typography.titleLarge)
                        } else {
                            Icon(Icons.Outlined.ThumbUp, contentDescription = "Like", tint = Color.Black)
                        }
                    }
                    Text(
                        likeCount.toString(),
                        modifier = Modifier.clickable { showLikesDialog = true },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCommentsClick) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", tint = Color.Black)
                    }
                    Text(
                        commentCount.toString(),
                        modifier = Modifier.clickable(onClick = onCommentsClick),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.width(22.dp))
                val context = LocalContext.current
                IconButton(onClick = {
                    val shareText = if (secureMediaUrl.isBlank()) post.text else post.text + "\\n" + secureMediaUrl
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share PPA Talk post"))
                }) {
                    Icon(Icons.Outlined.Reply, contentDescription = "Share", tint = Color.Black, modifier = Modifier.graphicsLayer(scaleX = -1f))
                }
                IconButton(onClick = {
                    db.collection("users").document(currentUid).collection("savedPosts").document(post.id)
                        .set(mapOf("postId" to post.id, "ownerUid" to post.uid,
                            "savedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                        .addOnSuccessListener { feedNotice = "Saved." }
                        .addOnFailureListener { feedNotice = it.localizedMessage ?: "Save failed." }
                }) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "Save reel or post", tint = Color.Black)
                }
                Spacer(Modifier.weight(1f))
                if (post.uid == currentUid) {
                    IconButton(
                        onClick = { db.collection("posts").document(post.id).delete() }
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete post", tint = Color.Black)
                    }
                }
            }
        }
    }
    if (showLikesDialog) PostLikesDialog(post, onDismiss = { showLikesDialog = false })
    if (showPhotoViewer && post.mediaUrl.isNotBlank() && post.mediaType == "image") {
        Dialog(
            onDismissRequest = { showPhotoViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = secureMediaUrl,
                    contentDescription = "Full-screen post photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showPhotoViewer = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close photo", tint = Color.White)
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = post.authorName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}


@Composable
fun PostLikesDialog(post: FeedPost, onDismiss: () -> Unit) {
    var likers by remember(post.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember(post.id) { mutableStateOf(true) }
    DisposableEffect(post.id) {
        val db = FirebaseFirestore.getInstance()
        val registration = db.collection("posts").document(post.id).collection("likes").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val ids = snapshot.documents.map { it.getString("uid").orEmpty().ifBlank { it.id } }.distinct()
                if (ids.isEmpty()) { likers = emptyList(); loading = false }
                else {
                    val profiles = mutableListOf<Pair<String, String>>()
                    var completed = 0
                    ids.forEach { uid ->
                        db.collection("profileDirectory").document(uid).get().addOnSuccessListener { profile ->
                            profiles.add((profile.getString("displayName") ?: "PPA Talk user") to profile.getString("photoUrl").orEmpty())
                        }.addOnCompleteListener {
                            completed++
                            if (completed == ids.size) { likers = profiles.toList(); loading = false }
                        }
                    }
                }
            }
        }
        onDispose { registration.remove() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("People who reacted") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                if (loading) CircularProgressIndicator()
                else if (likers.isEmpty()) Text("No reactions yet.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(likers) { (name, photo) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (photo.isNotBlank()) AsyncImage(model = photo, contentDescription = null,
                                modifier = Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.width(10.dp)); Text(name, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f)); Text("👍", color = RoyalBlue)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun PostCommentsDialog(post: FeedPost, currentUid: String, onDismiss: () -> Unit) {
    val commentReactionContext = LocalContext.current
    var comments by remember(post.id) { mutableStateOf<List<FeedComment>>(emptyList()) }
    var commentText by remember(post.id) { mutableStateOf("") }
    var replyTo by remember(post.id) { mutableStateOf<FeedComment?>(null) }
    var sending by remember(post.id) { mutableStateOf(false) }
    var sendError by remember(post.id) { mutableStateOf("") }
    var currentName by remember(currentUid) { mutableStateOf("PPA Talk user") }
    var currentPhoto by remember(currentUid) { mutableStateOf("") }
    var sortNewest by remember(post.id) { mutableStateOf(false) }
    var sortMenu by remember(post.id) { mutableStateOf(false) }
    var likes by remember(post.id) { mutableIntStateOf(0) }
    val db = remember { FirebaseFirestore.getInstance() }
    DisposableEffect(post.id) {
        val commentsReg = db.collection("posts").document(post.id).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener { snapshot, _ ->
                if (snapshot != null) comments = snapshot.documents.map { doc ->
                    FeedComment(doc.id, doc.getString("uid") ?: "", doc.getString("authorName") ?: "PPA Talk user",
                        doc.getString("text") ?: "", doc.getString("parentId") ?: "", doc.getTimestamp("createdAt")?.toDate())
                }
            }
        val likesReg = db.collection("posts").document(post.id).collection("likes")
            .addSnapshotListener { snapshot, _ -> if (snapshot != null) likes = snapshot.size() }
        onDispose { commentsReg.remove(); likesReg.remove() }
    }
    LaunchedEffect(currentUid) {
        db.collection("publicProfiles").document(currentUid).get().addOnSuccessListener {
            currentName = it.getString("displayName") ?: currentName
            currentPhoto = it.getString("photoUrl").orEmpty()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("👍 $likes", color = RoyalBlue, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("Comments", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.clickable { sortMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (sortNewest) "Newest" else "Most relevant", fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("Most relevant") }, onClick = { sortNewest = false; sortMenu = false })
                        DropdownMenuItem(text = { Text("Newest") }, onClick = { sortNewest = true; sortMenu = false })
                    }
                }
                val topLevel = comments.filter { it.parentId.isBlank() }
                val ordered = if (sortNewest) topLevel.sortedByDescending { it.createdAt } else topLevel
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (ordered.isEmpty()) item { Text("No comments yet. Be the first to comment!") }
                    items(ordered, key = { it.id }) { comment ->
                        var authorPhoto by remember(comment.uid) { mutableStateOf("") }
                        var myCommentReaction by remember(comment.id, currentUid) { mutableStateOf("") }
                        DisposableEffect(comment.id, currentUid) {
                            val reactionReg = db.collection("posts").document(post.id).collection("comments")
                                .document(comment.id).collection("reactions").document(currentUid)
                                .addSnapshotListener { snap, _ -> myCommentReaction = snap?.getString("reaction").orEmpty() }
                            onDispose { reactionReg.remove() }
                        }
                        LaunchedEffect(comment.uid) {
                            db.collection("profileDirectory").document(comment.uid).get()
                                .addOnSuccessListener { authorPhoto = it.getString("photoUrl").orEmpty() }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                if (authorPhoto.isNotBlank()) AsyncImage(model = authorPhoto, contentDescription = null,
                                    modifier = Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(comment.authorName, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(comment.createdAt?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it) } ?: "",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(comment.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { replyTo = comment }) { Text("Reply") }
                                        val count = comments.count { it.parentId == comment.id }
                                        if (count > 0) TextButton(onClick = { replyTo = comment }) {
                                            Text("View $count replies")
                                        }
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick = {
                                            val reactionRef = db.collection("posts").document(post.id).collection("comments").document(comment.id).collection("reactions").document(currentUid)
                                            if (myCommentReaction == "👍") reactionRef.delete()
                                            else reactionRef.set(mapOf("uid" to currentUid, "reaction" to "👍"))
                                                .addOnFailureListener { e -> Toast.makeText(commentReactionContext, "Like failed: ${e.localizedMessage ?: e.toString()}", Toast.LENGTH_LONG).show() }
                                        }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ThumbUpOffAlt, contentDescription = "Like comment", tint = if (myCommentReaction == "👍") RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant) }
                                        IconButton(onClick = {
                                            val reactionRef = db.collection("posts").document(post.id).collection("comments").document(comment.id).collection("reactions").document(currentUid)
                                            if (myCommentReaction == "👎") reactionRef.delete()
                                            else reactionRef.set(mapOf("uid" to currentUid, "reaction" to "👎"))
                                                .addOnFailureListener { e -> Toast.makeText(commentReactionContext, "Dislike failed: ${e.localizedMessage ?: e.toString()}", Toast.LENGTH_LONG).show() }
                                        }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ThumbDownOffAlt, contentDescription = "Dislike comment", tint = if (myCommentReaction == "👎") Orange else MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                            comments.filter { it.parentId == comment.id }.forEach { reply ->
                                Row(Modifier.padding(start = 52.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = Orange)
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(reply.authorName, fontWeight = FontWeight.SemiBold)
                                        Text(reply.text)
                                        TextButton(onClick = { replyTo = reply }) { Text("Reply") }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("❤️", "🙌", "🔥", "👏", "😢", "😍", "😮", "😂").forEach { emoji ->
                        TextButton(onClick = { commentText += emoji }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                if (replyTo != null) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Replying to " + replyTo!!.authorName, modifier = Modifier.weight(1f), color = RoyalBlue)
                        TextButton(onClick = { replyTo = null }) { Text("Cancel") }
                    }
                }
                if (sendError.isNotBlank()) Text("Comment failed: $sendError", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentPhoto.isNotBlank()) AsyncImage(model = currentPhoto, contentDescription = null,
                        modifier = Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = commentText, onValueChange = { commentText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Comment as $currentName") }, shape = CircleShape, maxLines = 3)
                    IconButton(enabled = !sending && commentText.isNotBlank() && commentText.trim().length <= 1000, onClick = {
                        sending = true
                        sendError = ""
                        val data = hashMapOf("uid" to currentUid, "authorName" to currentName, "text" to commentText.trim(),
                            "parentId" to (replyTo?.id ?: ""), "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                        db.collection("posts").document(post.id).collection("comments").add(data)
                            .addOnSuccessListener { commentText = ""; replyTo = null; sending = false }
                            .addOnFailureListener { error -> sending = false; sendError = error.localizedMessage ?: error.toString() }
                    }) { Icon(Icons.Default.Send, contentDescription = "Send comment", tint = RoyalBlue) }
                }
            }
        }
    }
}

private fun photoFilterMatrix(
    filter: String, brightness: Float = 0f, contrast: Float = 1f, saturation: Float = 1f
): FloatArray {
    val matrix = ColorMatrix()
    when (filter) {
        "Natural" -> matrix.setSaturation(1.08f)
        "Cinematic" -> matrix.set(floatArrayOf(1.12f,0f,0f,0f,3f, 0f,1.04f,0f,0f,0f, 0f,0f,0.92f,0f,5f, 0f,0f,0f,1f,0f))
        "Portrait" -> matrix.setSaturation(0.92f)
        "Vivid" -> matrix.setSaturation(1.35f)
        "Warm" -> matrix.set(floatArrayOf(1f,0f,0f,0f,18f, 0f,1f,0f,0f,5f, 0f,0f,1f,0f,-12f, 0f,0f,0f,1f,0f))
        "Cool" -> matrix.set(floatArrayOf(1f,0f,0f,0f,-10f, 0f,1f,0f,0f,3f, 0f,0f,1f,0f,16f, 0f,0f,0f,1f,0f))
        "B&W" -> matrix.setSaturation(0f)
        "Soft" -> matrix.setSaturation(0.78f)
        "Drama" -> matrix.set(floatArrayOf(1.18f,0f,0f,0f,-18f, 0f,1.18f,0f,0f,-18f, 0f,0f,1.18f,0f,-18f, 0f,0f,0f,1f,0f))
    }
    matrix.postConcat(ColorMatrix().apply { setSaturation(saturation) })
    val offset = 128f * (1f - contrast) + brightness * 255f
    matrix.postConcat(ColorMatrix(floatArrayOf(
        contrast,0f,0f,0f,offset, 0f,contrast,0f,0f,offset,
        0f,0f,contrast,0f,offset, 0f,0f,0f,1f,0f
    )))
    return matrix.array
}

private fun createFilteredPhotoUri(
    context: android.content.Context, source: Uri, filter: String,
    brightness: Float, contrast: Float, saturation: Float
): Uri? {
    return runCatching {
        val original = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(photoFilterMatrix(filter, brightness, contrast, saturation)))
        }
        canvas.drawBitmap(original, 0f, 0f, paint)
        val file = java.io.File(context.cacheDir, "ppa_edited_" + System.currentTimeMillis() + ".jpg")
        file.outputStream().use { stream -> output.compress(Bitmap.CompressFormat.JPEG, 94, stream) }
        original.recycle()
        output.recycle()
        Uri.fromFile(file)
    }.getOrNull()
}

@Composable
fun CreatePostScreen(
    uid: String, displayName: String, email: String, modifier: Modifier = Modifier,
    initialPicker: String? = null, onPublished: () -> Unit = {}, onClose: () -> Unit = {},
    onReelUploadProgress: (String, Int) -> Unit = { _, _ -> }
) {
    var postText by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf("") }
    var selectedPhotoFilter by remember { mutableStateOf("Original") }
    var selectedPhotoTool by remember { mutableStateOf("AI Studio") }
    var photoBrightness by remember { mutableFloatStateOf(0f) }
    var photoContrast by remember { mutableFloatStateOf(1f) }
    var photoSaturation by remember { mutableFloatStateOf(1f) }
    var showPostCropDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }
    var showReelCreator by remember { mutableStateOf(false) }
    var videoMuted by remember { mutableStateOf(false) }
    var videoPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var reelStage by remember { mutableStateOf("editor") }
    var reelTool by remember { mutableStateOf("Edit") }
    var reelEffect by remember { mutableStateOf("Original") }
    var reelOverlayText by remember { mutableStateOf("") }
    var reelSticker by remember { mutableStateOf("") }
    var reelShareToStory by remember { mutableStateOf(false) }
    var reelAudience by remember { mutableStateOf("Public") }
    var reelLocation by remember { mutableStateOf("") }
    var reelTags by remember { mutableStateOf("") }
    var reelShareToGroups by remember { mutableStateOf(true) }
    var reelAiLabel by remember { mutableStateOf(false) }
    var reelBoost by remember { mutableStateOf(false) }
    var reelAffiliateLink by remember { mutableStateOf("") }
    var reelPlaylist by remember { mutableStateOf("") }
    var reelClosedCaptions by remember { mutableStateOf(true) }
    var reelSettingsExpanded by remember { mutableStateOf(false) }
    var reelSettingsNotice by remember { mutableStateOf("") }
    val isReelComposer = initialPicker == "media" || initialPicker == "video"
    val context = LocalContext.current
    var deviceGalleryItems by remember { mutableStateOf<List<DeviceGalleryMedia>>(emptyList()) }
    var galleryPermissionGranted by remember { mutableStateOf(false) }
    var gallerySelectMultiple by remember { mutableStateOf(false) }
    var gallerySelectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        galleryPermissionGranted = result.values.any { it }
        if (galleryPermissionGranted) deviceGalleryItems = runCatching { loadDeviceGallery(context) }.getOrDefault(emptyList())
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMedia = uri
        mediaType = if (uri == null) "" else "image"
        selectedPhotoFilter = "Original"
        selectedPhotoTool = "AI Studio"
        photoBrightness = 0f; photoContrast = 1f; photoSaturation = 1f
        message = ""
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMedia = uri
        mediaType = if (uri == null) "" else "video"
        selectedPhotoFilter = "Original"
        selectedPhotoTool = "Edit"
        photoBrightness = 0f; photoContrast = 1f; photoSaturation = 1f
        message = ""
    }
    val mixedMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMedia = uri
        mediaType = if (uri == null) "" else {
            context.contentResolver.getType(uri)?.let { if (it.startsWith("video/")) "video" else "image" } ?: "image"
        }
        selectedPhotoFilter = "Original"
        selectedPhotoTool = if (mediaType == "video") "Edit" else "AI Studio"
        photoBrightness = 0f; photoContrast = 1f; photoSaturation = 1f
        message = ""
    }

    LaunchedEffect(initialPicker) {
        when (initialPicker) {
            "image" -> imagePicker.launch("image/*")
            "video" -> videoPicker.launch("video/*")
            "media" -> {
                val permissions = when {
                    Build.VERSION.SDK_INT >= 34 -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                    Build.VERSION.SDK_INT >= 33 -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                val alreadyAllowed = permissions.any { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
                if (alreadyAllowed) {
                    galleryPermissionGranted = true
                    deviceGalleryItems = runCatching { loadDeviceGallery(context) }.getOrDefault(emptyList())
                } else mediaPermissionLauncher.launch(permissions)
            }
        }
    }

    if (initialPicker == "media" && selectedMedia == null) {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close gallery") }
                        Text("Create reel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { message = "Reel settings" }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Camera", "Music", "Templates", "Drafts", "Green screen").forEach { action ->
                            Column(Modifier.width(76.dp).clickable {
                                message = when (action) {
                                    "Camera" -> "Camera capture will be available here soon."
                                    "Music" -> "Choose music after selecting a video."
                                    "Templates" -> "Reel templates are coming soon."
                                    "Drafts" -> "No saved drafts yet."
                                    else -> "Green screen tools are coming soon."
                                }
                            }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center) {
                                    Icon(when (action) {
                                        "Camera" -> Icons.Default.CameraAlt
                                        "Music" -> Icons.Default.MusicNote
                                        "Templates" -> Icons.Default.LibraryAdd
                                        "Drafts" -> Icons.Default.Drafts
                                        else -> Icons.Default.Portrait
                                    }, contentDescription = action, modifier = Modifier.size(26.dp))
                                }
                                Text(action, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
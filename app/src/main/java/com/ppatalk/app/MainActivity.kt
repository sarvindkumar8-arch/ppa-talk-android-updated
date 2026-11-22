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
import android.media.MediaPlayer
import android.content.Intent
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

private fun firebaseStorageObjectPath(value: String): String? {
    if (value.startsWith("profile_photos/") || value.startsWith("cover_photos/")) return value
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    val segments = uri.pathSegments
    val objectIndex = segments.indexOf("o")
    if (objectIndex < 0 || objectIndex + 1 >= segments.size) return null
    return runCatching { Uri.decode(segments[objectIndex + 1]) }.getOrNull()
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
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.takeIf { it.phoneNumber != null || it.isEmailVerified }) }
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
                AuthScreen(onAuthenticated = { user = FirebaseAuth.getInstance().currentUser })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var usePhone by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+91") }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var phoneVerificationId by remember { mutableStateOf<String?>(null) }
    var phoneOtp by remember { mutableStateOf("") }
    var phoneOtpSent by remember { mutableStateOf(false) }
    var phoneResendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
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

    val authFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = Color(0xFF65676B),
        unfocusedPlaceholderColor = Color(0xFF65676B),
        disabledTextColor = Color(0xFF65676B),
        cursorColor = RoyalBlue,
        focusedBorderColor = RoyalBlue,
        unfocusedBorderColor = Color(0xFFCED0D4),
        focusedLabelColor = RoyalBlue,
        unfocusedLabelColor = Color(0xFF65676B),
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PPA Talk", style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold, color = RoyalBlue)
        Text("Create. Connect. Share.", color = Orange)
        Spacer(Modifier.height(24.dp))
        Text(if (isSignUp) "Create your account" else "Welcome back",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !usePhone, onClick = { usePhone = false; message = "" },
                label = { Text("Email") }, modifier = Modifier.weight(1f))
            FilterChip(selected = usePhone, onClick = { usePhone = true; message = "" },
                label = { Text("Mobile") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        if (isSignUp) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface), colors = authFieldColors
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
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = authFieldColors
            )
        } else {
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text("Email address") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            placeholder = { Text(if (isSignUp) "At least 8 characters" else "Enter your password") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = authFieldColors
        )
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                message = ""
                val auth = FirebaseAuth.getInstance()
                if (password.length < 8) {
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
                        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("loginWithPhonePassword").call(payload)
                            .addOnSuccessListener { result ->
                                val data = result.getData() as? Map<*, *>
                                val token = data?.get("token") as? String
                                if (token.isNullOrBlank()) {
                                    busy = false
                                    message = "Authentication server returned an invalid response."
                                } else {
                                    auth.signInWithCustomToken(token)
                                        .addOnSuccessListener { busy = false; onAuthenticated() }
                                        .addOnFailureListener { exception ->
                                            busy = false
                                            message = exception.localizedMessage ?: "Mobile login failed."
                                        }
                                }
                            }
                            .addOnFailureListener { exception ->
                                busy = false
                                message = exception.localizedMessage ?: "Mobile authentication failed. Check backend setup."
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait..." else if (isSignUp) "Sign Up" else "Sign In")
        }

        OutlinedButton(
            onClick = {
                message = ""
                busy = true
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Please wait..." else "Continue with Google") }

        TextButton(onClick = { isSignUp = !isSignUp; message = "" }) {
            Text(if (isSignUp) "Already have an account? Sign In" else "Create account")
        }
        if (!isSignUp && !usePhone) {
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
                }
            ) { Text("Forgot password?") }
        }
        if (message.isNotBlank()) Text(message, color = if (message.startsWith("Password reset link sent")) RoyalBlue else MaterialTheme.colorScheme.error)
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
                    "uid" to uid, "displayName" to displayName.ifBlank { "PPA Talk user" },
                    "profileVisibility" to "public",
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ), com.google.firebase.firestore.SetOptions.merge())
                directoryRef.set(mapOf("uid" to uid, "displayName" to displayName.ifBlank { "PPA Talk user" },
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
                directoryRef.set(mapOf(
                    "uid" to uid,
                    "displayName" to (profile.getString("displayName") ?: displayName.ifBlank { "PPA Talk user" }),
                    "username" to profile.getString("username").orEmpty(),
                    "photoUrl" to profile.getString("photoUrl").orEmpty(),
                    "coverPhotoUrl" to profile.getString("coverPhotoUrl").orEmpty(),
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

    DisposableEffect(uid, displayName) {
        val db = FirebaseFirestore.getInstance()
        db.collection("publicProfiles").document(uid).set(
            mapOf("uid" to uid, "displayName" to displayName.ifBlank { "PPA Talk user" },
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge()
        )
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (activeChatUid == null) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = RoyalBlue,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text("PPA Talk", color = RoyalBlue, fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.headlineSmall)
                            Text("Create. Connect. Share.", color = Orange,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigateTo("Search") }) {
                            Icon(Icons.Default.Search, contentDescription = "Search",
                                modifier = Modifier.size(27.dp))
                        }
                        IconButton(onClick = { navigateTo("Messenger") }) {
                            Surface(shape = CircleShape, color = RoyalBlue, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Forum, contentDescription = "Messenger",
                                        tint = Color.White, modifier = Modifier.size(21.dp))
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = { menuExpanded = false; navigateTo("Settings") })
                                DropdownMenuItem(text = { Text("Log out") },
                                    leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                                    onClick = { menuExpanded = false; onSignOut() })
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Home" to Icons.Default.Home,
                        "Friends" to Icons.Default.PeopleAlt,
                        "Reels" to Icons.Default.PlayCircle,
                        "Marketplace" to Icons.Default.Storefront,
                        "Notifications" to Icons.Default.Notifications,
                        "Menu" to Icons.Default.Menu
                    ).forEach { (label, icon) ->
                        IconButton(onClick = { navigateTo(label) }, modifier = Modifier.weight(1f)) {
                            Icon(icon, contentDescription = label,
                                tint = if (selected == label) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(25.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
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
                onOpenProfile = { viewedProfileUid = uid; navigateTo("Profile") },
                onOpenUserProfile = { profileUid -> viewedProfileUid = profileUid; navigateTo("Profile") },
                displayName = displayName
            )
            "Profile" -> if (viewedProfileUid == uid) {
                ProfileScreen(uid = uid, email = email, initialName = displayName, modifier = Modifier.padding(padding))
            } else {
                PublicProfileScreen(viewedProfileUid, currentUid = uid, currentName = displayName, onOpenChat = { friendUid, friendName ->
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
            "Search" -> FriendsScreen(uid, displayName, { friendUid, friendName ->
                activeChatUid = friendUid
                activeChatName = friendName
            }, Modifier.padding(padding), onOpenProfile = { profileUid ->
                viewedProfileUid = profileUid
                navigateTo("Profile")
            }, onBack = { handleBack() }, screenTitle = "Search Friends")
            "Messenger" -> MessengerScreen(uid, onOpenChat = { friendUid, friendName -> activeChatUid = friendUid; activeChatName = friendName }, modifier = Modifier.padding(padding))
            "Create" -> CreatePostScreen(uid, displayName, email, Modifier.padding(padding))
            "Settings" -> SettingsScreen(isDarkMode, onThemeModeChange, Modifier.padding(padding))
            "Friends" -> FriendsScreen(uid, displayName, { friendUid, friendName ->
                activeChatUid = friendUid
                activeChatName = friendName
            }, Modifier.padding(padding), onOpenProfile = { profileUid ->
                viewedProfileUid = profileUid
                navigateTo("Profile")
            }, onBack = { handleBack() })
            "Reels" -> ReelsScreen(uid, Modifier.padding(padding), onOpenUserProfile = { profileUid ->
                viewedProfileUid = profileUid
                navigateTo("Profile")
            })
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

data class FeedPost(val id: String, val uid: String, val authorName: String, val text: String, val createdAt: Date?, val mediaUrl: String = "", val mediaType: String = "", val postType: String = "post")
data class FeedComment(val id: String, val uid: String, val authorName: String, val text: String, val parentId: String, val createdAt: Date?)
@Composable
fun ReelsScreen(
    currentUid: String,
    modifier: Modifier = Modifier,
    onOpenUserProfile: (String) -> Unit = {}
) {
    var reels by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedReelIndex by remember { mutableIntStateOf(-1) }
    var hiddenReelIds by remember { mutableStateOf(setOf<String>()) }

    DisposableEffect(Unit) {
        val registration = FirebaseFirestore.getInstance().collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage ?: "Reels load nahi ho paayi."
                    loading = false
                } else if (snapshot != null) {
                    reels = snapshot.documents.mapNotNull { doc ->
                        val mediaType = doc.getString("mediaType").orEmpty()
                        val postType = doc.getString("postType").orEmpty()
                        if (mediaType != "video" && postType != "reel") return@mapNotNull null
                        FeedPost(doc.id, doc.getString("uid").orEmpty(),
                            doc.getString("authorName") ?: "PPA Talk user",
                            doc.getString("text").orEmpty(), doc.getTimestamp("createdAt")?.toDate(),
                            doc.getString("mediaUrl").orEmpty(), mediaType, postType)
                    }
                    loading = false
                    error = ""
                }
            }
        onDispose { registration.remove() }
    }

    val visibleReels = reels.filterNot { it.id in hiddenReelIds }
    Column(modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Reels", color = Color.White, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Reels", tint = Color.White)
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            error.isNotBlank() -> Text(error, color = Color.White, modifier = Modifier.padding(16.dp))
            visibleReels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Abhi koi Reel nahi hai. Create Post mein Video / Reel select karke share karein.",
                    color = Color.White, modifier = Modifier.padding(24.dp))
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleReels, key = { it.id }) { reel ->
                    Card(onClick = { selectedReelIndex = visibleReels.indexOfFirst { it.id == reel.id } },
                        modifier = Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF171717))) {
                        Box(Modifier.fillMaxSize()) {
                            AndroidView(factory = { context ->
                                VideoView(context).apply {
                                    setVideoURI(Uri.parse(reel.mediaUrl))
                                    setOnPreparedListener { player -> player.isLooping = true; start() }
                                }
                            }, modifier = Modifier.fillMaxSize())
                            Text("▶  Open Reel", color = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }
    if (selectedReelIndex >= 0 && visibleReels.isNotEmpty()) {
        ReelFullscreenViewer(visibleReels, selectedReelIndex, currentUid,
            onClose = { selectedReelIndex = -1 }, onOpenUserProfile = onOpenUserProfile,
            onHideReel = { id -> hiddenReelIds = hiddenReelIds + id; selectedReelIndex = -1 })
    }
}

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
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val reel = reels.getOrNull(index) ?: return

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
            AndroidView(factory = { viewContext ->
                VideoView(viewContext).apply {
                    setVideoURI(Uri.parse(reel.mediaUrl))
                    setOnPreparedListener { player -> player.isLooping = true; start() }
                }
            }, update = { view -> if (!view.isPlaying) view.start() },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f).align(Alignment.Center))
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.32f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close reels", tint = Color.White)
                }
                Text("Reels", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text("${index + 1}/${reels.size}", color = Color.White)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 34.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, reel.mediaUrl)
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
                .background(Color.Black.copy(alpha = 0.38f)).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)) {
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
            Text("Swipe up for next · Swipe down for previous", color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
        }
    }
    if (showComments) PostCommentsDialog(reel, currentUid) { showComments = false }
}

@Composable
fun HomeFeed(
    currentUid: String,
    modifier: Modifier = Modifier,
    onCreatePost: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    displayName: String = "PPA Talk user"
) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<FeedPost?>(null) }
    var hiddenPostIds by remember { mutableStateOf(setOf<String>()) }
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
                            doc.getString("text") ?: "",
                            doc.getTimestamp("createdAt")?.toDate(), doc.getString("mediaUrl") ?: "",
                            doc.getString("mediaType") ?: "", doc.getString("postType") ?: "post")
                    }
                    loading = false
                    error = ""
                }
            }
        onDispose { registration.remove() }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            HomeComposerCard(
                uid = currentUid,
                onCreatePost = onCreatePost,
                onOpenProfile = onOpenProfile
            )
        }
        item {
            StoriesStrip(uid = currentUid, displayName = displayName)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("News Feed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = RoyalBlue)
                Spacer(Modifier.weight(1f))
                Text("For You", color = RoyalBlue, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (loading) item { CircularProgressIndicator() }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error.isBlank()) item { Text("No posts yet. Be the first to share something!") }
        items(posts.filterNot { it.id in hiddenPostIds }, key = { it.id }) { post ->
            FeedPostCard(post, currentUid, onOpenAuthorProfile = { onOpenUserProfile(post.uid) },
                onHidePost = { hiddenPostIds = hiddenPostIds + post.id }) { selectedPost = post }
        }
    }
    selectedPost?.let { PostCommentsDialog(it, currentUid) { selectedPost = null } }
}


@Composable
fun HomeComposerCard(uid: String, onCreatePost: () -> Unit, onOpenProfile: () -> Unit) {
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
                    color = MaterialTheme.colorScheme.background,
                    onClick = onCreatePost
                ) {
                    Text("What's on your mind?", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCreatePost) {
                    Icon(Icons.Default.Photo, contentDescription = null, tint = RoyalBlue)
                    Spacer(Modifier.width(5.dp))
                    Text("Photo")
                }
                TextButton(onClick = onCreatePost) {
                    Icon(Icons.Default.VideoCall, contentDescription = null, tint = Orange)
                    Spacer(Modifier.width(5.dp))
                    Text("Video")
                }
                TextButton(onClick = onCreatePost) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = RoyalBlue)
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
        val storyListener = db.collection("stories").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val now = System.currentTimeMillis()
                stories = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val expires = doc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                    if (expires > now) doc.data?.plus("id" to doc.id) else null
                }
            }
        val directoryListener = db.collection("profileDirectory").document(uid)
            .addSnapshotListener { snap, _ -> snap?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it } }
        val publicProfileListener = db.collection("publicProfiles").document(uid)
            .addSnapshotListener { snap, _ -> snap?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it } }
        val userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ -> snap?.getString("photoUrl")?.takeIf { it.isNotBlank() }?.let { profilePhotoUrl = it } }
        onDispose {
            storyListener.remove()
            directoryListener.remove()
            publicProfileListener.remove()
            userListener.remove()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().height(210.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                onClick = { showCreate = true },
                modifier = Modifier.width(116.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                setVideoURI(Uri.parse(story["mediaUrl"] as? String ?: ""))
                                setOnPreparedListener { player -> player.isLooping = true; start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = story["mediaUrl"] as? String,
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
                                        putExtra(Intent.EXTRA_TEXT, story["mediaUrl"] as? String ?: "")
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
    Card(onClick = onClick, modifier = Modifier.width(116.dp).fillMaxHeight()) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = story["mediaUrl"] as? String, contentDescription = "Story",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(42.dp),
                shape = CircleShape, color = RoyalBlue, border = BorderStroke(2.dp, Color.White)
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
                    fun saveStory(url: String) {
                        val now = System.currentTimeMillis()
                        val reelData = hashMapOf(
                            "uid" to authenticatedUid,
                            "authorName" to displayName,
                            "text" to "",
                            "mediaUrl" to url,
                            "mediaType" to mediaType,
                            "postType" to "reel",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        val storyData = hashMapOf(
                            "uid" to authenticatedUid,
                            "authorName" to displayName,
                            "mediaUrl" to url,
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
                                    saveStory(download.toString())
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


@Composable
fun FeedPostCard(post: FeedPost, currentUid: String, onOpenAuthorProfile: () -> Unit = {},
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
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        Text(post.authorName, fontWeight = FontWeight.SemiBold)
                    }
                    if (post.createdAt != null) Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(post.createdAt),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = post.mediaUrl,
                    contentDescription = "Open post photo full screen",
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        .clickable { showPhotoViewer = true },
                    contentScale = ContentScale.Fit
                )
            } else if (post.mediaUrl.isNotBlank() && (post.mediaType == "video" || post.postType == "reel")) {
                Spacer(Modifier.height(8.dp))
                var reelMuted by remember(post.id) { mutableStateOf(true) }
                var reelPlayer by remember(post.id) { mutableStateOf<MediaPlayer?>(null) }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(4f / 5f).background(Color.Black)
                ) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                setVideoURI(Uri.parse(post.mediaUrl))
                                setOnPreparedListener { player ->
                                    player.isLooping = true
                                    player.setVolume(0f, 0f)
                                    reelPlayer = player
                                    start()
                                }
                            }
                        },
                        update = { view ->
                            reelPlayer?.setVolume(if (reelMuted) 0f else 1f, if (reelMuted) 0f else 1f)
                            if (!view.isPlaying) view.start()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = {
                            reelMuted = !reelMuted
                            reelPlayer?.setVolume(if (reelMuted) 0f else 1f, if (reelMuted) 0f else 1f)
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            if (reelMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (reelMuted) "Unmute reel" else "Mute reel",
                            tint = Color.White
                        )
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
                    val shareText = if (post.mediaUrl.isBlank()) post.text else post.text + "\\n" + post.mediaUrl
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
                    model = post.mediaUrl,
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
    var comments by remember(post.id) { mutableStateOf<List<FeedComment>>(emptyList()) }
    var commentText by remember(post.id) { mutableStateOf("") }
    var replyTo by remember(post.id) { mutableStateOf<FeedComment?>(null) }
    var sending by remember(post.id) { mutableStateOf(false) }
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
                                            db.collection("posts").document(post.id).collection("comments").document(comment.id)
                                                .collection("reactions").document(currentUid).set(mapOf("uid" to currentUid, "reaction" to "👍"))
                                        }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ThumbUpOffAlt, contentDescription = "Like comment") }
                                        IconButton(onClick = {
                                            db.collection("posts").document(post.id).collection("comments").document(comment.id)
                                                .collection("reactions").document(currentUid).set(mapOf("uid" to currentUid, "reaction" to "👎"))
                                        }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ThumbDownOffAlt, contentDescription = "Dislike comment") }
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentPhoto.isNotBlank()) AsyncImage(model = currentPhoto, contentDescription = null,
                        modifier = Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = commentText, onValueChange = { commentText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Comment as $currentName") }, shape = CircleShape, maxLines = 3)
                    IconButton(enabled = !sending && commentText.isNotBlank() && commentText.trim().length <= 1000, onClick = {
                        sending = true
                        val data = hashMapOf("uid" to currentUid, "authorName" to currentName, "text" to commentText.trim(),
                            "parentId" to (replyTo?.id ?: ""), "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                        db.collection("posts").document(post.id).collection("comments").add(data)
                            .addOnSuccessListener { commentText = ""; replyTo = null; sending = false }
                            .addOnFailureListener { sending = false }
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
fun CreatePostScreen(uid: String, displayName: String, email: String, modifier: Modifier = Modifier) {
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
    val context = LocalContext.current
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { postText = ""; selectedMedia = null; mediaType = ""; message = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            Text("New post", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { showReelCreator = true }, enabled = !busy) {
                Icon(Icons.Default.PlayCircle, contentDescription = "Create Reel", tint = RoyalBlue)
                Text("Reel", color = RoyalBlue)
            }
            TextButton(enabled = !busy && (postText.isNotBlank() || selectedMedia != null) && postText.trim().length <= 5000,
                onClick = { /* Publish using the existing button below. */ }) { Text("Next", color = if (postText.isNotBlank() || selectedMedia != null) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(Icons.Default.AccountCircle, contentDescription = "Your profile", tint = RoyalBlue, modifier = Modifier.padding(4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("♫  Music", "♙  Tag/collaborate", "⌖  Location", "☺  Feeling").forEach { item ->
                SuggestionChip(onClick = { message = "$item option coming soon." }, label = { Text(item) })
            }
        }
        OutlinedTextField(
            value = postText, onValueChange = { postText = it; success = false; message = "" },
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 18.dp),
            placeholder = { Text("What's on your mind?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            maxLines = 12, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent)
        )
        selectedMedia?.let { uri ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (mediaType == "video") "Video selected" else "Photo preview", color = RoyalBlue,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedMedia = null; mediaType = ""; selectedPhotoFilter = "Original"; photoBrightness = 0f; photoContrast = 1f; photoSaturation = 1f }) { Text("Remove") }
            }
            if (mediaType == "image") {
                AsyncImage(
                    model = uri, contentDescription = "Full photo preview",
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (selectedPhotoFilter == "Original") null else
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(photoFilterMatrix(selectedPhotoFilter, photoBrightness, photoContrast, photoSaturation))
                        )
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("AI Enhance", "AI Portrait", "BG Replace", "BG Blur", "Erase", "Filters", "Adjust", "Crop").forEach { tool ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(82.dp).clickable {
                                when (tool) {
                                    "AI Enhance" -> {
                                        selectedPhotoTool = "AI Studio"
                                        selectedPhotoFilter = "Vivid"
                                        photoBrightness = 0.08f; photoContrast = 1.15f; photoSaturation = 1.18f
                                    }
                                    "Filters" -> selectedPhotoTool = "Filters"
                                    "Adjust" -> selectedPhotoTool = "Adjust"
                                    "Crop" -> { showPostCropDialog = true; message = "" }
                                    else -> {
                                        selectedPhotoTool = "AI Studio"
                                        message = when (tool) {
                                            "AI Portrait" -> "AI Portrait — Coming soon!"
                                            "BG Replace" -> "AI Background Replace — Coming soon!"
                                            "BG Blur" -> "AI Background Blur — Coming soon!"
                                            "Erase" -> "AI Object Eraser — Coming soon!"
                                            else -> ""
                                        }
                                    }
                                }
                            }.padding(vertical = 6.dp)) {
                            Icon(when (tool) {
                                "AI Enhance" -> Icons.Default.AutoAwesome
                                "AI Portrait" -> Icons.Default.Face
                                "BG Replace" -> Icons.Default.Image
                                "BG Blur" -> Icons.Default.BlurOn
                                "Erase" -> Icons.Default.AutoFixOff
                                "Filters" -> Icons.Default.FilterVintage
                                "Adjust" -> Icons.Default.Tune
                                else -> Icons.Default.Crop
                            }, contentDescription = tool, tint = if (tool == "AI Enhance") RoyalBlue else MaterialTheme.colorScheme.onSurface)
                            Text(tool, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                if (selectedPhotoTool == "AI Studio") {
                    Text("AI Enhance applies smart on-device adjustments. Portrait, background and object tools need a connected AI service.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                } else if (selectedPhotoTool == "Filters") {
                    Text("Choose a filter", modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Original", "Natural", "Cinematic", "Portrait", "Vivid", "Warm", "Cool", "B&W", "Soft", "Drama").forEach { filter ->
                            Column(
                                modifier = Modifier.width(78.dp).clickable { selectedPhotoFilter = filter },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "$filter filter preview",
                                    modifier = Modifier.fillMaxWidth().height(68.dp),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = if (filter == "Original") null else
                                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                            androidx.compose.ui.graphics.ColorMatrix(photoFilterMatrix(filter, photoBrightness, photoContrast, photoSaturation))
                                        )
                                )
                                Text(
                                    filter,
                                    modifier = Modifier.padding(top = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedPhotoFilter == filter) RoyalBlue else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selectedPhotoFilter == filter) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    Text("Photo adjustments", modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("Brightness  ${(photoBrightness * 100).toInt()}")
                        Slider(value = photoBrightness, onValueChange = { photoBrightness = it }, valueRange = -0.5f..0.5f)
                        Text("Contrast  ${(photoContrast * 100).toInt()}")
                        Slider(value = photoContrast, onValueChange = { photoContrast = it }, valueRange = 0.5f..1.8f)
                        Text("Saturation  ${(photoSaturation * 100).toInt()}")
                        Slider(value = photoSaturation, onValueChange = { photoSaturation = it }, valueRange = 0f..2f)
                        TextButton(onClick = { photoBrightness = 0f; photoContrast = 1f; photoSaturation = 1f }) {
                            Text("Reset adjustments")
                        }
                    }
                }
                Text("Edits and filters will be applied to the uploaded photo.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Video ready to upload. You can publish it as a Reel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f), enabled = !busy) {
                Icon(Icons.Default.Photo, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Gallery")
            }
            OutlinedButton(onClick = { message = "GIF picker coming soon." }, modifier = Modifier.weight(1f), enabled = !busy) {
                Icon(Icons.Default.Gif, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("GIF")
            }
            OutlinedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.weight(1f), enabled = !busy) {
                Icon(Icons.Default.Videocam, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Live")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { message = "Audience: Public" }, label = { Text("🌐 Public") })
            AssistChip(onClick = { message = "Instagram sharing option coming soon." }, label = { Text("◎ Instagram Off") })
            AssistChip(onClick = { message = "Threads sharing option coming soon." }, label = { Text("@ Threads Off") })
        }
        Button(
            enabled = !busy && (postText.isNotBlank() || selectedMedia != null) && postText.trim().length <= 5000,
            onClick = {
                busy = true
                message = ""
                success = false
                val db = FirebaseFirestore.getInstance()
                fun savePost(mediaUrl: String) {
                    val post = hashMapOf(
                        "uid" to uid,
                        "authorName" to displayName,

                        "text" to postText.trim(),
                        "mediaUrl" to mediaUrl,
                        "mediaType" to mediaType,
                        "postType" to if (mediaType == "video") "reel" else "post",
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    db.collection("posts").add(post)
                        .addOnSuccessListener {
                            busy = false
                            postText = ""
                            selectedMedia = null
                            mediaType = ""
                            success = true
                            message = "Your post was published!"
                        }
                        .addOnFailureListener { ex ->
                            busy = false
                            message = ex.localizedMessage ?: "Post failed. Check Firestore rules."
                        }
                }
                val uri = selectedMedia
                if (uri == null) {
                    savePost("")
                } else {
                    val extension = if (mediaType == "video") "mp4" else "jpg"
                    val folder = if (mediaType == "video") "reels" else "post_photos"
                    val mediaRef = FirebaseStorage.getInstance().reference.child("$folder/$uid/${System.currentTimeMillis()}.$extension")
                    val uploadUri = if (mediaType == "image" && (selectedPhotoFilter != "Original" || photoBrightness != 0f || photoContrast != 1f || photoSaturation != 1f)) {
                        createFilteredPhotoUri(context, uri, selectedPhotoFilter, photoBrightness, photoContrast, photoSaturation) ?: uri
                    } else uri
                    mediaRef.putFile(uploadUri)
                        .addOnSuccessListener {
                            mediaRef.downloadUrl.addOnSuccessListener { downloadUri -> savePost(downloadUri.toString()) }
                                .addOnFailureListener { ex -> busy = false; message = ex.localizedMessage ?: "Media URL could not be loaded." }
                        }
                        .addOnFailureListener { ex -> busy = false; message = ex.localizedMessage ?: "Media upload failed. Check Firebase Storage rules." }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
        ) { Text(if (busy) "Uploading..." else "Share") }
        if (postText.trim().length > 5000) Text("Post must be 5,000 characters or less.", color = MaterialTheme.colorScheme.error)
        if (message.isNotBlank()) Text(message, color = if (success) RoyalBlue else MaterialTheme.colorScheme.error)
    }
    if (showReelCreator) {
        CreateStoryDialog(uid, displayName, onDismiss = { showReelCreator = false }, publishAsReel = true)
    }
    if (showPostCropDialog && selectedMedia != null && mediaType == "image") {
        PhotoCropDialog(
            uri = selectedMedia!!,
            isCover = true,
            onCancel = { showPostCropDialog = false },
            onSave = { _, croppedBytes ->
                val croppedFile = java.io.File(context.cacheDir, "ppa_post_crop_" + System.currentTimeMillis() + ".jpg")
                croppedFile.writeBytes(croppedBytes)
                selectedMedia = Uri.fromFile(croppedFile)
                selectedPhotoFilter = "Original"
                photoBrightness = 0f
                photoContrast = 1f
                photoSaturation = 1f
                showPostCropDialog = false
                message = "Photo cropped. You can now share it."
            }
        )
    }
}

@Composable
private fun PhotoCropDialog(
    uri: Uri,
    isCover: Boolean,
    onCancel: () -> Unit,
    onSave: (ByteArray, ByteArray) -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(uri) { mutableFloatStateOf(0f) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var isCropping by remember(uri) { mutableStateOf(true) }
    var originalBytes by remember(uri) { mutableStateOf<ByteArray?>(null) }
    var croppedBytes by remember(uri) { mutableStateOf<ByteArray?>(null) }

    fun makeCrop(source: Bitmap): ByteArray {
        val outWidth = if (isCover) 1600 else 1080
        val outHeight = if (isCover) 900 else 1080
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)
        val fitScale = minOf(viewportWidth.toFloat() / source.width, viewportHeight.toFloat() / source.height)
        val imageScale = fitScale * zoom
        val frameWidth = if (isCover) minOf(viewportWidth * 0.9f, viewportHeight * 0.9f * (16f / 9f))
            else minOf(viewportWidth, viewportHeight) * 0.82f
        val frameHeight = if (isCover) frameWidth * 9f / 16f else frameWidth
        val frameLeft = (viewportWidth - frameWidth) / 2f
        val frameTop = (viewportHeight - frameHeight) / 2f
        val outputScale = outWidth.toFloat() / frameWidth
        val imageLeft = viewportWidth / 2f + offsetX - source.width * imageScale / 2f
        val imageTop = viewportHeight / 2f + offsetY - source.height * imageScale / 2f
        val matrix = Matrix().apply {
            setScale(imageScale * outputScale, imageScale * outputScale)
            postTranslate((imageLeft - frameLeft) * outputScale, (imageTop - frameTop) * outputScale)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val stream = java.io.ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        output.recycle()
        return stream.toByteArray()
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            if (bitmap == null) {
                Column(Modifier.padding(20.dp)) {
                    Text("Image open nahi ho paayi. Dusri photo select karein.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onCancel) { Text("Close") }
                }
            } else if (isCropping) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onCancel) { Text("‹ Back") }
                        Text("Crop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (original != null) {
                                originalBytes = original
                                croppedBytes = makeCrop(bitmap)
                                isCropping = false
                            }
                        }, enabled = viewportWidth > 0 && viewportHeight > 0) { Text("DONE") }
                    }
                    Text("Puri photo ko drag aur pinch karke frame mein set karein.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.fillMaxWidth().height(480.dp)
                            .background(Color(0xFF292929))
                            .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
                            .pointerInput(uri) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(0.25f, 6f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Full original photo for cropping",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX
                                translationY = offsetY
                            }
                        )
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            val frameWidth = if (isCover) minOf(size.width * 0.9f, size.height * 0.9f * (16f / 9f))
                                else minOf(size.width, size.height) * 0.82f
                            val frameHeight = if (isCover) frameWidth * 9f / 16f else frameWidth
                            val left = (size.width - frameWidth) / 2f
                            val top = (size.height - frameHeight) / 2f
                            val frameRect = androidx.compose.ui.geometry.Rect(left, top, left + frameWidth, top + frameHeight)
                            val mask = androidx.compose.ui.graphics.Path().apply {
                                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                                if (isCover) addRect(frameRect) else addOval(frameRect)
                            }
                            drawPath(mask, Color.Black.copy(alpha = 0.58f))
                            if (isCover) {
                                drawRect(Color(0xFF1877F2), topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(frameWidth, frameHeight),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                            } else {
                                drawCircle(Color(0xFF1877F2), radius = frameWidth / 2f, center = frameRect.center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                            }
                        }
                    }
                    Text("Zoom", style = MaterialTheme.typography.labelLarge)
                    Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 0.25f..6f)
                    OutlinedButton(onClick = { zoom = 1f; offsetX = 0f; offsetY = 0f }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset position")
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isCropping = true }) { Text("‹ Crop") }
                        Text("Preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Button(onClick = {
                            val original = originalBytes
                            val crop = croppedBytes
                            if (original != null && crop != null) onSave(original, crop)
                        }) { Text("SAVE") }
                    }
                    Text("Preview your ${if (isCover) "cover" else "profile"} photo",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    croppedBytes?.let { bytes ->
                        val preview = remember(bytes) {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                        if (preview != null) {
                            Image(bitmap = preview, contentDescription = "Final photo preview",
                                modifier = Modifier.fillMaxWidth()
                                    .aspectRatio(if (isCover) 16f / 9f else 1f)
                                    .clip(if (isCover) MaterialTheme.shapes.medium else CircleShape),
                                contentScale = ContentScale.Crop)
                        }
                    }
                    OutlinedButton(onClick = { isCropping = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Crop / Adjust again")
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(uid: String, email: String, initialName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf(initialName) }
    var username by remember { mutableStateOf("") }
    var profileVisibility by remember { mutableStateOf("public") }
    var usernameCheckMessage by remember { mutableStateOf("") }
    var usernameIsTaken by remember { mutableStateOf(false) }
    var bio by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var hobby by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    var favoritePlace by remember { mutableStateOf("") }
    var hometown by remember { mutableStateOf("") }
    var currentCity by remember { mutableStateOf("") }
    var work by remember { mutableStateOf("") }
    var education by remember { mutableStateOf("") }
    var relationshipStatus by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var coverPhotoUrl by remember { mutableStateOf("") }
    var photoOriginalUrl by remember { mutableStateOf("") }
    var coverPhotoOriginalUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var postCount by remember { mutableStateOf(0) }
    var friendCount by remember { mutableStateOf(0) }
    var profileTab by remember { mutableStateOf("All") }
    var galleryItems by remember { mutableStateOf(emptyList<ProfileGalleryItem>()) }
    var galleryFilter by remember { mutableStateOf("All photos") }
    var selectedGalleryItem by remember { mutableStateOf<ProfileGalleryItem?>(null) }
    var showStoryComposer by remember { mutableStateOf(false) }
    var showProfileSetup by remember { mutableStateOf(false) }
    var photoEditorUri by remember { mutableStateOf<Uri?>(null) }
    var photoEditorIsCover by remember { mutableStateOf(false) }
    var showProfilePhotoOptions by remember { mutableStateOf(false) }
    var showCoverPhotoOptions by remember { mutableStateOf(false) }
    var showPhotoLibraryPicker by remember { mutableStateOf(false) }
    var photoLibraryTarget by remember { mutableStateOf("cover") }
    var photoLibraryTab by remember { mutableStateOf("Photos of you") }
    var photoLibraryAlbum by remember { mutableStateOf<String?>(null) }

    fun openGalleryItem(item: ProfileGalleryItem) {
        val path = item.securePath ?: firebaseStorageObjectPath(item.url)
        val isProtectedProfileImage = path != null &&
            (path.startsWith("profile_photos/$uid/original_") || path.startsWith("cover_photos/$uid/original_"))
        if (!isProtectedProfileImage) {
            selectedGalleryItem = item
            return
        }
        FirebaseFunctions.getInstance("asia-south1")
            .getHttpsCallable("getSecureProfileMediaUrl")
            .call(mapOf("profileUid" to uid, "objectPath" to path))
            .addOnSuccessListener { result ->
                val secureUrl = (result.getData() as? Map<*, *>)?.get("url") as? String
                if (secureUrl.isNullOrBlank()) {
                    message = "Secure photo URL nahi mil paya."
                    isError = true
                } else {
                    selectedGalleryItem = item.copy(url = secureUrl, securePath = null)
                }
            }
            .addOnFailureListener { error ->
                message = error.localizedMessage ?: "Photo access verify nahi ho paya."
                isError = true
            }
    }

    LaunchedEffect(uid) {
        if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
            FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("revokeLegacyProfileMediaTokens")
                .call(mapOf("profileUid" to uid))
                .addOnFailureListener { error ->
                    android.util.Log.w("PpaProfileMedia", "Legacy profile media token migration did not finish", error)
                }
        }
    }


    LaunchedEffect(username, uid) {
        val cleanUsername = username.trim().lowercase()
        usernameCheckMessage = ""
        usernameIsTaken = false
        if (cleanUsername.isBlank() || !cleanUsername.matches(Regex("^[a-z0-9_.]{4,20}$"))) {
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(450)
        FirebaseFirestore.getInstance().collection("usernameClaims").document(cleanUsername).get()
            .addOnSuccessListener { claim ->
                if (username.trim().lowercase() != cleanUsername) return@addOnSuccessListener
                val ownerUid = claim.getString("uid")
                if (claim.exists() && ownerUid != uid) {
                    usernameIsTaken = true
                    usernameCheckMessage = "This username is already taken. Please choose another."
                } else {
                    usernameIsTaken = false
                    usernameCheckMessage = if (claim.exists()) "This is your current username." else "Username available."
                }
            }
            .addOnFailureListener {
                if (username.trim().lowercase() == cleanUsername) {
                    usernameCheckMessage = "Username availability check nahi ho paya. Internet check karein."
                }
            }
    }

    DisposableEffect(uid) {
        val db = FirebaseFirestore.getInstance()
        val postsRegistration = db.collection("posts").whereEqualTo("uid", uid).addSnapshotListener { snap, _ ->
            if (snap != null) postCount = snap.size()
        }
        val friendsRegistration = db.collection("friends").document(uid).collection("list").addSnapshotListener { snap, _ ->
            if (snap != null) friendCount = snap.size()
        }
        val galleryRegistration = db.collection("posts").whereEqualTo("uid", uid).addSnapshotListener { snap, _ ->
            if (snap != null) galleryItems = snap.documents.mapNotNull { doc ->
                val url = doc.getString("mediaUrl").orEmpty()
                if (url.isBlank()) null else {
                    val mediaType = doc.getString("mediaType").orEmpty().lowercase()
                    val postType = doc.getString("postType").orEmpty().lowercase()
                    val photoKind = doc.getString("photoKind").orEmpty().lowercase()
                    val isProfileOriginal = photoKind == "profile" || photoKind == "cover"
                    val originalPath = doc.getString("originalPath")
                        ?: if (isProfileOriginal) firebaseStorageObjectPath(url) else null
                    ProfileGalleryItem(doc.id, url, doc.getString("thumbnailUrl").orEmpty().ifBlank { url },
                        if (mediaType.contains("video") || postType == "reel" || postType == "reels") "video" else "image",
                        when (photoKind) { "profile" -> "Profile pictures"; "cover" -> "Cover photos"; else -> "Uploads" },
                        doc.getString("text").orEmpty(), originalPath)
                }
            }.sortedByDescending { it.id }
        }
        onDispose { postsRegistration.remove(); friendsRegistration.remove(); galleryRegistration.remove() }
    }

    DisposableEffect(uid) {
        val db = FirebaseFirestore.getInstance()
        val privateRegistration = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                message = error.localizedMessage ?: "Private profile load nahi ho paya."
                isError = true
                loading = false
            } else if (snapshot != null) {
                dateOfBirth = snapshot.getString("dateOfBirth") ?: ""
                gender = snapshot.getString("gender") ?: ""
                phoneNumber = snapshot.getString("phoneNumber") ?: ""
                hobby = snapshot.getString("hobby") ?: ""
                school = snapshot.getString("school") ?: ""
                favoritePlace = snapshot.getString("favoritePlace") ?: ""
                hometown = snapshot.getString("hometown") ?: ""
                currentCity = snapshot.getString("currentCity") ?: ""
                work = snapshot.getString("work") ?: ""
                education = snapshot.getString("education") ?: ""
                relationshipStatus = snapshot.getString("relationshipStatus") ?: ""
                interests = snapshot.getString("interests") ?: ""
                loading = false
            }
        }
        val publicRegistration = db.collection("publicProfiles").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                message = error.localizedMessage ?: "Public profile load nahi ho paya."
                isError = true
            } else if (snapshot != null) {
                displayName = snapshot.getString("displayName") ?: initialName
                username = snapshot.getString("username") ?: ""
                profileVisibility = snapshot.getString("profileVisibility") ?: "public"
                bio = snapshot.getString("bio") ?: ""
                photoUrl = snapshot.getString("photoUrl") ?: ""
                coverPhotoUrl = snapshot.getString("coverPhotoUrl") ?: ""
                currentCity = snapshot.getString("currentCity") ?: currentCity
                work = snapshot.getString("work") ?: work
                education = snapshot.getString("education") ?: education
                relationshipStatus = snapshot.getString("relationshipStatus") ?: relationshipStatus
                interests = snapshot.getString("interests") ?: interests
                photoOriginalUrl = snapshot.getString("photoOriginalPath")
                    ?: firebaseStorageObjectPath(snapshot.getString("photoOriginalUrl").orEmpty()).orEmpty()
                coverPhotoOriginalUrl = snapshot.getString("coverPhotoOriginalPath")
                    ?: firebaseStorageObjectPath(snapshot.getString("coverPhotoOriginalUrl").orEmpty()).orEmpty()
            }
        }
        onDispose { privateRegistration.remove(); publicRegistration.remove() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            photoEditorUri = uri
            photoEditorIsCover = false
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            photoEditorUri = uri
            photoEditorIsCover = true
        }
    }

    if (photoEditorUri != null) {
        PhotoCropDialog(
            uri = photoEditorUri!!,
            isCover = photoEditorIsCover,
            onCancel = { photoEditorUri = null },
            onSave = { originalBytes, croppedBytes ->
                val selectedUri = photoEditorUri
                if (selectedUri == null) {
                    saving = false
                    isError = true
                    message = "Selected photo dobara choose karein."
                } else {
                    saving = true
                    message = if (photoEditorIsCover) "Original cover photo upload ho rahi hai..." else "Original profile photo upload ho rahi hai..."
                    isError = false
                    // Close the editor after the user confirms the cropped photo.
                    photoEditorUri = null
                val isCover = photoEditorIsCover
                val originalPathField = if (isCover) "coverPhotoOriginalPath" else "photoOriginalPath"
                val legacyOriginalField = if (isCover) "coverPhotoOriginalUrl" else "photoOriginalUrl"
                val displayField = if (isCover) "coverPhotoUrl" else "photoUrl"
                val folder = if (isCover) "cover_photos/$uid" else "profile_photos/$uid"
                val storage = FirebaseStorage.getInstance().reference
                // Unique object names prevent stale cached download URLs after replacing a photo.
                val uploadVersion = System.currentTimeMillis().toString()
                val originalRef = storage.child("$folder/original_$uploadVersion.jpg")
                val displayFileName = if (isCover) "cover" else "profile"
                val displayRef = storage.child("$folder/${displayFileName}_$uploadVersion.jpg")
                val originalMimeType = context.contentResolver.getType(selectedUri) ?: "image/jpeg"
                val originalMetadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(originalMimeType).build()
                val croppedMetadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType("image/jpeg").build()
                originalRef.putBytes(originalBytes, originalMetadata)
                    .addOnSuccessListener {
                        message = "Original photo upload ho gayi. Display photo upload ho rahi hai..."
                        displayRef.putBytes(croppedBytes, croppedMetadata)
                            .addOnSuccessListener {
                                message = "Display photo upload ho gayi. Profile details save ho rahi hain..."
                                displayRef.downloadUrl.addOnSuccessListener { displayDownload ->
                                    message = "Photo URL mil gaya. Profile details save ho rahi hain..."
                                    val updates = mapOf(
                                        originalPathField to originalRef.path,
                                        legacyOriginalField to com.google.firebase.firestore.FieldValue.delete(),
                                        displayField to displayDownload.toString(),
                                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    )
                                        val firestore = FirebaseFirestore.getInstance()
                                        val profileRef = firestore.collection("publicProfiles").document(uid)
                                        val directoryRef = firestore.collection("profileDirectory").document(uid)
                                        val batch = firestore.batch()
                                        batch.set(profileRef, updates, com.google.firebase.firestore.SetOptions.merge())
                                        batch.set(directoryRef, mapOf(
                                            "uid" to uid,
                                            "displayName" to displayName.ifBlank { "PPA Talk user" },
                                            "username" to username,
                                            displayField to displayDownload.toString(),
                                            "profileVisibility" to profileVisibility,
                                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                        ), com.google.firebase.firestore.SetOptions.merge())
                                        batch.commit()
                                            .addOnSuccessListener {
                                                message = "Profile photo details save ho gayi. Finalizing..."
                                                if (isCover) {
                                                    coverPhotoUrl = displayDownload.toString()
                                                    coverPhotoOriginalUrl = originalRef.path
                                                } else {
                                                    photoUrl = displayDownload.toString()
                                                    photoOriginalUrl = originalRef.path
                                                }
                                                if (profileVisibility == "public") {
                                                    val photoPost = hashMapOf(
                                                        "uid" to uid,
                                                        "authorName" to displayName.ifBlank { "PPA Talk user" },
                                                        "text" to if (isCover) "Updated their cover photo." else "Updated their profile photo.",
                                                        "mediaUrl" to displayDownload.toString(),
                                                        "originalPath" to originalRef.path,
                                                        "thumbnailUrl" to displayDownload.toString(),
                                                        "mediaType" to "image",
                                                        "postType" to "post",
                                                        "photoKind" to if (isCover) "cover" else "profile",
                                                        "profileVisibility" to profileVisibility,
                                                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                                    )
                                                    FirebaseFirestore.getInstance().collection("posts").add(photoPost)
                                                        .addOnSuccessListener {
                                                            saving = false
                                                            photoEditorUri = null
                                                            message = if (isCover)
                                                                "Cover photo save ho gayi aur Feed + Photos mein share ho gayi."
                                                            else "Profile photo save ho gayi aur Feed + Photos mein share ho gayi."
                                                        }
                                                        .addOnFailureListener { exception ->
                                                            saving = false
                                                            isError = true
                                                            photoEditorUri = null
                                                            message = "FIRESTORE_FEED_POST_FAILED: ${exception.localizedMessage ?: "permission denied"}"
                                                        }
                                                } else {
                                                    saving = false
                                                    photoEditorUri = null
                                                    message = "Original photo save ho gayi. Private profile hone ke karan Feed par share nahi ki gayi."
                                                }
                                            }
                                            .addOnFailureListener { exception ->
                                                saving = false
                                                isError = true
                                                message = "FIRESTORE_PROFILE_SAVE_FAILED: ${exception.localizedMessage ?: "permission denied"}"
                                            }
                                    }.addOnFailureListener { exception ->
                                        saving = false
                                        isError = true
                                        message = "STORAGE_DOWNLOAD_URL_FAILED: ${exception.localizedMessage ?: "permission denied"}"
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    saving = false
                                    isError = true
                                    message = "STORAGE_DISPLAY_UPLOAD_FAILED: ${exception.localizedMessage ?: "permission denied"}"
                                }
                    }
                    .addOnFailureListener { exception ->
                        saving = false
                        isError = true
                        message = "STORAGE_ORIGINAL_UPLOAD_FAILED: ${exception.localizedMessage ?: "permission denied"}"
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("My Profile", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = RoyalBlue,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp))

        // Photo upload progress/status is intentionally not shown on the public profile screen.

        // Facebook-style cover with overlapping circular profile photo.
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(224.dp)) {
            Card(Modifier.fillMaxWidth().height(190.dp), shape = MaterialTheme.shapes.large) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (coverPhotoUrl.isNotBlank()) {
                        AsyncImage(model = coverPhotoUrl, contentDescription = "Profile cover photo",
                            modifier = Modifier.fillMaxSize().clickable { showCoverPhotoOptions = true }, contentScale = ContentScale.Crop)
                    } else {
                        Column(Modifier.fillMaxSize().clickable { showCoverPhotoOptions = true },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Photo, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(38.dp))
                            Text("Your cover photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            FilledIconButton(
                onClick = { coverPicker.launch("image/*") }, enabled = !saving,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = RoyalBlue, contentColor = Color.White)
            ) { Icon(Icons.Default.CameraAlt, contentDescription = "Change cover photo") }
            Box(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp).size(116.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().clickable { showProfilePhotoOptions = true },
                    shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface)
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(model = photoUrl, contentDescription = "Profile photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Profile photo",
                                modifier = Modifier.fillMaxSize(), tint = RoyalBlue)
                        }
                    }
                }
                FilledIconButton(
                    onClick = { imagePicker.launch("image/*") }, enabled = !saving,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = RoyalBlue, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Edit profile photo", modifier = Modifier.size(20.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(displayName.ifBlank { "PPA Talk user" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            if (username.isNotBlank()) Text("@$username", style = MaterialTheme.typography.bodyMedium, color = RoyalBlue)
            if (bio.isNotBlank()) Text(bio, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(postCount.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Posts", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(friendCount.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Friends", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("—", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Followers", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showProfileSetup = true; message = ""; isError = false }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Edit Profile")
                }
                OutlinedButton(onClick = { showStoryComposer = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddCircle, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Add to Story")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Reels", "Photos", "More").forEach { tab ->
                    FilterChip(selected = profileTab == tab, onClick = {
                    profileTab = tab
                    showProfileSetup = false
                    if (tab == "Photos") galleryFilter = "All photos"
                    if (tab == "Reels") galleryFilter = "All reels"
                }, label = { Text(tab) })
                }
            }
            if (profileTab == "Photos" || profileTab == "Reels") {
                val isReels = profileTab == "Reels"
                val currentProfilePhotos = listOfNotNull(
                    (photoOriginalUrl.ifBlank { photoUrl }).takeIf { it.isNotBlank() }?.let {
                        ProfileGalleryItem("current-profile", photoUrl, photoUrl, "image", "Profile pictures", "Profile photo", it)
                    },
                    (coverPhotoOriginalUrl.ifBlank { coverPhotoUrl }).takeIf { it.isNotBlank() }?.let {
                        ProfileGalleryItem("current-cover", coverPhotoUrl, coverPhotoUrl, "image", "Cover photos", "Cover photo", it)
                    }
                )
                val gallerySource = galleryItems + currentProfilePhotos.filter { current ->
                    galleryItems.none { it.url == current.url }
                }
                val filteredItems = gallerySource.filter { item ->
                    if (isReels) item.type == "video" else item.type == "image" && when (galleryFilter) {
                        "Cover photos" -> item.category == "Cover photos"
                        "Profile pictures" -> item.category == "Profile pictures"
                        "Tagged photos" -> false
                        else -> true
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (isReels) listOf("All reels") else listOf("Tagged photos", "Cover photos", "Profile pictures", "All photos")).forEach { filter ->
                        FilterChip(selected = galleryFilter == filter, onClick = { galleryFilter = filter }, label = { Text(filter) })
                    }
                }
                Text(if (isReels) "Your Reels" else "Your Photos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (filteredItems.isEmpty()) Text(if (isReels) "Abhi koi reel nahi hai." else "Abhi is category mein koi photo nahi hai.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                else filteredItems.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        rowItems.forEach { item ->
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.small).clickable { openGalleryItem(item) }) {
                                AsyncImage(model = item.thumbnailUrl, contentDescription = item.caption.ifBlank { "Open media" },
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (item.type == "video") Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play reel",
                                    tint = Color.White, modifier = Modifier.align(Alignment.Center).size(42.dp))
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }


        if (!showProfileSetup && (profileTab == "All" || profileTab == "More")) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (profileTab == "All") "About" else "More about you",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val profileDetails = listOf(
                    "Name" to displayName,
                    "Username" to username.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty(),
                    "Bio" to bio,
                    "Email" to email,
                    "Date of birth" to dateOfBirth,
                    "Gender" to gender,
                    "Phone" to phoneNumber,
                    "Hobby" to hobby,
                    "School / College" to school,
                    "Favorite place" to favoritePlace,
                    "Hometown" to hometown,
                    "Current city" to currentCity,
                    "Work" to work,
                    "Education" to education,
                    "Relationship status" to relationshipStatus,
                    "Interests" to interests
                ).filter { it.second.isNotBlank() }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (profileDetails.isEmpty()) {
                            Text("Abhi profile details add nahi ki gayi hain. Details add karne ke liye Edit Profile par click karein.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            profileDetails.forEach { (label, value) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showProfileSetup) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Profile Setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = RoyalBlue, modifier = Modifier.weight(1f))
                TextButton(onClick = { showProfileSetup = false }) { Text("Close") }
            }
            if (loading) CircularProgressIndicator()
            OutlinedTextField(value = displayName, onValueChange = { displayName = it },
                label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = username, onValueChange = { username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' } },
                label = { Text("Username") }, prefix = { Text("@") },
                supportingText = { Text("4–20 characters: a-z, 0-9, underscore or dot. Must be unique.") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (usernameCheckMessage.isNotBlank()) {
                Text(
                    usernameCheckMessage,
                    color = if (usernameIsTaken) MaterialTheme.colorScheme.error else RoyalBlue,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            OutlinedTextField(value = email, onValueChange = {}, readOnly = true,
                label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") },
                placeholder = { Text("Tell people about yourself") }, modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 4)
            Text("Profile privacy", style = MaterialTheme.typography.titleMedium,
                color = Orange, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (profileVisibility == "private") "Private profile" else "Public profile", fontWeight = FontWeight.SemiBold)
                    Text(if (profileVisibility == "private") "Only you can view your profile." else "Other signed-in PPA Talk users can view your profile.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = profileVisibility == "private", onCheckedChange = {
                    profileVisibility = if (it) "private" else "public"
                })
            }
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
            OutlinedTextField(value = currentCity, onValueChange = { currentCity = it },
                label = { Text("Current city") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = work, onValueChange = { work = it },
                label = { Text("Work / Company and role") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = education, onValueChange = { education = it },
                label = { Text("Education / College") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = relationshipStatus, onValueChange = { relationshipStatus = it },
                label = { Text("Relationship status") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = interests, onValueChange = { interests = it },
                label = { Text("Interests") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Email is linked to your login account. Avoid sharing private details publicly.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val cleanName = displayName.trim()
                    val cleanUsername = username.trim().lowercase()
                    if (cleanName.isBlank()) {
                        message = "Display name khali nahi ho sakta."
                        isError = true
                    } else if (!cleanUsername.matches(Regex("^[a-z0-9_.]{4,20}$"))) {
                        message = "Username 4–20 characters ka ho: a-z, 0-9, underscore ya dot."
                        isError = true
                    } else {
                        saving = true
                        message = ""
                        isError = false
                        val db = FirebaseFirestore.getInstance()
                        val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                        val privateUpdates = mapOf(
                            "uid" to uid, "email" to email,
                            "dateOfBirth" to dateOfBirth.trim(), "gender" to gender,
                            "phoneNumber" to phoneNumber.trim(), "hobby" to hobby.trim(),
                            "school" to school.trim(), "favoritePlace" to favoritePlace.trim(),
                            "hometown" to hometown.trim(), "currentCity" to currentCity.trim(), "work" to work.trim(), "education" to education.trim(), "relationshipStatus" to relationshipStatus.trim(), "interests" to interests.trim(), "updatedAt" to timestamp
                        )
                        val publicUpdates = mapOf(
                            "uid" to uid, "displayName" to cleanName, "username" to cleanUsername, "bio" to bio.trim(),
                            "photoUrl" to photoUrl, "coverPhotoUrl" to coverPhotoUrl,
                            "hobby" to hobby.trim(), "school" to school.trim(),
                            "favoritePlace" to favoritePlace.trim(), "hometown" to hometown.trim(),
                            "currentCity" to currentCity.trim(), "work" to work.trim(), "education" to education.trim(),
                            "relationshipStatus" to relationshipStatus.trim(), "interests" to interests.trim(),
                            "profileVisibility" to profileVisibility,
                            "updatedAt" to timestamp
                        )
                        val usernameRef = db.collection("usernameClaims").document(cleanUsername)
                        val profileRef = db.collection("publicProfiles").document(uid)
                        val directoryRef = db.collection("profileDirectory").document(uid)
                        val usersRef = db.collection("users").document(uid)
                        db.runTransaction { transaction ->
                            // Complete every transaction read before the first write.
                            val newClaim = transaction.get(usernameRef)
                            val currentProfile = transaction.get(profileRef)
                            val oldUsername = currentProfile.getString("username").orEmpty()
                            val oldClaimRef = if (oldUsername.isNotBlank() && oldUsername != cleanUsername) {
                                db.collection("usernameClaims").document(oldUsername)
                            } else null
                            val oldClaim = oldClaimRef?.let { transaction.get(it) }

                            if (newClaim.exists() && newClaim.getString("uid") != uid) {
                                throw com.google.firebase.firestore.FirebaseFirestoreException(
                                    "Username already taken. Please choose another.",
                                    com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                                )
                            }
                            if (oldClaim?.exists() == true && oldClaim.getString("uid") == uid) {
                                transaction.delete(oldClaimRef!!)
                            }
                            transaction.set(usernameRef, mapOf("uid" to uid, "username" to cleanUsername, "createdAt" to timestamp))
                            transaction.set(profileRef, publicUpdates, com.google.firebase.firestore.SetOptions.merge())
                            transaction.set(directoryRef, mapOf(
                                "uid" to uid, "displayName" to cleanName, "username" to cleanUsername,
                                "photoUrl" to photoUrl, "coverPhotoUrl" to coverPhotoUrl,
                                "profileVisibility" to profileVisibility, "updatedAt" to timestamp
                            ), com.google.firebase.firestore.SetOptions.merge())
                            null
                        }.addOnSuccessListener {
                            // Keep private fields in users/{uid}; never copy them to publicProfiles.
                            usersRef.set(privateUpdates, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener {
                                    FirebaseAuth.getInstance().currentUser
                                        ?.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                            .setDisplayName(cleanName).build())
                                    saving = false
                                    showProfileSetup = false
                                    message = "Profile save ho gayi."
                                }
                                .addOnFailureListener { exception ->
                                    saving = false
                                    isError = true
                                    message = "Private profile save failed: ${exception.localizedMessage ?: "permission denied"}"
                                }
                        }.addOnFailureListener { exception ->
                            saving = false
                            isError = true
                            message = "Username/public profile save failed: ${exception.localizedMessage ?: "permission denied"}"
                        }
                    }
                },
                enabled = !saving && !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saving) "Saving..." else "Save Profile") }
            if (message.isNotBlank()) {
                Text(message, color = if (isError) MaterialTheme.colorScheme.error else RoyalBlue)
            }
        }
        Spacer(Modifier.height(12.dp))
        }
    }



    if (selectedGalleryItem != null) {
        val item = selectedGalleryItem!!
        Dialog(onDismissRequest = { selectedGalleryItem = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize()) {
                    if (item.type == "video") {
                        AndroidView(factory = { viewContext ->
                            VideoView(viewContext).apply {
                                setVideoURI(Uri.parse(item.url))
                                setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                                setOnPreparedListener { player -> player.isLooping = true; start() }
                            }
                        }, modifier = Modifier.fillMaxSize())
                    } else AsyncImage(model = item.url, contentDescription = item.caption,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    IconButton(onClick = { selectedGalleryItem = null }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close media", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
    if (showProfilePhotoOptions || showCoverPhotoOptions) {
        val isCoverOptions = showCoverPhotoOptions
        val dismissPhotoOptions = {
            showProfilePhotoOptions = false
            showCoverPhotoOptions = false
        }
        Dialog(onDismissRequest = dismissPhotoOptions, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
                        Spacer(Modifier.height(18.dp))
                        val profilePhotoAction: (String) -> Unit = { action ->
                            dismissPhotoOptions()
                            when (action) {
                                "see-profile" -> if (photoUrl.isNotBlank()) openGalleryItem(ProfileGalleryItem("current-profile-preview", photoUrl, photoUrl, "image", "Profile pictures", "Profile photo", photoOriginalUrl.takeIf { it.isNotBlank() }))
                                "choose-profile" -> { photoLibraryTarget = "profile"; photoLibraryTab = "Photos of you"; photoLibraryAlbum = null; showPhotoLibraryPicker = true }
                                "see-cover" -> if (coverPhotoUrl.isNotBlank()) openGalleryItem(ProfileGalleryItem("current-cover-preview", coverPhotoUrl, coverPhotoUrl, "image", "Cover photos", "Cover photo", coverPhotoOriginalUrl.takeIf { it.isNotBlank() }))
                                "upload-cover" -> coverPicker.launch("image/*")
                                "choose-cover" -> { photoLibraryTarget = "cover"; photoLibraryTab = "Photos of you"; photoLibraryAlbum = null; showPhotoLibraryPicker = true }
                            }
                        }
                        @Composable
                        fun PhotoOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: String) {
                            Row(
                                Modifier.fillMaxWidth().clickable { profilePhotoAction(action) }.padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (isCoverOptions) {
                            PhotoOption(Icons.Default.Image, "See cover photo", "see-cover")
                            PhotoOption(Icons.Default.Upload, "Upload photo", "upload-cover")
                            PhotoOption(Icons.Default.FolderOpen, "Choose cover photo", "choose-cover")
                        } else {
                            PhotoOption(Icons.Default.AccountCircle, "See profile picture", "see-profile")
                            PhotoOption(Icons.Default.Image, "Choose profile picture", "choose-profile")
                        }
                    }
                }
            }
        }
    }
    if (showPhotoLibraryPicker) {
        val ownPhotoItems = (galleryItems + listOfNotNull(
            (photoOriginalUrl.ifBlank { photoUrl }).takeIf { it.isNotBlank() }?.let {
                ProfileGalleryItem("current-profile", it, photoUrl.ifBlank { it }, "image", "Profile pictures", "Profile photo")
            },
            (coverPhotoOriginalUrl.ifBlank { coverPhotoUrl }).takeIf { it.isNotBlank() }?.let {
                ProfileGalleryItem("current-cover", it, coverPhotoUrl.ifBlank { it }, "image", "Cover photos", "Cover photo")
            }
        )).filter { it.type == "image" }.distinctBy { it.url }
        val albumNames = listOf("Uploads", "Profile pictures", "Cover photos")
        val visiblePhotoItems = when (photoLibraryTab) {
            "Uploads" -> ownPhotoItems.filter { it.category == "Uploads" }
            "Albums" -> if (photoLibraryAlbum == null) emptyList() else ownPhotoItems.filter { it.category == photoLibraryAlbum }
            else -> ownPhotoItems
        }
        Dialog(onDismissRequest = { showPhotoLibraryPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (photoLibraryAlbum != null) photoLibraryAlbum = null else showPhotoLibraryPicker = false
                        }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                        Text(if (photoLibraryAlbum != null) photoLibraryAlbum!! else "Photos of you",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.width(48.dp))
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Photos of you", "Uploads", "Albums").forEach { tab ->
                            FilterChip(selected = photoLibraryTab == tab, onClick = {
                                photoLibraryTab = tab
                                photoLibraryAlbum = null
                            }, label = { Text(tab) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (photoLibraryTab == "Albums" && photoLibraryAlbum == null) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            albumNames.forEach { album ->
                                val albumItems = ownPhotoItems.filter { it.category == album }
                                if (albumItems.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth().clickable { photoLibraryAlbum = album }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(model = albumItems.first().thumbnailUrl, contentDescription = album,
                                            modifier = Modifier.size(76.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                            Text(album, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text("${albumItems.size} photos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Open album")
                                    }
                                }
                            }
                            if (albumNames.none { name -> ownPhotoItems.any { it.category == name } }) {
                                Text("Abhi koi album photo available nahi hai.", modifier = Modifier.padding(16.dp))
                            }
                        }
                    } else {
                        if (visiblePhotoItems.isEmpty()) {
                            // Keep the photo library empty without showing placeholder text.
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                visiblePhotoItems.chunked(3).forEach { photoRow ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        photoRow.forEach { item ->
                                            Box(Modifier.weight(1f).aspectRatio(1f).clickable {
                                                val selectedOriginalPath = item.securePath ?: firebaseStorageObjectPath(item.url).orEmpty()
                                                val selectedDisplayUrl = item.thumbnailUrl.ifBlank { item.url }
                                                val updates = if (photoLibraryTarget == "cover") mapOf(
                                                    "coverPhotoUrl" to selectedDisplayUrl,
                                                    "coverPhotoOriginalPath" to selectedOriginalPath,
                                                    "coverPhotoOriginalUrl" to com.google.firebase.firestore.FieldValue.delete(),
                                                    "updatedAt" to System.currentTimeMillis()
                                                ) else mapOf(
                                                    "photoUrl" to selectedDisplayUrl,
                                                    "photoOriginalPath" to selectedOriginalPath,
                                                    "photoOriginalUrl" to com.google.firebase.firestore.FieldValue.delete(),
                                                    "updatedAt" to System.currentTimeMillis()
                                                )
                                                FirebaseFirestore.getInstance().collection("users").document(uid)
                                                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                                                    .addOnSuccessListener {
                                                        FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
                                                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                                                        FirebaseFirestore.getInstance().collection("publicProfileDirectory").document(uid)
                                                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                                                        if (photoLibraryTarget == "cover") {
                                                            coverPhotoUrl = selectedDisplayUrl
                                                            coverPhotoOriginalUrl = selectedOriginalPath
                                                        } else {
                                                            photoUrl = selectedDisplayUrl
                                                            photoOriginalUrl = selectedOriginalPath
                                                        }
                                                        showPhotoLibraryPicker = false
                                                        message = if (photoLibraryTarget == "cover") "Cover photo update ho gayi." else "Profile photo update ho gayi."
                                                        isError = false
                                                    }
                                                    .addOnFailureListener { exception ->
                                                        message = exception.localizedMessage ?: "Photo update nahi ho payi."
                                                        isError = true
                                                    }
                                            }) {
                                                AsyncImage(model = item.thumbnailUrl, contentDescription = item.caption.ifBlank { "Choose photo" },
                                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            }
                                        }
                                        repeat(3 - photoRow.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showStoryComposer) CreateStoryDialog(uid, displayName, onDismiss = { showStoryComposer = false })
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    uid: String,
    currentUid: String,
    currentName: String,
    onOpenChat: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var displayName by remember(uid) { mutableStateOf("PPA Talk user") }
    var username by remember(uid) { mutableStateOf("") }
    var bio by remember(uid) { mutableStateOf("") }
    var photoUrl by remember(uid) { mutableStateOf("") }
    var coverPhotoUrl by remember(uid) { mutableStateOf("") }
    var hobby by remember(uid) { mutableStateOf("") }
    var school by remember(uid) { mutableStateOf("") }
    var favoritePlace by remember(uid) { mutableStateOf("") }
    var hometown by remember(uid) { mutableStateOf("") }
    var work by remember(uid) { mutableStateOf("") }
    var education by remember(uid) { mutableStateOf("") }
    var relationship by remember(uid) { mutableStateOf("") }
    var currentCity by remember(uid) { mutableStateOf("") }
    var interests by remember(uid) { mutableStateOf("") }
    var isPrivate by remember(uid) { mutableStateOf(false) }
    var loading by remember(uid) { mutableStateOf(true) }
    var error by remember(uid) { mutableStateOf("") }
    var friendStatus by remember(uid) { mutableStateOf("Add Friend") }
    var hasIncomingPendingRequest by remember(uid) { mutableStateOf(false) }
    var hasOutgoingPendingRequest by remember(uid) { mutableStateOf(false) }
    var hasAcceptedFriendRequest by remember(uid) { mutableStateOf(false) }
    var isFriendActionRunning by remember(uid) { mutableStateOf(false) }
    var actionMessage by remember(uid) { mutableStateOf("") }
    var selectedTab by remember(uid) { mutableStateOf("All") }
    var selectedProfileImage by remember(uid) { mutableStateOf<String?>(null) }
    var showFriendOptions by remember(uid) { mutableStateOf(false) }
    var friendOptionsPage by remember(uid) { mutableStateOf("menu") }
    var confirmUnfriend by remember(uid) { mutableStateOf(false) }
    var breakSeeLess by remember(uid) { mutableStateOf("See profile anywhere") }
    var breakLimit by remember(uid) { mutableStateOf("Keep current privacy settings") }
    var breakPastPosts by remember(uid) { mutableStateOf("Keep all posts as they are") }
    var friendLists by remember(uid) { mutableStateOf(setOf<String>()) }
    var isFavoriteFriend by remember(uid) { mutableStateOf(false) }

    DisposableEffect(uid, currentUid) {
        val directoryRegistration = db.collection("profileDirectory").document(uid)
            .addSnapshotListener { directory, directoryError ->
                if (directoryError == null && directory != null && directory.exists()) {
                    displayName = directory.getString("displayName")?.takeIf { it.isNotBlank() } ?: displayName
                    username = directory.getString("username").orEmpty()
                    photoUrl = directory.getString("photoUrl").orEmpty()
                    coverPhotoUrl = directory.getString("coverPhotoUrl").orEmpty()
                    isPrivate = directory.getString("profileVisibility") == "private"
                    loading = false
                }
            }
        val profileRegistration = db.collection("publicProfiles").document(uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    val denied = exception.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                    isPrivate = denied
                    error = if (denied) "" else (exception.localizedMessage ?: "Profile load nahi ho payi.")
                    loading = false
                } else if (snapshot != null && snapshot.exists()) {
                    displayName = snapshot.getString("displayName")?.takeIf { it.isNotBlank() } ?: "PPA Talk user"
                    username = snapshot.getString("username").orEmpty()
                    bio = snapshot.getString("bio").orEmpty()
                    photoUrl = snapshot.getString("photoUrl").orEmpty()
                    coverPhotoUrl = snapshot.getString("coverPhotoUrl").orEmpty()
                    hobby = snapshot.getString("hobby").orEmpty()
                    school = snapshot.getString("school").orEmpty()
                    favoritePlace = snapshot.getString("favoritePlace").orEmpty()
                    currentCity = snapshot.getString("currentCity").orEmpty()
                    hometown = snapshot.getString("hometown").orEmpty()
                    work = snapshot.getString("work").orEmpty()
                    education = snapshot.getString("education").orEmpty()
                    relationship = snapshot.getString("relationshipStatus").orEmpty()
                    interests = snapshot.getString("interests").orEmpty()
                    isPrivate = snapshot.getString("profileVisibility") == "private"
                    loading = false
                } else {
                    error = "Is user ki public profile abhi available nahi hai."
                    loading = false
                }
            }
        val outgoingRegistration = db.collection("friendRequests").document("${currentUid}_${uid}")
            .addSnapshotListener { request, _ ->
                hasOutgoingPendingRequest = request?.getString("status") == "pending"
                hasAcceptedFriendRequest = hasAcceptedFriendRequest || request?.getString("status") == "accepted"
                friendStatus = when {
                    hasAcceptedFriendRequest -> "Friends"
                    hasIncomingPendingRequest -> "Confirm Request"
                    hasOutgoingPendingRequest -> "Requested"
                    else -> "Add Friend"
                }
            }
        val incomingRegistration = db.collection("friendRequests").document("${uid}_${currentUid}")
            .addSnapshotListener { request, _ ->
                hasIncomingPendingRequest = request?.getString("status") == "pending"
                hasAcceptedFriendRequest = hasAcceptedFriendRequest || request?.getString("status") == "accepted"
                friendStatus = when {
                    hasAcceptedFriendRequest -> "Friends"
                    hasIncomingPendingRequest -> "Confirm Request"
                    hasOutgoingPendingRequest -> "Requested"
                    else -> "Add Friend"
                }
            }
        onDispose { directoryRegistration.remove(); profileRegistration.remove(); outgoingRegistration.remove(); incomingRegistration.remove() }
    }

    // A private profile becomes visible to an accepted friend; keep privacy for everyone else.
    if (isPrivate && friendStatus != "Friends") {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(205.dp)) {
                if (coverPhotoUrl.isNotBlank()) AsyncImage(model = coverPhotoUrl, contentDescription = "Cover photo",
                    modifier = Modifier.fillMaxSize().clickable { if (!isPrivate || friendStatus == "Friends") selectedProfileImage = coverPhotoUrl }, contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Photo, null, tint = RoyalBlue, modifier = Modifier.size(48.dp)) }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp).offset(y = 48.dp)) {
                    if (photoUrl.isNotBlank()) AsyncImage(model = photoUrl, contentDescription = "Profile photo",
                        modifier = Modifier.size(118.dp).padding(4.dp).clip(CircleShape).clickable { if (!isPrivate || friendStatus == "Friends") selectedProfileImage = photoUrl }, contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AccountCircle, null, tint = RoyalBlue, modifier = Modifier.size(118.dp).padding(4.dp))
                }
            }
            Spacer(Modifier.height(45.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                if (username.isNotBlank()) Text("@$username", color = RoyalBlue)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !isFriendActionRunning,
                        onClick = {
                            isFriendActionRunning = true
                            if (friendStatus == "Friends") {
                                isFriendActionRunning = false
                                showFriendOptions = true
                                friendOptionsPage = "menu"
                            } else if (friendStatus == "Requested") {
                                cancelFriendRequestSafely(db, currentUid, uid) { success, detail ->
                                    isFriendActionRunning = false
                                    actionMessage = detail
                                    if (success) friendStatus = "Add Friend"
                                }
                            } else if (friendStatus == "Confirm Request") {
                                acceptFriendRequestSafely(db, currentUid, uid, currentName, displayName) { success, detail ->
                                    isFriendActionRunning = false
                                    actionMessage = detail
                                    if (success) friendStatus = "Friends"
                                }
                            } else {
                                friendStatus = "Requested"
                                sendFriendRequestSafely(db, currentUid, uid, currentName, displayName) { success, detail ->
                                    isFriendActionRunning = false
                                    actionMessage = detail
                                    if (!success && !detail.contains("already sent", ignoreCase = true)) friendStatus = "Add Friend"
                                    else friendStatus = "Requested"
                                }
                            }
                        }, modifier = Modifier.weight(1f)
                    ) {
                        if (isFriendActionRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(if (friendStatus == "Friends") Icons.Default.PersonRemove else Icons.Default.PersonAdd, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isFriendActionRunning) { if (friendStatus == "Requested") "Cancelling..." else "Sending..." } else if (friendStatus == "Requested") "Cancel Request" else if (friendStatus == "Friends") "Friends" else friendStatus)
                    }
                    OutlinedButton(onClick = { if (!isPrivate || friendStatus == "Friends") onOpenChat(uid, displayName) else actionMessage = "Message karne ke liye pehle friend request accept honi chahiye." }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Chat, null); Spacer(Modifier.width(6.dp)); Text("Message")
                    }
                }
                if (actionMessage.isNotBlank()) Text(actionMessage, color = RoyalBlue, style = MaterialTheme.typography.bodySmall)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("This profile is private", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Add this person as a friend to see their posts, photos and personal details.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(205.dp)) {
                if (coverPhotoUrl.isNotBlank()) AsyncImage(model = coverPhotoUrl, contentDescription = "Cover photo",
                    modifier = Modifier.fillMaxSize().clickable { if (!isPrivate || friendStatus == "Friends") selectedProfileImage = coverPhotoUrl }, contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Photo, null, tint = RoyalBlue, modifier = Modifier.size(48.dp)) }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp).offset(y = 48.dp)) {
                    if (photoUrl.isNotBlank()) AsyncImage(model = photoUrl, contentDescription = "Profile photo",
                        modifier = Modifier.size(118.dp).padding(4.dp).clip(CircleShape).clickable { if (!isPrivate || friendStatus == "Friends") selectedProfileImage = photoUrl }, contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AccountCircle, null, tint = RoyalBlue, modifier = Modifier.size(118.dp).padding(4.dp))
                }
            }
            Spacer(Modifier.height(45.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                if (username.isNotBlank()) Text("@$username", color = RoyalBlue)
                if (bio.isNotBlank()) Text(bio, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Posts", fontWeight = FontWeight.SemiBold)
                    Text("Friends", fontWeight = FontWeight.SemiBold)
                    Text("Followers", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !isFriendActionRunning && friendStatus != "Requested",
                        onClick = {
                            isFriendActionRunning = true
                            actionMessage = if (friendStatus == "Confirm Request") "Accepting friend request..." else "Sending friend request..."
                            if (friendStatus == "Friends") {
                                isFriendActionRunning = false
                                showFriendOptions = true
                                friendOptionsPage = "menu"
                            } else if (friendStatus == "Requested") {
                                cancelFriendRequestSafely(db, currentUid, uid) { success, detail ->
                                    isFriendActionRunning = false
                                    actionMessage = detail
                                    if (success) friendStatus = "Add Friend"
                                }
                            } else if (friendStatus == "Confirm Request") {
                                acceptFriendRequestSafely(db, currentUid, uid, currentName, displayName) { success, detail ->
                                    isFriendActionRunning = false
                                    actionMessage = detail
                                    if (success) friendStatus = "Friends"
                                }
                            } else {
                                // Update the button immediately while Firestore confirms the request.
                                friendStatus = "Requested"
                                sendFriendRequestSafely(db, currentUid, uid, currentName, displayName) { success, detail ->
                                    isFriendActionRunning = false
                                    actionMessage = detail
                                    if (!success && !detail.contains("already sent", ignoreCase = true)) {
                                        friendStatus = "Add Friend"
                                    } else {
                                        friendStatus = "Requested"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isFriendActionRunning) CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary
                        ) else Icon(
                            if (friendStatus == "Friends") Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isFriendActionRunning) { if (friendStatus == "Friends") "Removing..." else if (friendStatus == "Requested") "Cancelling..." else "Please wait..." } else if (friendStatus == "Friends") "Friends" else friendStatus)
                    }
                    OutlinedButton(
                        onClick = { if (!isPrivate || friendStatus == "Friends") onOpenChat(uid, displayName) else actionMessage = "Message karne ke liye pehle friend request accept honi chahiye." },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Chat, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Message")
                    }
                }
                if (actionMessage.isNotBlank()) Text(actionMessage, color = RoyalBlue, style = MaterialTheme.typography.bodySmall)
            }
            if (showFriendOptions && friendStatus == "Friends" && friendOptionsPage == "menu") {
                ModalBottomSheet(
                    onDismissRequest = { showFriendOptions = false },
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                        listOf(
                            "Take a break" to Icons.Default.PersonOff,
                            "Edit friend list" to Icons.Default.Edit,
                            "Favorites" to Icons.Default.Star,
                            "Unfriend" to Icons.Default.PersonRemove
                        ).forEach { (option, optionIcon) ->
                            Row(
                                Modifier.fillMaxWidth().height(76.dp).clickable {
                                    friendOptionsPage = when(option) {
                                        "Take a break" -> "break"
                                        "Edit friend list" -> "lists"
                                        "Favorites" -> "favorites"
                                        else -> "unfriend"
                                    }
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(54.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(optionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Text(option, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            if (showFriendOptions && friendStatus == "Friends" && friendOptionsPage != "menu") {
                AlertDialog(onDismissRequest = { showFriendOptions = false },
                    title = { Text(when(friendOptionsPage) { "menu" -> displayName; "break" -> "Take a break"; "see" -> "See less of profile"; "limit" -> "Limit profile visibility"; "past" -> "Edit who can see past posts"; "lists" -> "Edit friend list"; "favorites" -> "Favorites"; else -> "Friend options" }) },
                    text = {
                        Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                            when(friendOptionsPage) {
                                "menu" -> listOf("Take a break", "Edit friend list", "Favorites", "Unfriend").forEach { option ->
                                    TextButton(onClick = { friendOptionsPage = when(option) { "Take a break" -> "break"; "Edit friend list" -> "lists"; "Favorites" -> "favorites"; else -> "unfriend" } }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(when(option) { "Take a break" -> Icons.Default.PersonOff; "Edit friend list" -> Icons.Default.Edit; "Favorites" -> Icons.Default.Star; else -> Icons.Default.PersonRemove }, null)
                                        Spacer(Modifier.width(12.dp)); Text(option)
                                    }
                                }
                                "break" -> {
                                    Text("Stay friends while controlling what you see and what they can see.")
                                    TextButton(onClick = { friendOptionsPage = "see" }) { Text("See less of " + displayName + "'s profile") }
                                    TextButton(onClick = { friendOptionsPage = "limit" }) { Text("Limit what " + displayName + "'s profile will see") }
                                    TextButton(onClick = { friendOptionsPage = "past" }) { Text("Edit who can see past posts") }
                                }
                                "see" -> listOf("See profile anywhere", "Limit where you see this profile").forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(breakSeeLess == v, onClick = { breakSeeLess = v }); Text(v) } }
                                "limit" -> listOf("Keep current privacy settings", "Hide your posts from this profile").forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(breakLimit == v, onClick = { breakLimit = v }); Text(v) } }
                                "past" -> listOf("Keep all posts as they are", "Remove yourself from all their posts").forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(breakPastPosts == v, onClick = { breakPastPosts = v }); Text(v) } }
                                "lists" -> listOf("Close Friends", "Acquaintances", "Unnamed List", "Restricted").forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(friendLists.contains(v), onCheckedChange = { checked -> friendLists = if (checked) friendLists + v else friendLists - v }); Text(v) } }
                                "favorites" -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isFavoriteFriend, onCheckedChange = { isFavoriteFriend = it }); Text("Add to Favorites") }
                                "unfriend" -> Text("Are you sure you want to remove " + displayName + " as your friend?")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (friendOptionsPage == "unfriend") {
                                showFriendOptions = false
                                isFriendActionRunning = true
                                unfriendSafely(db, currentUid, uid) { success, detail ->
                                    isFriendActionRunning = false
                                    actionMessage = detail
                                    if (success) friendStatus = "Add Friend"
                                }
                            } else if (friendOptionsPage == "menu") showFriendOptions = false
                            else if (friendOptionsPage in listOf("see","limit","past")) friendOptionsPage = "break"
                            else showFriendOptions = false
                        }) { Text(if (friendOptionsPage == "unfriend") "Yes" else if (friendOptionsPage in listOf("see","limit","past")) "Save" else "Done") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            if (friendOptionsPage == "unfriend") {
                                showFriendOptions = false
                                friendOptionsPage = "menu"
                            } else if (friendOptionsPage == "menu") showFriendOptions = false
                            else friendOptionsPage = if (friendOptionsPage in listOf("see","limit","past")) "break" else "menu"
                        }) { Text(if (friendOptionsPage == "unfriend") "No" else if (friendOptionsPage == "menu") "Cancel" else "Back") }
                    }
                )
            }
            if (confirmUnfriend) AlertDialog(onDismissRequest = { confirmUnfriend = false }, title = { Text("Confirm unfriend") }, text = { Text("Are you sure you want to remove " + displayName + " as your friend?") }, confirmButton = { TextButton(onClick = { confirmUnfriend = false; isFriendActionRunning = true; unfriendSafely(db, currentUid, uid) { success, detail -> isFriendActionRunning = false; actionMessage = detail; if (success) friendStatus = "Add Friend" } }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { confirmUnfriend = false }) { Text("Cancel") } })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Reels", "Photos").forEach { tab ->
                    FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            if (selectedTab == "All") {
                @Composable
                fun DetailSection(title: String, rows: List<Pair<String, String>>, icon: androidx.compose.ui.graphics.vector.ImageVector) {
                    val visibleRows = rows.filter { it.second.isNotBlank() }
                    if (visibleRows.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            visibleRows.forEach { (label, value) ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(34.dp))
                                    Spacer(Modifier.width(18.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (label.isNotBlank()) Text(label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(value, style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                DetailSection("Personal details", listOf(
                    "Current city" to currentCity.ifBlank { favoritePlace },
                    "Hometown" to hometown,
                    "Relationship" to relationship
                ), Icons.Default.Home)

                DetailSection("Work", listOf(
                    "" to work
                ), Icons.Default.Work)

                DetailSection("Education", listOf(
                    "" to school.ifBlank { education }
                ), Icons.Default.School)

                DetailSection("Hobbies", listOf(
                    "" to hobby
                ), Icons.Default.Favorite)

                DetailSection("Interests", listOf(
                    "" to interests
                ), Icons.Default.Info)

            } else {
                Text(if (selectedTab == "Reels") "Reels will appear here." else "Photos will appear here.",
                    modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    selectedProfileImage?.let { imageUrl ->
        Dialog(
            onDismissRequest = { selectedProfileImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Full-screen profile or cover photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { selectedProfileImage = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close photo",
                            tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(uid: String, modifier: Modifier = Modifier, onOpenFriends: () -> Unit = {}) {
    val db = remember { FirebaseFirestore.getInstance() }
    var notifications by remember(uid) { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var error by remember(uid) { mutableStateOf("") }

    DisposableEffect(uid) {
        var incoming = emptyList<Map<String, Any?>>()
        var outgoing = emptyList<Map<String, Any?>>()
        fun publish() {
            notifications = (incoming + outgoing).sortedByDescending {
                (it["createdAt"] as? Date)?.time ?: 0L
            }
            loading = false
        }
        val incomingRegistration = db.collection("friendRequests").whereEqualTo("toUid", uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage ?: "Notifications load nahi ho payi."
                    loading = false
                } else if (snapshot != null) {
                    incoming = snapshot.documents.filter {
                        it.getString("status") == "pending"
                    }.map { doc ->
                        mapOf(
                            "fromName" to doc.getString("fromName").orEmpty().ifBlank { "PPA Talk user" },
                            "toName" to doc.getString("toName").orEmpty(),
                            "status" to doc.getString("status").orEmpty(),
                            "direction" to "incoming",
                            "createdAt" to doc.getTimestamp("createdAt")?.toDate()
                        )
                    }
                    publish()
                }
            }
        val outgoingRegistration = db.collection("friendRequests").whereEqualTo("fromUid", uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage ?: "Sent notifications load nahi ho payi."
                    loading = false
                } else if (snapshot != null) {
                    outgoing = snapshot.documents.filter {
                        it.getString("status") in listOf("pending", "accepted", "rejected")
                    }.map { doc ->
                        mapOf(
                            "toName" to doc.getString("toName").orEmpty().ifBlank { "PPA Talk user" },
                            "status" to doc.getString("status").orEmpty(),
                            "direction" to "outgoing",
                            "createdAt" to doc.getTimestamp("createdAt")?.toDate()
                        )
                    }
                    publish()
                }
            }
        onDispose {
            incomingRegistration.remove()
            outgoingRegistration.remove()
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Notifications", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = RoyalBlue)
        Text("Friend requests and account activity", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loading) CircularProgressIndicator()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!loading && notifications.isEmpty() && error.isBlank()) {
            Text("Abhi koi notification nahi hai.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        notifications.forEach { item ->
            val incoming = item["direction"] == "incoming"
            val otherName = if (incoming) item["fromName"] as? String ?: "PPA Talk user"
                else item["toName"] as? String ?: "PPA Talk user"
            val status = item["status"] as? String ?: ""
            val label = when {
                incoming && status == "pending" -> "$otherName sent you a friend request"
                incoming && status == "accepted" -> "You accepted $otherName's friend request"
                incoming -> "You declined $otherName's friend request"
                status == "pending" -> "Friend request sent to $otherName"
                status == "accepted" -> "$otherName accepted your friend request"
                else -> "$otherName declined your friend request"
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = RoyalBlue,
                        modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        (item["createdAt"] as? Date)?.let {
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (incoming && status == "pending") {
                        TextButton(onClick = onOpenFriends) { Text("View") }
                    }
                }
            }
        }
    }
}

data class PPAUser(val uid: String, val name: String, val photoUrl: String = "")

@Composable
fun FriendsScreen(
    uid: String, currentName: String, onMessage: (String, String) -> Unit,
    modifier: Modifier = Modifier, onOpenProfile: (String) -> Unit = {}, onBack: () -> Unit = {},
    screenTitle: String = "Messenger"
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var search by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<PPAUser>>(emptyList()) }
    var requests by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var friends by remember { mutableStateOf<List<PPAUser>>(emptyList()) }
    var sentRequests by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onlineFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val usersListener = db.collection("profileDirectory").addSnapshotListener { snap, error ->
            if (error != null) status = error.localizedMessage ?: "People directory load nahi hui. Firestore rules check karein."
            if (snap != null) users = snap.documents.mapNotNull { doc ->
                if (doc.id == uid) null else PPAUser(doc.id,
                    doc.getString("displayName")?.takeIf { it.isNotBlank() } ?: "PPA Talk user",
                    doc.getString("photoUrl") ?: "")
            }
        }
        val requestsListener = db.collection("friendRequests").whereEqualTo("toUid", uid).addSnapshotListener { snap, _ ->
                if (snap != null) requests = snap.documents.filter { it.getString("status") == "pending" }.map { doc ->
                    mapOf("id" to doc.id, "fromUid" to (doc.getString("fromUid") ?: ""),
                        "fromName" to (doc.getString("fromName") ?: "PPA Talk user"))
                }
            }
        val sentListener = db.collection("friendRequests").whereEqualTo("fromUid", uid).addSnapshotListener { snap, _ ->
                if (snap != null) sentRequests = snap.documents.filter { it.getString("status") == "pending" }.mapNotNull { it.getString("toUid") }.toSet()
            }
        val friendsListener = db.collection("friends").document(uid).collection("list")
            .addSnapshotListener { snap, error ->
                if (error != null) status = error.localizedMessage ?: "Friends list load nahi hui."
                if (snap != null) {
                    friends = snap.documents.map { doc ->
                        PPAUser(doc.id, doc.getString("displayName") ?: "PPA Talk user",
                            doc.getString("photoUrl") ?: "")
                    }
                    // Older friend-list entries may not contain photoUrl. Hydrate
                    // them from the public profile so existing friends also see photos.
                    snap.documents.filter { it.getString("photoUrl").isNullOrBlank() }.forEach { doc ->
                        db.collection("publicProfiles").document(doc.id).get().addOnSuccessListener { profile ->
                            val photo = profile.getString("photoUrl").orEmpty()
                            val name = profile.getString("displayName").orEmpty()
                            if (photo.isNotBlank() || name.isNotBlank()) {
                                friends = friends.map { friend ->
                                    if (friend.uid == doc.id) friend.copy(
                                        name = name.ifBlank { friend.name },
                                        photoUrl = photo.ifBlank { friend.photoUrl }
                                    ) else friend
                                }
                            }
                        }
                    }
                }
            }
        onDispose { usersListener.remove(); requestsListener.remove(); sentListener.remove(); friendsListener.remove() }
    }
    DisposableEffect(uid, friends.map { it.uid }.sorted().joinToString("|")) {
        val registrations = friends.map { friend ->
            db.collection("profilePresence").document(friend.uid).addSnapshotListener { snap, _ ->
                onlineFriends = if (snap?.getBoolean("isOnline") == true) {
                    onlineFriends + friend.uid
                } else {
                    onlineFriends - friend.uid
                }
            }
        }
        onDispose {
            registrations.forEach { it.remove() }
            onlineFriends = emptySet()
        }
    }

    var selectedFriendsTab by remember(uid) { mutableStateOf("Requests") }
    var showSearch by remember(uid) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            Text("Friends", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showSearch = !showSearch; search = "" }) {
                Icon(Icons.Default.Search, contentDescription = "Search friends")
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true, shape = CircleShape, label = { Text("Search people or friends") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { IconButton(onClick = { search = ""; showSearch = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close search")
                } }
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Online" to "🟢 ${onlineFriends.intersect(friends.map { it.uid }.toSet()).size} online",
                "Suggestions" to "Suggestions",
                "Friends" to "Your friends"
            ).forEach { (tab, label) ->
                Surface(
                    modifier = Modifier.clip(CircleShape).clickable { selectedFriendsTab = tab },
                    shape = CircleShape,
                    color = Color(0xFFE4E7EB)
                ) {
                    Text(label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        color = Color(0xFF17191C), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        when (selectedFriendsTab) {
            "Requests" -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Friend requests", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text("${requests.size}", color = Color(0xFFD33B4A), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    TextButton(onClick = { selectedFriendsTab = "Suggestions" }) { Text("See all") }
                }
                if (requests.isEmpty()) Text("No new friend requests.", modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(requests, key = { it["id"].orEmpty() }) { request ->
                        val fromUid = request["fromUid"].orEmpty()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val requestPerson = users.firstOrNull { it.uid == fromUid }
                            Surface(modifier = Modifier.size(164.dp, 164.dp).clip(CircleShape)
                                .clickable { onOpenProfile(fromUid) },
                                shape = CircleShape, color = Color(0xFFE4E6EB)) {
                                if (requestPerson?.photoUrl?.isNotBlank() == true) {
                                    AsyncImage(model = requestPerson.photoUrl, contentDescription = "Friend profile",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.AccountCircle, contentDescription = "Friend profile",
                                        tint = Color(0xFF7B8087), modifier = Modifier.padding(8.dp))
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(request["fromName"] ?: "PPA Talk user", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenProfile(fromUid) })
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        acceptFriendRequestSafely(db, uid, fromUid, currentName,
                                            request["fromName"].orEmpty()) { success, detail -> status = detail }
                                    }, modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0866FF))) {
                                        Text("Confirm", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(onClick = {
                                        db.collection("friendRequests").document(request["id"].orEmpty())
                                            .update("status", "rejected")
                                            .addOnSuccessListener { status = "Request deleted." }
                                            .addOnFailureListener { status = it.localizedMessage ?: "Could not delete request." }
                                    }, modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE4E6EB))) {
                                        Text("Delete", color = Color(0xFF17191C), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Friends", "Online" -> {
                val visibleFriends = friends.filter {
                    (selectedFriendsTab != "Online" || it.uid in onlineFriends) &&
                        (search.isBlank() || it.name.contains(search, true))
                }
                Text(if (selectedFriendsTab == "Online") "Friends online" else "Your friends (${visibleFriends.size})",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                if (visibleFriends.isEmpty()) Text("No friends to show here yet.",
                    modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visibleFriends, key = { it.uid }) { friend ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenProfile(friend.uid) }
                            .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(66.dp)) {
                                Surface(Modifier.size(62.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    if (friend.photoUrl.isNotBlank()) AsyncImage(model = friend.photoUrl,
                                        contentDescription = friend.name, modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop)
                                    else Icon(Icons.Default.AccountCircle, null, tint = RoyalBlue, modifier = Modifier.padding(4.dp))
                                }
                                if (friend.uid in onlineFriends) Surface(Modifier.size(15.dp).align(Alignment.BottomEnd),
                                    shape = CircleShape, color = Color(0xFF31C46C),
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {}
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(friend.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(if (friend.uid in onlineFriends) "Active now" else "Your friend",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { onMessage(friend.uid, friend.name) }) { Text("Message") }
                        }
                    }
                }
            }
            "Suggestions" -> {
                val matches = users.filter { person ->
                    person.name.contains(search, true) &&
                        friends.none { it.uid == person.uid } &&
                        requests.none { it["fromUid"] == person.uid }
                }.take(50)
                Text("People you may know", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                if (matches.isEmpty()) Text("No suggestions found.", modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(matches, key = { it.uid }) { person ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(66.dp).clickable { onOpenProfile(person.uid) },
                                shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                if (person.photoUrl.isNotBlank()) AsyncImage(model = person.photoUrl,
                                    contentDescription = person.name, modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.AccountCircle, null, tint = RoyalBlue, modifier = Modifier.padding(4.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(person.name, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenProfile(person.uid) })
                                Text("Suggested for you", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sentRequests.contains(person.uid)) Text("Requested", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Button(onClick = {
                                sendFriendRequestSafely(db, uid, person.uid, currentName, person.name) { _, detail -> status = detail }
                            }) { Text("Add Friend") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessengerScreen(uid: String, onOpenChat: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val db = remember { FirebaseFirestore.getInstance() }
    var search by remember { mutableStateOf("") }
    var chats by remember(uid) { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("Inbox") }
    var loading by remember(uid) { mutableStateOf(true) }
    DisposableEffect(uid) {
        val registration = db.collection("chats").whereArrayContains("participantIds", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    chats = snapshot.documents.mapNotNull { doc ->
                        val participants = doc.get("participantIds") as? List<*> ?: return@mapNotNull null
                        val otherUid = participants.filterIsInstance<String>().firstOrNull { it != uid } ?: return@mapNotNull null
                        val lastMessage = doc.getString("lastMessage")?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val lastMessageAt = doc.getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L
                        mapOf(
                            "uid" to otherUid,
                            "lastMessage" to lastMessage,
                            "lastMessageAt" to lastMessageAt.toString(),
                            "lastReadAt" to (doc.getTimestamp("lastReadAt_$uid")?.toDate()?.time ?: 0L).toString(),
                            "lastSenderUid" to (doc.getString("lastSenderUid") ?: ""),
                            "unreadCount" to (doc.getLong("unreadCount_$uid") ?: 0L).toString()
                        )
                    }.sortedByDescending { it["lastMessageAt"]?.toLongOrNull() ?: 0L }
                    loading = false
                }
            }
        onDispose { registration.remove() }
    }
    var people by remember(uid) { mutableStateOf<List<PPAUser>>(emptyList()) }
    var onlinePeople by remember(uid) { mutableStateOf<Set<String>>(emptySet()) }
    DisposableEffect(uid, people.map { it.uid }.sorted().joinToString("|")) {
        val presenceRegistrations = people.map { person ->
            db.collection("profilePresence").document(person.uid).addSnapshotListener { snapshot, _ ->
                onlinePeople = if (snapshot?.getBoolean("isOnline") == true) {
                    onlinePeople + person.uid
                } else {
                    onlinePeople - person.uid
                }
            }
        }
        onDispose {
            presenceRegistrations.forEach { it.remove() }
            onlinePeople = emptySet()
        }
    }
    var friendsLoadError by remember(uid) { mutableStateOf("") }
    DisposableEffect(uid) {
        val registration = db.collection("friends").document(uid).collection("list")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    friendsLoadError = error.localizedMessage ?: "Friend list sync failed. Firestore rules check karein."
                } else if (snapshot != null) {
                    friendsLoadError = ""
                    val base = snapshot.documents.map { doc ->
                        PPAUser(doc.id, doc.getString("displayName") ?: "PPA Talk user", doc.getString("photoUrl") ?: "")
                    }
                    people = base
                    base.filter { it.photoUrl.isBlank() || it.name == "PPA Talk user" }.forEach { friend ->
                        db.collection("publicProfiles").document(friend.uid).get().addOnSuccessListener { profile ->
                            people = people.map { current ->
                                if (current.uid == friend.uid) current.copy(
                                    name = profile.getString("displayName")?.takeIf { it.isNotBlank() } ?: current.name,
                                    photoUrl = profile.getString("photoUrl")?.takeIf { it.isNotBlank() } ?: current.photoUrl
                                ) else current
                            }
                        }
                    }
                }
            }
        onDispose { registration.remove() }
    }
    // Messenger shows only people with at least one sent/received message.
    // Opening an empty chat does not add a friend to the inbox.
    val visible = chats.mapNotNull { chat ->
        people.firstOrNull { it.uid == chat["uid"] }?.let { person -> person.copy(name = person.name) }
    }.distinctBy { it.uid }.filter { it.name.contains(search, true) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { }) { Icon(Icons.Default.ArrowBack, contentDescription = "Chats", tint = MaterialTheme.colorScheme.onSurface) }
            Text("Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { }) { Icon(Icons.Default.Settings, contentDescription = "Chat settings") }
            IconButton(onClick = { }) { Icon(Icons.Default.Edit, contentDescription = "New message") }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true, shape = CircleShape, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        if (friendsLoadError.isNotBlank()) Text(friendsLoadError, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (people.isEmpty() && !loading && friendsLoadError.isBlank()) Text("Abhi koi accepted friend sync nahi hua. Friends tab mein friend list check karein.",
            modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Inbox", "Marketplace", "Communities").forEach { tab ->
                FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
            }
        }
        if (selectedTab == "Inbox") {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (visible.isEmpty() && !loading) item { Text("Your conversations will appear here. Add friends to start chatting.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(visible, key = { it.uid }) { person ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenChat(person.uid, person.name) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(66.dp)) {
                            Surface(Modifier.size(62.dp).align(Alignment.TopStart), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                if (person.photoUrl.isNotBlank()) AsyncImage(model = person.photoUrl, contentDescription = person.name, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RoyalBlue, modifier = Modifier.padding(5.dp))
                            }
                            if (person.uid in onlinePeople) Surface(Modifier.size(14.dp).align(Alignment.BottomEnd),
                                shape = CircleShape, color = Color(0xFF31C46C), border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {}
                        }
                        val chat = chats.firstOrNull { it["uid"] == person.uid }
                        val unreadCount = chat?.get("unreadCount")?.toLongOrNull() ?: 0L
                        val unread = chat != null && (
                            unreadCount > 0L ||
                            (chat["lastSenderUid"] == person.uid &&
                                (chat["lastMessageAt"]?.toLongOrNull() ?: 0L) >
                                (chat["lastReadAt"]?.toLongOrNull() ?: 0L))
                        )
                        val preview = if (unread) "${maxOf(1L, unreadCount)} new messages"
                            else chat?.get("lastMessage") ?: "Tap to message"
                        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold)
                            Text(preview,
                                color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1)
                        }
                        if (unread) {
                            Icon(Icons.Default.Circle, contentDescription = "Unread message",
                                tint = RoyalBlue, modifier = Modifier.size(8.dp))
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$selectedTab coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun ChatScreen(currentUid: String, friendUid: String, friendName: String, modifier: Modifier = Modifier, onBack: () -> Unit, onOpenProfile: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current
    val activity = context as? Activity
    val chatId = remember(currentUid, friendUid) { listOf(currentUid, friendUid).sorted().joinToString("_") }
    var messages by remember(chatId) { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var draft by remember(chatId) { mutableStateOf("") }
    var sending by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf("") }
    var callController by remember { mutableStateOf<PpaCallController?>(null) }
    var callStatus by remember { mutableStateOf("") }
    var callError by remember { mutableStateOf("") }
    var incomingCallId by remember { mutableStateOf<String?>(null) }
    var incomingCallType by remember { mutableStateOf("audio") }
    var friendPhotoUrl by remember(friendUid) { mutableStateOf("") }
    var autoDeleteSeconds by remember(chatId) { mutableStateOf(0L) }
    var showChatSettings by remember { mutableStateOf(false) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var chatSettingsSaving by remember { mutableStateOf(false) }

    DisposableEffect(friendUid) {
        val profileListener = db.collection("publicProfiles").document(friendUid)
            .addSnapshotListener { snapshot, _ ->
                friendPhotoUrl = snapshot?.getString("photoUrl").orEmpty()
            }
        onDispose { profileListener.remove() }
    }
    var pendingCallId by remember { mutableStateOf<String?>(null) }
    var requestedCallType by remember { mutableStateOf("audio") }

    // Keep the display awake on both caller and receiver while a call controller is active.
    // Restore the previous window setting as soon as the call ends or this screen is disposed.
    DisposableEffect(callController) {
        val window = activity?.window
        val keepScreenOnFlag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasAlreadyEnabled = (window?.attributes?.flags ?: 0) and keepScreenOnFlag != 0
        if (callController != null) window?.addFlags(keepScreenOnFlag)
        onDispose {
            if (!wasAlreadyEnabled) window?.clearFlags(keepScreenOnFlag)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { !it }.keys
        val incomingId = pendingCallId
        pendingCallId = null
        if (denied.isNotEmpty()) {
            val deniedNames = denied.mapNotNull { permission ->
                when (permission) {
                    android.Manifest.permission.RECORD_AUDIO -> "Microphone"
                    android.Manifest.permission.CAMERA -> "Camera"
                    else -> null
                }
            }.joinToString(" and ")
            callError = "$deniedNames permission denied. Allow it in Android Settings > Apps > PPA Talk > Permissions."
            if (incomingId != null) {
                db.collection("callSessions").document(incomingId).update(mapOf(
                    "status" to "rejected",
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            }
        } else {
            val controller = PpaCallController(
                context, currentUid, friendUid,
                onStatus = { callStatus = it },
                onError = { callError = it; callStatus = it },
                onRemoteCallEnded = {
                    callStatus = "Call ended"
                    callController?.let { active ->
                        active.release()
                        callController = null
                    }
                }
            )
            callController = controller
            if (incomingId != null) controller.acceptIncoming(incomingId)
            else controller.startOutgoing(requestedCallType)
        }
    }

    fun requestCall(type: String, incomingId: String? = null) {
        requestedCallType = type
        pendingCallId = incomingId
        callError = ""
        if (incomingId != null) {
            incomingCallId = null
            (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(incomingId.hashCode())
        }
        val permissions = if (type == "video") {
            arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA)
        } else arrayOf(android.Manifest.permission.RECORD_AUDIO)
        permissionLauncher.launch(permissions)
    }

    DisposableEffect(currentUid, friendUid) {
        val incomingRegistration = db.collection("callSessions")
            .whereEqualTo("calleeUid", currentUid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    callError = exception.localizedMessage ?: "Incoming call check failed."
                } else {
                    val incoming = snapshot?.documents?.firstOrNull {
                        it.getString("callerUid") == friendUid
                    }
                    incomingCallId = incoming?.id
                    incomingCallType = incoming?.getString("callType") ?: "audio"
                }
            }
        onDispose { incomingRegistration.remove() }
    }

    // Close the local call UI when Firestore reports that the other participant
    // ended or rejected the call.
    LaunchedEffect(callStatus) {
        if (callStatus == "Call ended" || callStatus == "Call declined") {
            callController?.let { controller ->
                controller.release()
                callController = null
            }
        }
    }

    // Ensure the parent chat exists before subscribing to messages. Firestore
    // rules authorize message reads only for participants in this parent document.
    DisposableEffect(chatId, currentUid, friendUid) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        val chatRef = db.collection("chats").document(chatId)
        val settingsRegistration = chatRef.addSnapshotListener { chatSnapshot, _ ->
            autoDeleteSeconds = chatSnapshot?.getLong("autoDeleteSeconds") ?: 0L
        }
        chatRef.set(mapOf(
            "participantIds" to listOf(currentUid, friendUid).sorted(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "lastReadAt_$currentUid" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "unreadCount_$currentUid" to 0L
        ), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                registration = chatRef.collection("messages")
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .addSnapshotListener { snap, exception ->
                        if (exception != null) error = exception.localizedMessage ?: "Messages could not load. Check Firestore chat permissions."
                        else if (snap != null) {
                            messages = snap.documents.map { doc ->
                                mapOf("id" to doc.id, "uid" to (doc.getString("uid") ?: ""),
                                    "text" to (doc.getString("text") ?: ""),
                                    "expireAt" to doc.getTimestamp("expireAt"))
                            }
                            // Keep the conversation marked read while it is open,
                            // including messages received after the initial load.
                            chatRef.set(
                                mapOf(
                                    "lastReadAt_$currentUid" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                    "unreadCount_$currentUid" to 0L
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        }
                    }
            }
            .addOnFailureListener { exception ->
                error = exception.localizedMessage ?: "Chat permission denied. Please check Firestore rules."
            }
        onDispose { registration?.remove(); settingsRegistration.remove() }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Box(
                Modifier.size(42.dp).clip(CircleShape).clickable { onOpenProfile() },
                contentAlignment = Alignment.Center
            ) {
                if (friendPhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = friendPhotoUrl,
                        contentDescription = "$friendName profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Friend profile",
                        modifier = Modifier.fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                friendName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).clickable { onOpenProfile() }
            )
            Box {
                IconButton(onClick = { showChatSettings = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Chat settings")
                }
                DropdownMenu(expanded = showChatSettings, onDismissRequest = { showChatSettings = false }) {
                    DropdownMenuItem(text = { Text("Auto-delete messages") }, onClick = { showChatSettings = false; selectedMessageId = "__timer__" })
                }
            }
            IconButton(enabled = callController == null, onClick = { requestCall("audio") }) {
                Icon(Icons.Default.Call, contentDescription = "Voice call", tint = RoyalBlue)
            }
            IconButton(enabled = callController == null, onClick = { requestCall("video") }) {
                Icon(Icons.Default.VideoCall, contentDescription = "Video call", tint = Orange)
            }
        }
        HorizontalDivider()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        if (callError.isNotBlank()) Text(callError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item { Text("Say hello to ${friendName} 👋", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(messages, key = { it["id"] as String }) { msg ->
                val mine = msg["uid"] == currentUid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Box {
                        Surface(
                            color = if (mine) RoyalBlue else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.clickable { if (mine) selectedMessageId = msg["id"] as String }
                        ) {
                            Text(msg["text"] as? String ?: "", modifier = Modifier.widthIn(max = 280.dp).padding(12.dp),
                                color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = selectedMessageId == msg["id"], onDismissRequest = { selectedMessageId = null }) {
                            DropdownMenuItem(text = { Text("Delete message") }, onClick = {
                                val messageId = msg["id"] as String
                                selectedMessageId = null
                                db.collection("chats").document(chatId).collection("messages").document(messageId)
                                    .delete().addOnFailureListener { error = it.localizedMessage ?: "Message delete failed." }
                            })
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") }, maxLines = 4)
            Spacer(Modifier.width(8.dp))
            IconButton(enabled = draft.isNotBlank() && !sending, onClick = {
                val messageText = draft.trim()
                if (messageText.isNotBlank()) {
                    sending = true; error = ""
                    val chatRef = db.collection("chats").document(chatId)
                    chatRef.set(mapOf("participantIds" to listOf(currentUid, friendUid).sorted(),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
                        com.google.firebase.firestore.SetOptions.merge()).onSuccessTask {
                        val messageData = hashMapOf<String, Any>(
                            "uid" to currentUid, "text" to messageText,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        if (autoDeleteSeconds > 0L) {
                            messageData["expireAt"] = com.google.firebase.Timestamp(
                                java.util.Date(System.currentTimeMillis() + autoDeleteSeconds * 1000L)
                            )
                        }
                        chatRef.collection("messages").add(messageData).addOnSuccessListener {
                            chatRef.set(mapOf("lastMessage" to messageText, "lastMessageAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "lastSenderUid" to currentUid), com.google.firebase.firestore.SetOptions.merge())
                        }
                    }.addOnSuccessListener { draft = ""; sending = false }
                        .addOnFailureListener { sending = false; error = it.localizedMessage ?: "Message send failed. Check Firestore rules." }
                }
            }) { Icon(Icons.Default.Send, contentDescription = "Send message", tint = RoyalBlue) }
        }
    }

    if (selectedMessageId == "__timer__") {
        AlertDialog(
            onDismissRequest = { selectedMessageId = null },
            title = { Text("Auto-delete messages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose when new messages in this chat are automatically deleted.")
                    listOf(
                        0L to "Never",
                        86400L to "24 hours",
                        604800L to "7 days",
                        1296000L to "15 days",
                        2592000L to "30 days"
                    ).forEach { (seconds, label) ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !chatSettingsSaving) {
                            chatSettingsSaving = true
                            db.collection("chats").document(chatId).update(
                                mapOf("autoDeleteSeconds" to seconds,
                                    "autoDeleteLabel" to label,
                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                            ).addOnSuccessListener {
                                autoDeleteSeconds = seconds
                                chatSettingsSaving = false
                                selectedMessageId = null
                            }.addOnFailureListener {
                                chatSettingsSaving = false
                                error = it.localizedMessage ?: "Could not update auto-delete setting."
                                selectedMessageId = null
                            }
                        }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = autoDeleteSeconds == seconds, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMessageId = null }) { Text("Close") } }
        )
    }

    if (incomingCallId != null && callController == null) {
        IncomingCallScreen(
            callerName = friendName,
            callerPhotoUrl = friendPhotoUrl,
            callType = incomingCallType,
            onAccept = { requestCall(incomingCallType, incomingCallId) },
            onReject = {
                val id = incomingCallId
                incomingCallId = null
                if (id != null) {
                    (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(id.hashCode())
                    db.collection("callSessions").document(id).update(
                        mapOf("status" to "rejected", "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                    )
                }
            }
        )
    }

    callController?.let { controller ->
        PpaCallDialog(
            controller = controller,
            callerName = friendName,
            callType = requestedCallType,
            status = callStatus.ifBlank { "Connecting…" },
            onEnd = {
                controller.endCall()
                callController = null
                callStatus = "Call ended"
            }
        )
    }
}


@Composable
fun IncomingCallScreen(
    callerName: String,
    callerPhotoUrl: String,
    callType: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101827)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 42.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "PPA TALK",
                        color = Color(0xFF79A6FF),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (callType == "video") "Incoming video call" else "Incoming voice call",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        modifier = Modifier.size(150.dp),
                        shape = CircleShape,
                        color = Color(0xFF26364F)
                    ) {
                        if (callerPhotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = callerPhotoUrl,
                                contentDescription = "$callerName profile photo",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Caller profile",
                                    tint = Color(0xFF9FB7E8),
                                    modifier = Modifier.size(112.dp)
                                )
                            }
                        }
                    }
                    Text(
                        callerName.ifBlank { "PPA Talk user" },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "is calling you…",
                        color = Color(0xFFB8C4D8),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = onReject,
                            modifier = Modifier.size(76.dp),
                            shape = CircleShape,
                            color = Color(0xFFE53945),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Reject call", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text("Reject", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            onClick = onAccept,
                            modifier = Modifier.size(76.dp),
                            shape = CircleShape,
                            color = Color(0xFF19A65A),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Accept call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text("Accept", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(isDarkMode: Boolean, onThemeModeChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val auth = remember { FirebaseAuth.getInstance() }
    val account = auth.currentUser
    var emailVerified by remember { mutableStateOf(account?.isEmailVerified == true) }
    var securityMessage by remember { mutableStateOf("") }
    var securityBusy by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Account security", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Account", fontWeight = FontWeight.SemiBold)
                Text(account?.email ?: account?.phoneNumber ?: "Signed-in PPA Talk account",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (account?.email != null) {
                    Text(if (emailVerified) "Email status: Verified" else "Email status: Not verified",
                        color = if (emailVerified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        enabled = !securityBusy,
                        onClick = {
                            securityBusy = true
                            securityMessage = ""
                            account.reload().addOnCompleteListener { reloadTask ->
                                if (reloadTask.isSuccessful) {
                                    emailVerified = auth.currentUser?.isEmailVerified == true
                                    securityMessage = if (emailVerified) "Your email is verified."
                                        else "Email is not verified yet. Check your inbox."
                                } else {
                                    securityMessage = reloadTask.exception?.localizedMessage ?: "Could not refresh email status."
                                }
                                securityBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Refresh verification status") }
                    if (!emailVerified) {
                        Button(
                            enabled = !securityBusy,
                            onClick = {
                                securityBusy = true
                                securityMessage = ""
                                account.sendEmailVerification().addOnCompleteListener { task ->
                                    securityMessage = if (task.isSuccessful) "Verification email sent. Check your inbox and spam folder."
                                        else task.exception?.localizedMessage ?: "Could not send verification email."
                                    securityBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Resend verification email") }
                    }
                    OutlinedButton(
                        enabled = !securityBusy,
                        onClick = {
                            currentPassword = ""
                            newPassword = ""
                            confirmNewPassword = ""
                            securityMessage = ""
                            showChangePassword = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Change password") }
                } else {
                    Text("This account uses mobile-number authentication.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (securityMessage.isNotBlank()) {
                    Text(securityMessage, style = MaterialTheme.typography.bodySmall,
                        color = if (securityMessage.contains("sent", ignoreCase = true) || securityMessage.contains("verified", ignoreCase = true))
                            Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dark mode", fontWeight = FontWeight.SemiBold)
                    Text(if (isDarkMode) "Dark appearance is enabled" else "Royal White appearance is enabled",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isDarkMode, onCheckedChange = onThemeModeChange)
            }
        }
        Text("Your theme choice is saved on this device. Log out from the account menu.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showChangePassword && account?.email != null) {
        AlertDialog(
            onDismissRequest = { if (!securityBusy) showChangePassword = false },
            title = { Text("Change password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPassword, onValueChange = { currentPassword = it },
                        label = { Text("Current password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("New password (at least 8 characters)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmNewPassword, onValueChange = { confirmNewPassword = it },
                        label = { Text("Confirm new password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !securityBusy,
                    onClick = {
                        when {
                            currentPassword.isBlank() -> securityMessage = "Enter your current password."
                            newPassword.length < 8 -> securityMessage = "New password must be at least 8 characters."
                            newPassword != confirmNewPassword -> securityMessage = "New passwords do not match."
                            currentPassword == newPassword -> securityMessage = "Choose a password different from your current password."
                            else -> {
                                securityBusy = true
                                securityMessage = ""
                                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(
                                    account.email!!, currentPassword
                                )
                                account.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                                    if (!reauthTask.isSuccessful) {
                                        securityMessage = reauthTask.exception?.localizedMessage
                                            ?: "Current password is incorrect. Please try again."
                                        securityBusy = false
                                    } else {
                                        account.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                                            securityMessage = if (updateTask.isSuccessful) {
                                                showChangePassword = false
                                                currentPassword = ""
                                                newPassword = ""
                                                confirmNewPassword = ""
                                                "Password changed successfully."
                                            } else {
                                                updateTask.exception?.localizedMessage ?: "Could not change password."
                                            }
                                            securityBusy = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { Text(if (securityBusy) "Please wait…" else "Update password") }
            },
            dismissButton = {
                TextButton(enabled = !securityBusy, onClick = { showChangePassword = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SimplePage(title: String, detail: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = RoyalBlue)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
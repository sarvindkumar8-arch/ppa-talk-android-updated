package com.ppatalk.app

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.horizontalScroll
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
                                val db = FirebaseFirestore.getInstance()
                                val name = firebaseUser.displayName ?: account.displayName ?: "PPA Talk user"
                                val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                                val batch = db.batch()
                                batch.set(db.collection("users").document(firebaseUser.uid), mapOf(
                                    "uid" to firebaseUser.uid,
                                    "email" to (firebaseUser.email ?: account.email.orEmpty()),
                                    "updatedAt" to timestamp
                                ), com.google.firebase.firestore.SetOptions.merge())
                                batch.set(db.collection("publicProfiles").document(firebaseUser.uid), mapOf(
                                    "uid" to firebaseUser.uid, "displayName" to name,
                                    "photoUrl" to (firebaseUser.photoUrl?.toString() ?: ""),
                                    "updatedAt" to timestamp
                                ), com.google.firebase.firestore.SetOptions.merge())
                                batch.commit()
                                    .addOnSuccessListener { busy = false; onAuthenticated() }
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
                                    val db = FirebaseFirestore.getInstance()
                                    val profileName = signedInUser.displayName?.takeIf { it.isNotBlank() }
                                        ?: signedInUser.email?.substringBefore("@") ?: "PPA Talk user"
                                    val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    val batch = db.batch()
                                    batch.set(db.collection("users").document(signedInUser.uid), mapOf(
                                        "uid" to signedInUser.uid, "email" to (signedInUser.email ?: ""),
                                        "updatedAt" to timestamp
                                    ), com.google.firebase.firestore.SetOptions.merge())
                                    batch.set(db.collection("publicProfiles").document(signedInUser.uid), mapOf(
                                        "uid" to signedInUser.uid, "displayName" to profileName,
                                        "updatedAt" to timestamp
                                    ), com.google.firebase.firestore.SetOptions.merge())
                                    batch.commit().addOnCompleteListener { task ->
                                        busy = false
                                        if (task.isSuccessful) onAuthenticated()
                                        else message = task.exception?.localizedMessage ?: "Profile setup failed."
                                    }
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
        fun setPresence(online: Boolean) {
            db.collection("publicProfiles").document(uid).set(
                mapOf("isOnline" to online, "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge()
            )
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
        if (activeChatUid != null) {
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
        },
    ) { padding ->
        if (activeChatUid != null) {
            ChatScreen(uid, activeChatUid!!, activeChatName, Modifier.padding(padding)) { activeChatUid = null }
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
                PublicProfileScreen(viewedProfileUid, Modifier.padding(padding))
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

private fun acceptFriendRequestSafely(
    db: FirebaseFirestore, currentUid: String, friendUid: String, currentName: String, friendName: String,
    onResult: (Boolean, String) -> Unit
) {
    db.collection("friendRequests").document(friendUid + "_" + currentUid).update("status", "accepted")
        .addOnSuccessListener {
            db.collection("publicProfiles").document(currentUid).get().addOnSuccessListener { mine ->
                db.collection("publicProfiles").document(friendUid).get().addOnSuccessListener { theirs ->
                    val batch = db.batch()
                    batch.set(db.collection("friends").document(currentUid).collection("list").document(friendUid),
                        mapOf("uid" to friendUid, "displayName" to (theirs.getString("displayName") ?: friendName),
                            "photoUrl" to (theirs.getString("photoUrl") ?: "")))
                    batch.set(db.collection("friends").document(friendUid).collection("list").document(currentUid),
                        mapOf("uid" to currentUid, "displayName" to (mine.getString("displayName") ?: currentName),
                            "photoUrl" to (mine.getString("photoUrl") ?: "")))
                    batch.commit().addOnSuccessListener { onResult(true, "Friend request accepted.") }
                        .addOnFailureListener { onResult(false, it.localizedMessage ?: "Friend list update failed.") }
                }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not load friend's profile.") }
            }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not load your profile.") }
        }.addOnFailureListener { onResult(false, it.localizedMessage ?: "Could not accept friend request.") }
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
    var selectedReel by remember { mutableStateOf<FeedPost?>(null) }

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
                        FeedPost(
                            doc.id, doc.getString("uid").orEmpty(),
                            doc.getString("authorName") ?: "PPA Talk user",
                            doc.getString("text").orEmpty(),
                            doc.getTimestamp("createdAt")?.toDate(),
                            doc.getString("mediaUrl").orEmpty(), mediaType, postType
                        )
                    }
                    loading = false
                    error = ""
                }
            }
        onDispose { registration.remove() }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Reels", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = RoyalBlue)
        }
        if (loading) item { CircularProgressIndicator() }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (!loading && reels.isEmpty() && error.isBlank()) {
            item { Text("Abhi koi Reel nahi hai. Create Post mein Video / Reel select karke share karein.") }
        }
        items(reels, key = { it.id }) { reel ->
            FeedPostCard(reel, currentUid,
                onOpenAuthorProfile = { onOpenUserProfile(reel.uid) }) {
                selectedReel = reel
            }
        }
    }
    selectedReel?.let { reel ->
        PostCommentsDialog(reel, currentUid) { selectedReel = null }
    }
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
        items(posts, key = { it.id }) { post ->
            FeedPostCard(post, currentUid, onOpenAuthorProfile = { onOpenUserProfile(post.uid) }) { selectedPost = post }
        }
    }
    selectedPost?.let { PostCommentsDialog(it, currentUid) { selectedPost = null } }
}


@Composable
fun HomeComposerCard(uid: String, onCreatePost: () -> Unit, onOpenProfile: () -> Unit) {
    var profilePhotoUrl by remember(uid) { mutableStateOf("") }

    DisposableEffect(uid) {
        val registration = FirebaseFirestore.getInstance()
            .collection("publicProfiles").document(uid)
            .addSnapshotListener { snapshot, _ ->
                profilePhotoUrl = snapshot?.getString("photoUrl").orEmpty()
            }
        onDispose { registration.remove() }
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
    val db = remember { FirebaseFirestore.getInstance() }
    var stories by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var viewingStory by remember { mutableStateOf<Map<String, Any>?>(null) }

    DisposableEffect(uid) {
        val listener = db.collection("stories").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val now = System.currentTimeMillis()
                stories = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val expires = doc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                    if (expires > now) doc.data?.plus("id" to doc.id) else null
                }
            }
        onDispose { listener.remove() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().height(118.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                onClick = { showCreate = true },
                modifier = Modifier.width(92.dp).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Create story", tint = RoyalBlue, modifier = Modifier.size(34.dp))
                    Text("Create story", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            stories.forEach { story ->
                StoryPreviewCard(story = story, onClick = { viewingStory = story })
            }
        }
    }

    if (showCreate) CreateStoryDialog(uid, displayName, onDismiss = { showCreate = false })
    viewingStory?.let { story ->
        AlertDialog(
            onDismissRequest = { viewingStory = null },
            title = { Text(story["authorName"] as? String ?: "Story") },
            text = {
                if (story["mediaType"] == "video") {
                    AndroidView(factory = { viewContext ->
                        VideoView(viewContext).apply {
                            setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                            setVideoURI(Uri.parse(story["mediaUrl"] as? String ?: ""))
                            setOnPreparedListener { player -> player.isLooping = true; start() }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 480.dp))
                } else {
                    AsyncImage(model = story["mediaUrl"] as? String, contentDescription = "Story media",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp), contentScale = ContentScale.Fit)
                }
            },
            confirmButton = { TextButton(onClick = { viewingStory = null }) { Text("Close") } }
        )
    }
}

@Composable
fun StoryPreviewCard(story: Map<String, Any>, onClick: () -> Unit) {
    var profilePhotoUrl by remember(story["uid"]) { mutableStateOf("") }
    val storyUid = story["uid"] as? String ?: ""
    DisposableEffect(storyUid) {
        if (storyUid.isBlank()) return@DisposableEffect onDispose { }
        val registration = FirebaseFirestore.getInstance().collection("publicProfiles")
            .document(storyUid).addSnapshotListener { profile, _ ->
                profilePhotoUrl = profile?.getString("photoUrl").orEmpty()
            }
        onDispose { registration.remove() }
    }
    Card(onClick = onClick, modifier = Modifier.width(92.dp).fillMaxHeight()) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = story["mediaUrl"] as? String, contentDescription = "Story",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(34.dp),
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
fun CreateStoryDialog(uid: String, displayName: String, onDismiss: () -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        mediaType = if (uri == null) "" else "image"
        message = ""
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        mediaType = if (uri == null) "" else "video"
        message = ""
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Create story") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose a photo or video. Your story will be visible for 24 hours.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy) { Icon(Icons.Default.Photo, null); Text("Photo") }
                    OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !busy) { Icon(Icons.Default.VideoLibrary, null); Text("Video") }
                }
                selectedUri?.let { uri ->
                    if (mediaType == "image") AsyncImage(model = uri, contentDescription = "Selected story",
                        modifier = Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop)
                    else Text("Video selected")
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(enabled = selectedUri != null && !busy, onClick = {
                val uri = selectedUri ?: return@Button
                busy = true
                message = ""
                val ext = if (mediaType == "video") "mp4" else "jpg"
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("stories/$uid/${System.currentTimeMillis()}.$ext")
                storageRef.putFile(uri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { download ->
                            val now = System.currentTimeMillis()
                            val story = hashMapOf(
                                "uid" to uid,
                                "authorName" to displayName,
                                "mediaUrl" to download.toString(),
                                "mediaType" to mediaType,
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "expiresAt" to com.google.firebase.Timestamp(Date(now + 24L * 60L * 60L * 1000L))
                            )
                            FirebaseFirestore.getInstance().collection("stories").add(story)
                                .addOnSuccessListener { busy = false; onDismiss() }
                                .addOnFailureListener { e -> busy = false; message = e.localizedMessage ?: "Story save failed. Check Firestore rules." }
                        }.addOnFailureListener { e -> busy = false; message = e.localizedMessage ?: "Could not get media URL." }
                    }
                    .addOnFailureListener { e -> busy = false; message = e.localizedMessage ?: "Upload failed. Check Storage rules." }
            }) { Text(if (busy) "Uploading..." else "Share story") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

@Composable
fun FeedPostCard(post: FeedPost, currentUid: String, onOpenAuthorProfile: () -> Unit = {}, onCommentsClick: () -> Unit) {
    var liked by remember(post.id, currentUid) { mutableStateOf(false) }
    var selectedReaction by remember(post.id, currentUid) { mutableStateOf("") }
    var showReactionPicker by remember(post.id, currentUid) { mutableStateOf(false) }
    var likeCount by remember(post.id) { mutableIntStateOf(0) }
    var commentCount by remember(post.id) { mutableIntStateOf(0) }
    var actionBusy by remember(post.id) { mutableStateOf(false) }
    var friendState by remember(post.uid, currentUid) { mutableStateOf("Add Friend") }
    var friendBusy by remember(post.uid, currentUid) { mutableStateOf(false) }
    var friendMessage by remember(post.uid, currentUid) { mutableStateOf("") }
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
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant, onClick = onOpenAuthorProfile
                ) {
                    var authorPhotoUrl by remember(post.uid) { mutableStateOf("") }
                    DisposableEffect(post.uid) {
                        val registration = db.collection("publicProfiles").document(post.uid)
                            .addSnapshotListener { profile, _ -> authorPhotoUrl = profile?.getString("photoUrl").orEmpty() }
                        onDispose { registration.remove() }
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
                if (post.uid != currentUid) {
                    OutlinedButton(
                        enabled = !friendBusy && friendState in listOf("Add Friend", "Respond"),
                        onClick = {
                            friendBusy = true
                            if (friendState == "Respond") {
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
                            "Requested" -> "Requested"
                            "Friends" -> "Friends"
                            "Respond" -> "Accept"
                            else -> "Add Friend"
                        })
                    }
                }
            }
            if (friendMessage.isNotBlank()) Text(friendMessage, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            if (post.text.isNotBlank()) Text(post.text)
            if (post.mediaUrl.isNotBlank() && post.mediaType == "image") {
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = post.mediaUrl,
                    contentDescription = "Post photo",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentScale = ContentScale.Crop
                )
            } else if (post.mediaUrl.isNotBlank() && post.mediaType == "video") {
                Spacer(Modifier.height(10.dp))
                AndroidView(
                    factory = { context -> VideoView(context).apply { setMediaController(MediaController(context).also { it.setAnchorView(this) }); setVideoURI(Uri.parse(post.mediaUrl)) } },
                    update = { },
                    modifier = Modifier.fillMaxWidth().height(230.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (showReactionPicker) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !actionBusy, onClick = { showReactionPicker = !showReactionPicker }) {
                        if (liked) {
                            Text(selectedReaction.ifBlank { "👍" }, style = MaterialTheme.typography.titleLarge)
                        } else {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = RoyalBlue)
                        }
                    }
                    Text(likeCount.toString(), style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCommentsClick) {
                        Icon(Icons.Default.ModeComment, contentDescription = "Comments", tint = RoyalBlue)
                    }
                    Text(commentCount.toString(), style = MaterialTheme.typography.labelMedium)
                }
                val context = LocalContext.current
                IconButton(onClick = {
                    val shareText = if (post.mediaUrl.isBlank()) post.text else post.text + "\\n" + post.mediaUrl
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share PPA Talk post"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Orange)
                }
            }
            if (post.uid == currentUid) {
                var menuExpanded by remember(post.id) { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Post options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete post") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                db.collection("posts").document(post.id).delete()
                            }
                        )
                    }
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
        db.collection("publicProfiles").document(currentUid).get()
            .addOnSuccessListener { currentName = it.getString("displayName") ?: currentName }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                Text(post.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val topLevel = comments.filter { it.parentId.isBlank() }
                    if (topLevel.isEmpty()) item { Text("No comments yet. Start the conversation!") }
                    items(topLevel, key = { it.id }) { comment ->
                        Column(Modifier.fillMaxWidth()) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(comment.authorName, fontWeight = FontWeight.Bold, color = RoyalBlue)
                                    Text(comment.text)
                                    TextButton(onClick = { replyTo = comment }) { Text("Reply") }
                                }
                            }
                            comments.filter { it.parentId == comment.id }.forEach { reply ->
                                Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 5.dp)) {
                                    Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = Orange,
                                        modifier = Modifier.padding(end = 6.dp))
                                    Column {
                                        Text(reply.authorName, fontWeight = FontWeight.SemiBold, color = RoyalBlue)
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
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMedia = uri
        mediaType = if (uri == null) "" else "image"
        message = ""
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMedia = uri
        mediaType = if (uri == null) "" else "video"
        message = ""
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { postText = ""; selectedMedia = null; mediaType = ""; message = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            Text("New post", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(horizontal = 18.dp),
            placeholder = { Text("What's on your mind?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            maxLines = 12, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent)
        )
        Spacer(Modifier.weight(1f, fill = false))
        selectedMedia?.let { uri ->
            Text(if (mediaType == "video") "Video selected" else "Photo selected", color = RoyalBlue)
            if (mediaType == "image") {
                AsyncImage(model = uri, contentDescription = "Selected photo", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Crop)
            } else {
                Text("Video ready to upload. You can publish it as a Reel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { selectedMedia = null; mediaType = "" }) { Text("Remove attachment") }
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
                    mediaRef.putFile(uri)
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
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offsetX by remember(uri) { mutableFloatStateOf(0f) }
    var offsetY by remember(uri) { mutableFloatStateOf(0f) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (isCover) "Adjust cover photo" else "Adjust profile photo",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Photo ko drag karke position set karein aur slider se size chhota-bada karein.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (bitmap != null) {
                    Box(
                        Modifier.fillMaxWidth()
                            .aspectRatio(if (isCover) 16f / 9f else 1f)
                            .clip(if (isCover) MaterialTheme.shapes.medium else CircleShape)
                            .background(Color.Black)
                            .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
                            .pointerInput(uri) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Photo crop preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX
                                translationY = offsetY
                            }
                        )
                    }
                    Text("Zoom", style = MaterialTheme.typography.labelLarge)
                    Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..5f)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { zoom = 1f; offsetX = 0f; offsetY = 0f }, modifier = Modifier.weight(1f)) {
                            Text("Reset")
                        }
                        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            enabled = viewportWidth > 0 && viewportHeight > 0,
                            onClick = {
                                val outWidth = if (isCover) 1600 else 1080
                                val outHeight = if (isCover) 900 else 1080
                                val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(output)
                                val baseScale = maxOf(outWidth.toFloat() / bitmap.width, outHeight.toFloat() / bitmap.height)
                                val finalScale = baseScale * zoom
                                val matrix = Matrix().apply {
                                    setScale(finalScale, finalScale)
                                    postTranslate(
                                        (outWidth - bitmap.width * finalScale) / 2f + offsetX * outWidth / viewportWidth,
                                        (outHeight - bitmap.height * finalScale) / 2f + offsetY * outHeight / viewportHeight
                                    )
                                }
                                canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                                val stream = java.io.ByteArrayOutputStream()
                                output.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                output.recycle()
                                val originalBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                if (originalBytes == null) {
                                    output.recycle()
                                    return@Button
                                }
                                onSave(originalBytes, stream.toByteArray())
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save & Upload") }
                    }
                } else {
                    Text("Image open nahi ho paayi. Dusri photo select karein.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onCancel) { Text("Close") }
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
    var showStoryComposer by remember { mutableStateOf(false) }
    var photoEditorUri by remember { mutableStateOf<Uri?>(null) }
    var photoEditorIsCover by remember { mutableStateOf(false) }

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
        onDispose { postsRegistration.remove(); friendsRegistration.remove() }
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
                photoOriginalUrl = snapshot.getString("photoOriginalUrl") ?: photoUrl
                coverPhotoOriginalUrl = snapshot.getString("coverPhotoOriginalUrl") ?: coverPhotoUrl
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
                saving = true
                message = if (photoEditorIsCover) "Original cover photo upload ho rahi hai..." else "Original profile photo upload ho rahi hai..."
                isError = false
                val isCover = photoEditorIsCover
                val originalField = if (isCover) "coverPhotoOriginalUrl" else "photoOriginalUrl"
                val displayField = if (isCover) "coverPhotoUrl" else "photoUrl"
                val folder = if (isCover) "cover_photos/$uid" else "profile_photos/$uid"
                val storage = FirebaseStorage.getInstance().reference
                val originalRef = storage.child("$folder/original.jpg")
                val displayRef = storage.child(if (isCover) "$folder/cover.jpg" else "$folder/profile.jpg")
                val originalMimeType = context.contentResolver.getType(photoEditorUri!!) ?: "image/jpeg"
                val originalMetadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(originalMimeType).build()
                val croppedMetadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType("image/jpeg").build()
                originalRef.putBytes(originalBytes, originalMetadata)
                    .addOnSuccessListener {
                        originalRef.downloadUrl.addOnSuccessListener { originalDownload ->
                            displayRef.putBytes(croppedBytes, croppedMetadata)
                                .addOnSuccessListener {
                                    displayRef.downloadUrl.addOnSuccessListener { displayDownload ->
                                        val updates = mapOf(
                                            originalField to originalDownload.toString(),
                                            displayField to displayDownload.toString(),
                                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                        )
                                        FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
                                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                                            .addOnSuccessListener {
                                                if (isCover) {
                                                    coverPhotoUrl = displayDownload.toString()
                                                    coverPhotoOriginalUrl = originalDownload.toString()
                                                } else {
                                                    photoUrl = displayDownload.toString()
                                                    photoOriginalUrl = originalDownload.toString()
                                                }
                                                if (profileVisibility == "public") {
                                                    val photoPost = hashMapOf(
                                                        "uid" to uid,
                                                        "authorName" to displayName.ifBlank { "PPA Talk user" },
                                                        "text" to if (isCover) "Updated their cover photo." else "Updated their profile photo.",
                                                        "mediaUrl" to originalDownload.toString(),
                                                        "mediaType" to "image",
                                                        "postType" to "post",
                                                        "photoKind" to if (isCover) "cover" else "profile",
                                                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                                    )
                                                    FirebaseFirestore.getInstance().collection("posts").add(photoPost)
                                                        .addOnSuccessListener {
                                                            saving = false
                                                            photoEditorUri = null
                                                            message = if (isCover)
                                                                "Original cover photo save ho gayi aur Feed + Photos mein share ho gayi."
                                                            else "Original profile photo save ho gayi aur Feed + Photos mein share ho gayi."
                                                        }
                                                        .addOnFailureListener { exception ->
                                                            saving = false
                                                            isError = true
                                                            photoEditorUri = null
                                                            message = "Photo profile par save ho gayi, lekin Feed post nahi ban paayi: ${exception.localizedMessage ?: "permission denied"}"
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
                                                message = exception.localizedMessage ?: "Photo details save nahi hui."
                                            }
                                    }.addOnFailureListener { exception ->
                                        saving = false
                                        isError = true
                                        message = exception.localizedMessage ?: "Display photo URL nahi mil paya."
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    saving = false
                                    isError = true
                                    message = exception.localizedMessage ?: "Display photo upload failed."
                                }
                        }.addOnFailureListener { exception ->
                            saving = false
                            isError = true
                            message = exception.localizedMessage ?: "Original photo URL nahi mil paya."
                        }
                    }
                    .addOnFailureListener { exception ->
                        saving = false
                        isError = true
                        message = exception.localizedMessage ?: "Original photo upload failed. Firebase Storage rules check karein."
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

        // Facebook-style cover banner with an upload/change control.
        Box(
            modifier = Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                tint = RoyalBlue, modifier = Modifier.size(38.dp))
                            Text("Your cover photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            FilledIconButton(
                onClick = { coverPicker.launch("image/*") },
                enabled = !saving,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = RoyalBlue, contentColor = Color.White
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
                        modifier = Modifier.size(104.dp), tint = RoyalBlue)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName.ifBlank { "PPA Talk user" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("PPA Talk member", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !saving) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit profile photo")
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(displayName.ifBlank { "PPA Talk user" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            if (username.isNotBlank()) Text("@$username", style = MaterialTheme.typography.bodyMedium, color = RoyalBlue)
            if (bio.isNotBlank()) Text(bio, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(postCount.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Posts", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(friendCount.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Friends", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("—", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text("Followers", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { message = "Profile dashboard: $postCount posts and $friendCount friends." }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.BarChart, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Dashboard")
                }
                OutlinedButton(onClick = { showStoryComposer = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddCircle, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Add to story")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Reels", "Photos", "More").forEach { tab ->
                    FilterChip(selected = profileTab == tab, onClick = { profileTab = tab }, label = { Text(tab) })
                }
            }
            if (profileTab == "Photos") {
                Text("Your Photos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val savedPhotos = listOfNotNull(
                    (photoOriginalUrl.ifBlank { photoUrl }).takeIf { it.isNotBlank() }?.let { "Profile photo" to it },
                    (coverPhotoOriginalUrl.ifBlank { coverPhotoUrl }).takeIf { it.isNotBlank() }?.let { "Cover photo" to it }
                )
                if (savedPhotos.isEmpty()) {
                    Text("Abhi koi photo nahi hai. Profile ya cover photo upload karne par yahan dikhegi.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedPhotos.forEach { (label, imageUrl) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AsyncImage(model = imageUrl, contentDescription = label,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop)
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        repeat(2 - savedPhotos.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text("Personal details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            "hometown" to hometown.trim(), "updatedAt" to timestamp
                        )
                        val publicUpdates = mapOf(
                            "uid" to uid, "displayName" to cleanName, "username" to cleanUsername, "bio" to bio.trim(),
                            "photoUrl" to photoUrl, "coverPhotoUrl" to coverPhotoUrl,
                            "hobby" to hobby.trim(), "school" to school.trim(),
                            "favoritePlace" to favoritePlace.trim(), "hometown" to hometown.trim(),
                            "profileVisibility" to profileVisibility,
                            "updatedAt" to timestamp
                        )
                        val usernameRef = db.collection("usernameClaims").document(cleanUsername)
                        val profileRef = db.collection("publicProfiles").document(uid)
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
                            null
                        }.addOnSuccessListener {
                            // Keep private fields in users/{uid}; never copy them to publicProfiles.
                            usersRef.set(privateUpdates, com.google.firebase.firestore.SetOptions.merge())
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
    if (showStoryComposer) CreateStoryDialog(uid, displayName, onDismiss = { showStoryComposer = false })
}


@Composable
fun PublicProfileScreen(uid: String, modifier: Modifier = Modifier) {
    var displayName by remember(uid) { mutableStateOf("PPA Talk user") }
    var username by remember(uid) { mutableStateOf("") }
    var bio by remember(uid) { mutableStateOf("") }
    var photoUrl by remember(uid) { mutableStateOf("") }
    var coverPhotoUrl by remember(uid) { mutableStateOf("") }
    var hobby by remember(uid) { mutableStateOf("") }
    var school by remember(uid) { mutableStateOf("") }
    var favoritePlace by remember(uid) { mutableStateOf("") }
    var hometown by remember(uid) { mutableStateOf("") }
    var isPrivate by remember(uid) { mutableStateOf(false) }
    var loading by remember(uid) { mutableStateOf(true) }
    var error by remember(uid) { mutableStateOf("") }
    DisposableEffect(uid) {
        val registration = FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    val isPermissionDenied = exception.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                    isPrivate = isPermissionDenied
                    error = if (isPermissionDenied) "" else (exception.localizedMessage ?: "Profile load nahi ho payi.")
                    loading = false
                }
                else if (snapshot != null && snapshot.exists()) {
                    displayName = snapshot.getString("displayName")?.takeIf { it.isNotBlank() } ?: "PPA Talk user"
                    username = snapshot.getString("username").orEmpty()
                    bio = snapshot.getString("bio").orEmpty()
                    photoUrl = snapshot.getString("photoUrl").orEmpty()
                    coverPhotoUrl = snapshot.getString("coverPhotoUrl").orEmpty()
                    hobby = snapshot.getString("hobby").orEmpty()
                    school = snapshot.getString("school").orEmpty()
                    favoritePlace = snapshot.getString("favoritePlace").orEmpty()
                    hometown = snapshot.getString("hometown").orEmpty()
                    isPrivate = snapshot.getString("profileVisibility") == "private"
                    loading = false
                } else { error = "Is user ki public profile abhi available nahi hai."; loading = false }
            }
        onDispose { registration.remove() }
    }
    if (isPrivate) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(56.dp))
            Text("This profile is private", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Only this user can view their profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RoyalBlue, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
        Box(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 12.dp)) {
            Card(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large) {
                if (coverPhotoUrl.isNotBlank()) AsyncImage(model = coverPhotoUrl, contentDescription = "Cover photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Photo, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(42.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (photoUrl.isNotBlank()) AsyncImage(model = photoUrl, contentDescription = "Profile photo", modifier = Modifier.size(100.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.AccountCircle, contentDescription = "Profile photo", modifier = Modifier.size(100.dp), tint = RoyalBlue)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (username.isNotBlank()) Text("@$username", style = MaterialTheme.typography.bodyMedium, color = RoyalBlue)
                Text("PPA Talk member", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loading) CircularProgressIndicator(Modifier.padding(20.dp))
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
        if (bio.isNotBlank()) Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Column(Modifier.padding(16.dp)) { Text("About", style = MaterialTheme.typography.titleMedium, color = Orange, fontWeight = FontWeight.Bold); Text(bio) } }
        val publicDetails = listOf(
            "Hobby" to hobby,
            "School" to school,
            "Favorite place" to favoritePlace,
            "Hometown" to hometown
        ).filter { it.second.isNotBlank() }
        if (publicDetails.isNotEmpty()) Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profile details", style = MaterialTheme.typography.titleMedium, color = Orange, fontWeight = FontWeight.Bold)
                publicDetails.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$label:", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(112.dp))
                        Text(value, modifier = Modifier.weight(1f))
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
                        it.getString("status") in listOf("pending", "accepted", "rejected")
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
        val usersListener = db.collection("publicProfiles").whereEqualTo("profileVisibility", "public").addSnapshotListener { snap, error ->
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
    DisposableEffect(uid) {
        val registration = db.collection("publicProfiles").whereEqualTo("profileVisibility", "public").addSnapshotListener { snap, _ ->
            if (snap != null) onlineFriends = snap.documents.filter { it.getBoolean("isOnline") == true }.map { it.id }.toSet()
        }
        onDispose { registration.remove() }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Friends", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("${friends.size} friends • ${onlineFriends.intersect(friends.map { it.uid }.toSet()).size} active now",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { search = "" }) { Icon(Icons.Default.PersonAddAlt1, contentDescription = "Find friends") }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = CircleShape, label = { Text("Search friends") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = search.isBlank(), onClick = { search = "" }, label = { Text("All friends") })
            FilterChip(selected = search == "__online__", onClick = { search = "__online__" }, label = { Text("Online") })
            FilterChip(selected = search == "__requests__", onClick = { search = "__requests__" }, label = { Text("Requests ${requests.size}") })
        }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text("Friend requests (${requests.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (requests.isEmpty() && search != "__requests__") Text("No new friend requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (search.isBlank() || search == "__requests__") requests.forEach { request ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onOpenProfile(request["fromUid"].orEmpty()) },
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                        Text(request["fromName"] ?: "PPA Talk user", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = {
                        val fromUid = request["fromUid"].orEmpty()
                        db.collection("friendRequests").document(request["id"].orEmpty()).update("status", "accepted")
                            .addOnSuccessListener {
                                db.collection("publicProfiles").document(uid).get().addOnSuccessListener { mine ->
                                    db.collection("publicProfiles").document(fromUid).get().addOnSuccessListener { theirs ->
                                        val batch = db.batch()
                                        batch.set(db.collection("friends").document(uid).collection("list").document(fromUid),
                                            mapOf("displayName" to (theirs.getString("displayName") ?: request["fromName"]),
                                                "uid" to fromUid, "photoUrl" to (theirs.getString("photoUrl") ?: "")))
                                        batch.set(db.collection("friends").document(fromUid).collection("list").document(uid),
                                            mapOf("displayName" to (mine.getString("displayName") ?: currentName),
                                                "uid" to uid, "photoUrl" to (mine.getString("photoUrl") ?: "")))
                                        batch.commit().addOnSuccessListener { status = "Friend request accepted." }
                                            .addOnFailureListener { status = it.localizedMessage ?: "Friend list update failed." }
                                    }.addOnFailureListener { status = it.localizedMessage ?: "Could not load friend profile." }
                                }.addOnFailureListener { status = it.localizedMessage ?: "Could not load your profile." }
                            }.addOnFailureListener { status = it.localizedMessage ?: "Could not accept request." }
                    }) { Text("Accept") }
                    TextButton(onClick = {
                        db.collection("friendRequests").document(request["id"].orEmpty()).update("status", "rejected")
                            .addOnSuccessListener { status = "Request declined." }
                            .addOnFailureListener { status = it.localizedMessage ?: "Could not decline request. Check Firestore rules." }
                    }) { Text("Reject", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (search != "__requests__") Text("Your friends (${friends.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (friends.isEmpty() && search != "__requests__") Text("Your accepted friends will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        friends.filter { friend ->
            (search != "__online__" || friend.uid in onlineFriends) &&
            (search.isBlank() || search == "__online__" || friend.name.contains(search, true))
        }.forEach { friend ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp)) {
                        Surface(modifier = Modifier.size(44.dp).align(Alignment.TopStart), shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant, onClick = { onOpenProfile(friend.uid) }) {
                            if (friend.photoUrl.isNotBlank()) AsyncImage(model = friend.photoUrl,
                                contentDescription = "Open friend profile",
                                modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.AccountCircle, contentDescription = "Open profile", tint = RoyalBlue)
                        }
                        if (friend.uid in onlineFriends) Surface(
                            modifier = Modifier.size(13.dp).align(Alignment.BottomEnd),
                            shape = CircleShape, color = Color(0xFF31C46C), border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                        ) {}
                    }
                    TextButton(onClick = { onOpenProfile(friend.uid) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 10.dp)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(friend.name, fontWeight = FontWeight.SemiBold)
                            Text(if (friend.uid in onlineFriends) "Active now" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (friend.uid in onlineFriends) Color(0xFF239B56) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = { onMessage(friend.uid, friend.name) }) {
                        Icon(Icons.Default.Chat, contentDescription = null)
                        Spacer(Modifier.width(5.dp)); Text("Message")
                    }
                }
            }
        }
        Text("Find people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val matches = users.filter { person ->
            person.name.contains(search, true) &&
                friends.none { friend -> friend.uid == person.uid } &&
                requests.none { request -> request["fromUid"] == person.uid }
        }.take(30)
        if (matches.isEmpty()) Text("No people found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        matches.forEach { person ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant, onClick = { onOpenProfile(person.uid) }) {
                        if (person.photoUrl.isNotBlank()) AsyncImage(model = person.photoUrl,
                            contentDescription = "Open profile photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBlue)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        TextButton(onClick = { onOpenProfile(person.uid) }, contentPadding = PaddingValues(0.dp)) {
                            Text(person.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (sentRequests.contains(person.uid)) Text("Requested", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else OutlinedButton(onClick = {
                        sendFriendRequestSafely(db, uid, person.uid, currentName, person.name) { _, detail ->
                            status = detail
                        }
                    }) { Text("Add Friend") }
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
                        mapOf("uid" to otherUid, "lastMessage" to (doc.getString("lastMessage") ?: "Start a conversation"))
                    }
                    loading = false
                }
            }
        onDispose { registration.remove() }
    }
    var people by remember(uid) { mutableStateOf<List<PPAUser>>(emptyList()) }
    var onlinePeople by remember(uid) { mutableStateOf<Set<String>>(emptySet()) }
    DisposableEffect(uid) {
        val presenceRegistration = db.collection("publicProfiles").whereEqualTo("profileVisibility", "public").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) onlinePeople = snapshot.documents.filter { it.getBoolean("isOnline") == true }.map { it.id }.toSet()
        }
        onDispose { presenceRegistration.remove() }
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
    val visible = (chats.mapNotNull { chat -> people.firstOrNull { it.uid == chat["uid"] }?.let { person ->
        person.copy(name = person.name)
    } } + people.filter { friend -> chats.none { it["uid"] == friend.uid } })
        .distinctBy { it.uid }.filter { it.name.contains(search, true) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { }) { Icon(Icons.Default.ArrowBack, contentDescription = "Chats", tint = MaterialTheme.colorScheme.onSurface) }
            Text("Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { }) { Icon(Icons.Default.Settings, contentDescription = "Chat settings") }
            IconButton(onClick = { }) { Icon(Icons.Default.Edit, contentDescription = "New message") }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true, shape = CircleShape, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 14.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            people.forEach { person ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                    Box(Modifier.size(66.dp)) {
                        Surface(onClick = { onOpenChat(person.uid, person.name) }, modifier = Modifier.size(62.dp).align(Alignment.TopStart), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            if (person.photoUrl.isNotBlank()) AsyncImage(model = person.photoUrl, contentDescription = person.name, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RoyalBlue, modifier = Modifier.padding(5.dp))
                        }
                        if (person.uid in onlinePeople) Surface(Modifier.size(14.dp).align(Alignment.BottomEnd), shape = CircleShape,
                            color = Color(0xFF31C46C), border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {}
                    }
                    Text(person.name, maxLines = 2, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        if (friendsLoadError.isNotBlank()) Text(friendsLoadError, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (people.isEmpty() && !loading && friendsLoadError.isBlank()) Text("Abhi koi accepted friend sync nahi hua. Friends tab mein friend list check karein.",
            modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Inbox", "Unread", "Marketplace", "Communities").forEach { tab ->
                FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
            }
        }
        if (selectedTab == "Inbox" || selectedTab == "Unread") {
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
                        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(chats.firstOrNull { it["uid"] == person.uid }?.get("lastMessage") ?: "Tap to message", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Icons.Default.Circle, contentDescription = "Open chat", tint = RoyalBlue, modifier = Modifier.size(8.dp))
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$selectedTab coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun ChatScreen(currentUid: String, friendUid: String, friendName: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
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
        chatRef.set(mapOf(
            "participantIds" to listOf(currentUid, friendUid).sorted(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        ), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                registration = chatRef.collection("messages")
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .addSnapshotListener { snap, exception ->
                        if (exception != null) error = exception.localizedMessage ?: "Messages could not load. Check Firestore chat permissions."
                        else if (snap != null) messages = snap.documents.map { doc ->
                            mapOf("id" to doc.id, "uid" to (doc.getString("uid") ?: ""), "text" to (doc.getString("text") ?: ""))
                        }
                    }
            }
            .addOnFailureListener { exception ->
                error = exception.localizedMessage ?: "Chat permission denied. Please check Firestore rules."
            }
        onDispose { registration?.remove() }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text(friendName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                    Surface(color = if (mine) RoyalBlue else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                        Text(msg["text"] as? String ?: "", modifier = Modifier.widthIn(max = 280.dp).padding(12.dp),
                            color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
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
                        chatRef.collection("messages").add(mapOf("uid" to currentUid, "text" to messageText,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).addOnSuccessListener {
                            chatRef.set(mapOf("lastMessage" to messageText, "lastMessageAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "lastSenderUid" to currentUid), com.google.firebase.firestore.SetOptions.merge())
                        }
                    }.addOnSuccessListener { draft = ""; sending = false }
                        .addOnFailureListener { sending = false; error = it.localizedMessage ?: "Message send failed. Check Firestore rules." }
                }
            }) { Icon(Icons.Default.Send, contentDescription = "Send message", tint = RoyalBlue) }
        }
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

package com.ppatalk.app

import android.os.Bundle
import android.app.Activity
import android.net.Uri
import android.content.Intent
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
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
import com.google.firebase.functions.FirebaseFunctionsException
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

private val RoyalBlue = Color(0xFF2457D6)
private val Orange = Color(0xFFF27A21)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PPATalkApp() }
    }
}

@Composable
fun PPATalkApp() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val preferences = remember { context.getSharedPreferences("ppa_talk_preferences", android.content.Context.MODE_PRIVATE) }
    var isDarkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
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
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var otpRequested by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
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

    if (usePhone) {
        PhonePasswordAuthScreen(onAuthenticated = onAuthenticated, onSwitchToEmail = { usePhone = false })
        return
    }

    fun finishPhoneSignIn(credential: com.google.firebase.auth.PhoneAuthCredential, fullPhone: String) {
        busy = true
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                if (firebaseUser == null) {
                    busy = false
                    message = "Phone verified, but account details were unavailable."
                } else if (isSignUp && result.additionalUserInfo?.isNewUser == false) {
                    auth.signOut()
                    busy = false
                    message = "This mobile number is already registered. Please Sign In."
                } else if (!isSignUp && result.additionalUserInfo?.isNewUser == true) {
                    firebaseUser.delete().addOnCompleteListener {
                        auth.signOut()
                        busy = false
                        message = "No account found for this number. Please Create account."
                    }
                } else {
                    val profileName = if (isSignUp) name.trim()
                        else firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: "PPA Talk user"
                    firebaseUser.updateProfile(
                        com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(profileName).build()
                    ).addOnCompleteListener {
                        val db = FirebaseFirestore.getInstance()
                        val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                        val batch = db.batch()
                        batch.set(db.collection("users").document(firebaseUser.uid), mapOf(
                            "uid" to firebaseUser.uid, "phoneNumber" to (firebaseUser.phoneNumber ?: fullPhone),
                            "updatedAt" to timestamp
                        ), com.google.firebase.firestore.SetOptions.merge())
                        batch.set(db.collection("publicProfiles").document(firebaseUser.uid), mapOf(
                            "uid" to firebaseUser.uid, "displayName" to profileName,
                            "updatedAt" to timestamp
                        ), com.google.firebase.firestore.SetOptions.merge())
                        batch.commit().addOnCompleteListener { task ->
                            busy = false
                            // Authentication is already successful. A profile write failure must not
                            // lock the user out; surface it as a non-blocking warning.
                            if (!task.isSuccessful) {
                                message = "Phone verified. Profile sync pending: " +
                                    (task.exception?.localizedMessage ?: "Firestore permission denied.")
                            }
                            onAuthenticated()
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                busy = false
                message = exception.localizedMessage ?: "Incorrect or expired OTP."
            }
    }

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
                                batch.commit().addOnCompleteListener { task ->
                                    busy = false
                                    if (!task.isSuccessful) {
                                        message = "Google login successful. Profile sync pending: " +
                                            (task.exception?.localizedMessage ?: "Firestore permission denied.")
                                    }
                                    onAuthenticated()
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
            // RESULT_CANCELED can also hide a Google OAuth configuration error.
            // Read the status returned by Google Play Services before showing a message.
            val statusCode = try {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                null
            } catch (exception: ApiException) {
                exception.statusCode
            } catch (_: Exception) {
                null
            }
            message = when (statusCode) {
                10 -> "Google sign-in configuration error (DEVELOPER_ERROR). Check SHA-1/SHA-256 fingerprints and OAuth client IDs in Firebase."
                7 -> "Google sign-in network error. Check your internet connection and try again."
                12500 -> "Google sign-in failed. Check that Google provider is enabled in Firebase Authentication and OAuth setup is complete."
                12501 -> "Google sign-in was cancelled. Please select your Google account and try again."
                else -> "Google sign-in did not complete (resultCode=${result.resultCode}, statusCode=${statusCode ?: "unknown"}). Check Firebase Google provider and app SHA fingerprints."
            }
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
            FilterChip(selected = !usePhone, onClick = { usePhone = false; otpRequested = false; verificationId = null; message = "" },
                label = { Text("Email") }, modifier = Modifier.weight(1f))
            FilterChip(selected = usePhone, onClick = { usePhone = true; otpRequested = false; verificationId = null; message = "" },
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
        if (usePhone && otpRequested) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = otpCode, onValueChange = { otpCode = it.filter(Char::isDigit).take(6) },
                label = { Text("SMS verification code") }, placeholder = { Text("Enter 6-digit OTP") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = authFieldColors)
            Text("We sent a verification code to $countryCode$phoneNumber",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!usePhone) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text(if (isSignUp) "At least 8 characters" else "Enter your password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authFieldColors)
        }
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                message = ""
                val auth = FirebaseAuth.getInstance()
                if (!usePhone && password.length < 8) {
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
                    if (!otpRequested) {
                        busy = true
                        val activity = context as? Activity
                        if (activity == null) {
                            busy = false
                            message = "Phone verification is not available on this screen."
                            return@Button
                        }
                        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                                finishPhoneSignIn(credential, fullPhone)
                            }
                            override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                                busy = false
                                message = exception.localizedMessage ?: "Could not send OTP. Check Firebase Phone sign-in settings."
                            }
                            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                                verificationId = id
                                otpRequested = true
                                busy = false
                                message = "OTP sent. Please check your SMS."
                            }
                            override fun onCodeAutoRetrievalTimeOut(id: String) {
                                verificationId = id
                                otpRequested = true
                                busy = false
                                message = "Enter the OTP from your SMS."
                            }
                        }
                        val options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(fullPhone)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity)
                            .setCallbacks(callbacks)
                            .build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                    } else {
                        val id = verificationId
                        if (id.isNullOrBlank() || otpCode.length != 6) {
                            message = "Enter the 6-digit OTP sent to your mobile."
                            return@Button
                        }
                        busy = true
                        val credential = PhoneAuthProvider.getCredential(id, otpCode)
                        finishPhoneSignIn(credential, fullPhone)
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
                                        val db = FirebaseFirestore.getInstance()
                                        val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                                        val batch = db.batch()
                                        batch.set(db.collection("users").document(firebaseUser.uid), mapOf(
                                            "uid" to firebaseUser.uid, "email" to email.trim(),
                                            "createdAt" to timestamp
                                        ))
                                        batch.set(db.collection("publicProfiles").document(firebaseUser.uid), mapOf(
                                            "uid" to firebaseUser.uid, "displayName" to profileName,
                                            "createdAt" to timestamp
                                        ))
                                        batch.commit().addOnCompleteListener { task ->
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
                            .addOnSuccessListener { result ->
                                val signedInUser = result.user
                                if (signedInUser == null) {
                                    busy = false
                                    message = "Login succeeded but account details were unavailable."
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
                                        if (!task.isSuccessful) {
                                            message = "Login successful. Profile sync pending: " +
                                                (task.exception?.localizedMessage ?: "Firestore permission denied.")
                                        }
                                        onAuthenticated()
                                    }
                                }
                            }
                            .addOnFailureListener { exception ->
                                busy = false
                                val authCode = (exception as? FirebaseAuthException)?.errorCode
                                message = when (authCode) {
                                    "ERROR_INVALID_CREDENTIAL",
                                    "ERROR_WRONG_PASSWORD",
                                    "ERROR_USER_NOT_FOUND" -> "Wrong email or password. Please check your details and try again."
                                    "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                                    "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact support."
                                    "ERROR_TOO_MANY_REQUESTS" -> "Too many login attempts. Please try again later."
                                    "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your internet connection and try again."
                                    else -> "Wrong email or password. Please check your details and try again."
                                }
                            }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait..." else if (usePhone && !otpRequested) "Send OTP" else if (usePhone) "Verify OTP" else if (isSignUp) "Sign Up" else "Sign In")
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
        if (usePhone && otpRequested) {
            TextButton(enabled = !busy, onClick = {
                otpRequested = false
                verificationId = null
                otpCode = ""
                message = "Enter your number and request a new OTP."
            }) { Text("Change mobile number") }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhonePasswordAuthScreen(onAuthenticated: () -> Unit, onSwitchToEmail: () -> Unit) {
    var mode by remember { mutableStateOf("login") } // login, signup, forgot
    var countryCode by remember { mutableStateOf("+91") }
    var phone by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var otpSent by remember { mutableStateOf(false) }
    var otpVerified by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val fullPhone = countryCode + phone.filter(Char::isDigit)
    val codes = listOf("India (+91)" to "+91", "United States (+1)" to "+1",
        "United Kingdom (+44)" to "+44", "United Arab Emirates (+971)" to "+971",
        "Canada (+1)" to "+1", "Australia (+61)" to "+61")

    fun validPassword(value: String): Boolean =
        value.length in 8..128 && value.any { it.isUpperCase() } &&
            value.any { it.isLowerCase() } && value.any { it.isDigit() } &&
            value.any { !it.isLetterOrDigit() }

    fun functionErrorMessage(exception: Exception, fallback: String): String {
        val functionsError = exception as? FirebaseFunctionsException
        return when (functionsError?.code) {
            FirebaseFunctionsException.Code.NOT_FOUND ->
                "Phone login service is not deployed in asia-south1. Firebase project/functions setup is required."
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                "Phone service is temporarily unavailable. Please try again."
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Mobile number or password is incorrect, or your phone verification has expired."
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                "Phone verification does not match this account. Please verify your number again."
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                functionsError.message ?: "Please check your mobile number and password."
            FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                "An account already exists for this mobile number. Please sign in."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                "Too many attempts. Please wait before trying again."
            else -> functionsError?.message ?: exception.localizedMessage ?: fallback
        }
    }

    fun resetOtpState() {
        otp = ""
        verificationId = null
        otpSent = false
        otpVerified = false
    }

    fun verifyPhoneCredential(credential: com.google.firebase.auth.PhoneAuthCredential) {
        busy = true
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val signedIn = result.user
                if (signedIn == null) {
                    busy = false
                    message = "Phone verification succeeded, but account details were unavailable."
                } else if (mode == "forgot" && result.additionalUserInfo?.isNewUser == true) {
                    signedIn.delete().addOnCompleteListener {
                        auth.signOut()
                        busy = false
                        message = "No account found for this mobile number."
                        resetOtpState()
                    }
                } else {
                    otpVerified = true
                    busy = false
                    message = if (mode == "signup") "Mobile verified. Now create your password."
                        else "Mobile verified. Set your new password."
                }
            }
            .addOnFailureListener {
                busy = false
                message = it.localizedMessage ?: "OTP verification failed."
            }
    }

    fun requestOtp() {
        if (!fullPhone.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
            message = "Enter a valid mobile number."
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            message = "Phone verification is not available on this screen."
            return
        }
        busy = true
        message = ""
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                verifyPhoneCredential(credential)
            }
            override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                busy = false
                message = exception.localizedMessage ?: "Could not send OTP. Check Firebase Phone Auth settings."
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                otpSent = true
                busy = false
                message = "OTP sent. Please check your SMS."
            }
            override fun onCodeAutoRetrievalTimeOut(id: String) {
                verificationId = id
                otpSent = true
                busy = false
                message = "Enter the OTP received by SMS."
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(fullPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun submitPassword() {
        if (!validPassword(password)) {
            message = "Use 8–128 characters with uppercase, lowercase, number and special character."
            return
        }
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.phoneNumber != fullPhone) {
            message = "Please verify your mobile number again."
            resetOtpState()
            return
        }
        busy = true
        message = ""
        val callableName = if (mode == "signup") "registerWithPhonePassword" else "resetPhonePassword"
        FirebaseFunctions.getInstance("asia-south1").getHttpsCallable(callableName)
            .call(mapOf("phoneNumber" to fullPhone, "password" to password))
            .addOnSuccessListener {
                if (mode == "forgot") {
                    auth.signOut()
                    mode = "login"
                    password = ""
                    resetOtpState()
                    busy = false
                    message = "Password reset successful. Sign in with your new password."
                } else {
                    val profileName = fullName.trim()
                    currentUser.updateProfile(
                        com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(profileName).build()
                    ).addOnCompleteListener {
                        val db = FirebaseFirestore.getInstance()
                        val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
                        val batch = db.batch()
                        batch.set(db.collection("users").document(currentUser.uid), mapOf(
                            "uid" to currentUser.uid, "phoneNumber" to fullPhone,
                            "createdAt" to timestamp, "updatedAt" to timestamp
                        ), com.google.firebase.firestore.SetOptions.merge())
                        batch.set(db.collection("publicProfiles").document(currentUser.uid), mapOf(
                            "uid" to currentUser.uid, "displayName" to profileName,
                            "createdAt" to timestamp, "updatedAt" to timestamp
                        ), com.google.firebase.firestore.SetOptions.merge())
                        batch.commit().addOnCompleteListener { task ->
                            busy = false
                            if (!task.isSuccessful) {
                                message = "Account created. Profile sync pending: " +
                                    (task.exception?.localizedMessage ?: "Firestore permission denied.")
                            }
                            onAuthenticated()
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                busy = false
                message = functionErrorMessage(exception, "Could not save password.")
            }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).imePadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PPA Talk", style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold, color = RoyalBlue)
        Text("Create. Connect. Share.", color = Orange)
        Spacer(Modifier.height(22.dp))
        Text(when (mode) {
            "signup" -> "Create your account"
            "forgot" -> "Reset password"
            else -> "Welcome back"
        }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        if (mode == "signup" && !otpVerified) {
            OutlinedTextField(value = fullName, onValueChange = { fullName = it },
                label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (!otpVerified) {
            ExposedDropdownMenuBox(expanded = false, onExpandedChange = { }) {
                OutlinedTextField(value = codes.firstOrNull { it.second == countryCode }?.first ?: countryCode,
                    onValueChange = {}, readOnly = true, label = { Text("Country code") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true)
            }
            OutlinedTextField(value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(14) },
                label = { Text("Mobile number") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        }

        if (mode == "login") {
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        } else if (!otpVerified) {
            if (otpSent) {
                OutlinedTextField(value = otp, onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit OTP") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        } else {
            OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text(if (mode == "signup") "Create password" else "New password") },
                placeholder = { Text("8+ chars, upper/lower/number/symbol") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text("Password: 8+ characters, uppercase, lowercase, number and special character.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                when {
                    mode == "login" -> {
                        if (!fullPhone.matches(Regex("^\\+[1-9]\\d{7,14}$")) || password.isBlank()) {
                            message = "Enter your mobile number and password."
                        } else {
                            busy = true
                            message = ""
                            FirebaseFunctions.getInstance("asia-south1")
                                .getHttpsCallable("loginWithPhonePassword")
                                .call(mapOf("phoneNumber" to fullPhone, "password" to password))
                                .addOnSuccessListener { result ->
                                    val data = result.getData() as? Map<*, *>
                                    val token = data?.get("token") as? String
                                    if (token.isNullOrBlank()) {
                                        busy = false
                                        message = "Authentication server returned an invalid response."
                                    } else {
                                        auth.signInWithCustomToken(token)
                                            .addOnSuccessListener { busy = false; onAuthenticated() }
                                            .addOnFailureListener { busy = false; message = it.localizedMessage ?: "Login failed." }
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    busy = false
                                    message = functionErrorMessage(exception, "Login failed. Please try again.")
                                }
                        }
                    }
                    !otpVerified && !otpSent -> {
                        if (mode == "signup" && fullName.isBlank()) message = "Enter your full name."
                        else requestOtp()
                    }
                    !otpVerified -> {
                        val id = verificationId
                        if (id.isNullOrBlank() || otp.length != 6) message = "Enter the 6-digit OTP."
                        else verifyPhoneCredential(PhoneAuthProvider.getCredential(id, otp))
                    }
                    else -> submitPassword()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait..." else when {
                mode == "login" -> "Sign In"
                !otpVerified && !otpSent -> "Send OTP"
                !otpVerified -> "Verify OTP"
                mode == "signup" -> "Create Account"
                else -> "Reset Password"
            })
        }

        if (mode == "login") {
            TextButton(enabled = !busy, onClick = {
                mode = "forgot"; password = ""; message = ""; resetOtpState()
            }) { Text("Forgot password?") }
            TextButton(enabled = !busy, onClick = {
                mode = "signup"; password = ""; message = ""; resetOtpState()
            }) { Text("Create account") }
        } else {
            TextButton(enabled = !busy, onClick = {
                mode = "login"; password = ""; message = ""; resetOtpState()
            }) { Text("Back to Sign In") }
            if (otpSent && !otpVerified) {
                TextButton(enabled = !busy, onClick = { resetOtpState(); requestOtp() }) {
                    Text("Resend OTP")
                }
            }
        }
        if (message.isNotBlank()) {
            Text(message, color = if (message.contains("successful") || message.contains("sent") ||
                message.contains("verified") || message.contains("created")) RoyalBlue
                else MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(enabled = !busy, onClick = onSwitchToEmail) { Text("Use Email / Google instead") }
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
    var selected by remember { mutableStateOf("Home") }
    var activeChatUid by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var isPrivateAccount by remember { mutableStateOf(false) }
    DisposableEffect(uid) {
        val registration = FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
            .addSnapshotListener { snapshot, _ ->
                isPrivateAccount = snapshot?.getBoolean("isPrivate") ?: false
            }
        onDispose { registration.remove() }
    }
    // Private accounts enter a profile-only experience: no feed, discovery, friends,
    // reels, marketplace, notifications, or app navigation is rendered.
    if (isPrivateAccount) {
        ProfileScreen(uid = uid, email = email, initialName = displayName, modifier = Modifier.fillMaxSize())
        return
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
                        IconButton(onClick = { selected = "Search" }) {
                            Icon(Icons.Default.Search, contentDescription = "Search",
                                modifier = Modifier.size(27.dp))
                        }
                        IconButton(onClick = { selected = "Friends" }) {
                            Icon(Icons.Default.Chat, contentDescription = "Messenger",
                                modifier = Modifier.size(25.dp))
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = { menuExpanded = false; selected = "Settings" })
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
                        IconButton(onClick = { selected = label }, modifier = Modifier.weight(1f)) {
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
            "Home" -> HomeFeed(uid, Modifier.padding(padding)) { selected = "Create" }
            "Profile" -> ProfileScreen(uid = uid, email = email, initialName = displayName, modifier = Modifier.padding(padding))
            "Menu" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Menu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RoyalBlue)
                listOf("Profile", "Settings", "Search").forEach { item ->
                    OutlinedButton(onClick = { selected = item }, modifier = Modifier.fillMaxWidth()) { Text(item) }
                }
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
            }
            "Marketplace" -> SimplePage("Marketplace", "Discover products and community listings", Modifier.padding(padding))
            "Search" -> SimplePage("Search", "Find people and public posts", Modifier.padding(padding))
            "Create" -> CreatePostScreen(uid, displayName, email, Modifier.padding(padding))
            "Settings" -> SettingsScreen(isDarkMode, onThemeModeChange, Modifier.padding(padding))
            "Friends" -> FriendsScreen(uid, displayName, { friendUid, friendName ->
                activeChatUid = friendUid
                activeChatName = friendName
            }, Modifier.padding(padding))
            "Reels" -> SimplePage("Reels", "Short videos from the PPA Talk community.", Modifier.padding(padding))
            else -> SimplePage("Notifications", "Your activity will appear here", Modifier.padding(padding))
        }
    }
}

data class FeedPost(val id: String, val uid: String, val authorName: String, val text: String, val createdAt: Date?, val mediaUrl: String = "", val mediaType: String = "", val postType: String = "post")
data class FeedComment(val id: String, val uid: String, val authorName: String, val text: String, val parentId: String, val createdAt: Date?)

@Composable
fun HomeFeed(currentUid: String, modifier: Modifier = Modifier, onCreatePost: () -> Unit = {}) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedPost by remember { mutableStateOf<FeedPost?>(null) }
    var permittedAuthors by remember(currentUid) { mutableStateOf(setOf(currentUid)) }
    val db = remember { FirebaseFirestore.getInstance() }

    // Build the feed allow-list from public accounts plus accepted friends.
    DisposableEffect(currentUid) {
        val profilesListener = db.collection("publicProfiles").addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                error = exception.localizedMessage ?: "Could not load public profiles."
            } else if (snapshot != null) {
                val publicIds = snapshot.documents
                    .filter { it.getBoolean("isPrivate") != true }
                    .map { it.id }.toSet()
                val friendIds = permittedAuthors - currentUid
                permittedAuthors = publicIds + friendIds + currentUid
            }
        }
        val friendsListener = db.collection("friends").document(currentUid).collection("list")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage ?: "Could not load your friends."
                } else if (snapshot != null) {
                    val friendIds = snapshot.documents.map { it.id }.toSet()
                    val publicIds = permittedAuthors - currentUid - friendIds
                    permittedAuthors = publicIds + friendIds + currentUid
                }
            }
        onDispose { profilesListener.remove(); friendsListener.remove() }
    }

    // Firestore rules authorize each exact author query; never request the entire posts collection.
    DisposableEffect(permittedAuthors) {
        loading = true
        error = ""
        val authorPosts = mutableMapOf<String, List<FeedPost>>()
        val registrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        fun publishPosts() {
            posts = authorPosts.values.flatten()
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt?.time ?: 0L }
        }
        permittedAuthors.forEach { authorUid ->
            val registration = db.collection("posts")
                .whereEqualTo("uid", authorUid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = exception.localizedMessage ?: "Could not load posts for an allowed account."
                        authorPosts.remove(authorUid)
                    } else if (snapshot != null) {
                        authorPosts[authorUid] = snapshot.documents.map { doc ->
                            FeedPost(doc.id, doc.getString("uid") ?: "",
                                doc.getString("authorName") ?: "PPA Talk user",
                                doc.getString("text") ?: "",
                                doc.getTimestamp("createdAt")?.toDate(), doc.getString("mediaUrl") ?: "",
                                doc.getString("mediaType") ?: "", doc.getString("postType") ?: "post")
                        }
                    }
                    publishPosts()
                    loading = registrations.size < permittedAuthors.size
                    if (exception == null) error = ""
                }
            registrations.add(registration)
        }
        if (permittedAuthors.isEmpty()) {
            loading = false
            posts = emptyList()
        }
        onDispose { registrations.forEach { it.remove() } }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item { HomeComposerCard(onCreatePost = onCreatePost) }
        item { StoriesStrip() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("News Feed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = RoyalBlue)
                Spacer(Modifier.weight(1f))
                Text("For You", color = RoyalBlue, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (loading) item { CircularProgressIndicator() }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error.isBlank()) item { Text("No posts yet. Add friends or follow public accounts to see posts.") }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(38.dp))
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
fun StoriesStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f).height(104.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(32.dp))
                    Text("Create story", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            listOf("Friends", "Community", "Explore").forEachIndexed { index, title ->
                Card(
                    modifier = Modifier.weight(1f).height(104.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = listOf(RoyalBlue.copy(alpha = 0.28f), Orange.copy(alpha = 0.24f), RoyalBlue.copy(alpha = 0.18f))[index]
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
    var selectedReaction by remember(post.id, currentUid) { mutableStateOf("") }
    var showReactionPicker by remember(post.id, currentUid) { mutableStateOf(false) }
    var likeCount by remember(post.id) { mutableIntStateOf(0) }
    var commentCount by remember(post.id) { mutableIntStateOf(0) }
    var actionBusy by remember(post.id) { mutableStateOf(false) }
    val db = remember { FirebaseFirestore.getInstance() }
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
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RoyalBlue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorName, fontWeight = FontWeight.SemiBold)
                    if (post.createdAt != null) Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(post.createdAt),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                    update = { view -> if (!view.isPlaying) view.setVideoURI(Uri.parse(post.mediaUrl)) },
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !actionBusy, onClick = { showReactionPicker = !showReactionPicker }) {
                    if (liked) {
                        Text(selectedReaction.ifBlank { "👍" }, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "Reactions", tint = RoyalBlue)
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(if (liked) "Reacted" else "Like")
                    Spacer(Modifier.width(5.dp)); Text(likeCount.toString())
                }
                TextButton(onClick = onCommentsClick) {
                    Icon(Icons.Default.ModeComment, contentDescription = "Comments", tint = RoyalBlue)
                    Spacer(Modifier.width(5.dp)); Text("Comment"); Spacer(Modifier.width(5.dp)); Text(commentCount.toString())
                }
                val context = LocalContext.current
                TextButton(onClick = {
                    val shareText = if (post.mediaUrl.isBlank()) post.text else post.text + "\\n" + post.mediaUrl
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share PPA Talk post"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Orange)
                    Spacer(Modifier.width(4.dp)); Text("Share")
                }
            }
            if (post.uid == currentUid) {
                var menuExpanded by remember(post.id) { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Post options")
                        Text("Options")
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

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Create a post", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = RoyalBlue)
        Text("Share a photo, video or update with the PPA Talk community.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = postText,
            onValueChange = { postText = it; success = false; message = "" },
            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
            label = { Text("What's on your mind?") },
            placeholder = { Text("Write your post...") }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Photo, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Photo")
            }
            OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.VideoCall, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Video / Reel")
            }
        }
        selectedMedia?.let { uri ->
            Text(if (mediaType == "video") "Video selected" else "Photo selected", color = RoyalBlue)
            if (mediaType == "image") {
                AsyncImage(model = uri, contentDescription = "Selected photo", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Crop)
            } else {
                Text("Video ready to upload. You can publish it as a Reel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { selectedMedia = null; mediaType = "" }) { Text("Remove attachment") }
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
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Uploading..." else "Publish Post / Reel") }
        if (postText.trim().length > 5000) Text("Post must be 5,000 characters or less.", color = MaterialTheme.colorScheme.error)
        if (message.isNotBlank()) Text(message, color = if (success) RoyalBlue else MaterialTheme.colorScheme.error)
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
    var isPrivateProfile by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

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
                bio = snapshot.getString("bio") ?: ""
                photoUrl = snapshot.getString("photoUrl") ?: ""
                coverPhotoUrl = snapshot.getString("coverPhotoUrl") ?: ""
                isPrivateProfile = snapshot.getBoolean("isPrivate") ?: false
            }
        }
        onDispose { privateRegistration.remove(); publicRegistration.remove() }
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
                        FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
                            .set(mapOf("photoUrl" to downloadUri.toString(), "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
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
                        FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
                            .set(mapOf("coverPhotoUrl" to downloadUri.toString(), "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
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
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Private profile", fontWeight = FontWeight.Bold)
                        Text("Private mode shows only this profile screen in the app.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isPrivateProfile, onCheckedChange = { enabled ->
                        isPrivateProfile = enabled
                        saving = true
                        FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)
                            .update(mapOf("isPrivate" to enabled, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                            .addOnCompleteListener { task ->
                                saving = false
                                if (task.isSuccessful) {
                                    message = if (enabled) "Private profile enabled." else "Private profile disabled."
                                    isError = false
                                } else {
                                    isPrivateProfile = !enabled
                                    message = task.exception?.localizedMessage ?: "Privacy setting save nahi hui."
                                    isError = true
                                }
                            }
                    }, enabled = !saving && !loading)
                }
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
                    if (cleanName.isBlank()) {
                        message = "Display name khali nahi ho sakta."
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
                            "uid" to uid, "displayName" to cleanName, "bio" to bio.trim(),
                            "photoUrl" to photoUrl, "coverPhotoUrl" to coverPhotoUrl,
                            "isPrivate" to isPrivateProfile, "updatedAt" to timestamp
                        )
                        val batch = db.batch()
                        batch.set(db.collection("users").document(uid), privateUpdates, com.google.firebase.firestore.SetOptions.merge())
                        batch.set(db.collection("publicProfiles").document(uid), publicUpdates, com.google.firebase.firestore.SetOptions.merge())
                        batch.commit()
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
                Text(message, color = if (isError) MaterialTheme.colorScheme.error else RoyalBlue)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}


data class PPAUser(val uid: String, val name: String, val photoUrl: String = "")

@Composable
fun FriendsScreen(uid: String, currentName: String, onMessage: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val db = remember { FirebaseFirestore.getInstance() }
    var search by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<PPAUser>>(emptyList()) }
    var requests by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var friends by remember { mutableStateOf<List<PPAUser>>(emptyList()) }
    var sentRequests by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val usersListener = db.collection("publicProfiles").addSnapshotListener { snap, _ ->
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
            .addSnapshotListener { snap, _ ->
                if (snap != null) friends = snap.documents.map { doc ->
                    PPAUser(doc.id, doc.getString("displayName") ?: "PPA Talk user",
                        doc.getString("photoUrl") ?: "")
                }
            }
        onDispose { usersListener.remove(); requestsListener.remove(); sentListener.remove(); friendsListener.remove() }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Friends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RoyalBlue)
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("Search people by name") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) })
        if (status.isNotBlank()) Text(status, color = RoyalBlue, style = MaterialTheme.typography.bodySmall)
        Text("Friend requests (${requests.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (requests.isEmpty()) Text("No new friend requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        requests.forEach { request ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(request["fromName"] ?: "PPA Talk user", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        val fromUid = request["fromUid"].orEmpty()
                        db.collection("friendRequests").document(request["id"].orEmpty()).update("status", "accepted")
                            .addOnSuccessListener {
                                db.collection("friends").document(uid).collection("list").document(fromUid)
                                    .set(mapOf("displayName" to currentName, "uid" to uid))
                                db.collection("friends").document(fromUid).collection("list").document(uid)
                                    .set(mapOf("displayName" to (request["fromName"] ?: "PPA Talk user"), "uid" to fromUid))
                                status = "Friend request accepted."
                            }.addOnFailureListener { status = it.localizedMessage ?: "Could not accept request." }
                    }) { Text("Accept") }
                    TextButton(onClick = {
                        db.collection("friendRequests").document(request["id"].orEmpty()).update("status", "rejected")
                            .addOnSuccessListener { status = "Request declined." }
                    }) { Text("Reject", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Text("Your friends (${friends.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (friends.isEmpty()) Text("Your accepted friends will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        friends.forEach { friend ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(36.dp))
                    Text(friend.name, modifier = Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
                    Button(onClick = { onMessage(friend.uid, friend.name) }) {
                        Icon(Icons.Default.Chat, contentDescription = null)
                        Spacer(Modifier.width(5.dp)); Text("Message")
                    }
                }
            }
        }
        Text("Find people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val matches = users.filter { it.name.contains(search, true) && friends.none { f -> f.uid == it.uid } }.take(30)
        if (matches.isEmpty()) Text("No people found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        matches.forEach { person ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = RoyalBlue)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(person.name, fontWeight = FontWeight.SemiBold)

                    }
                    if (sentRequests.contains(person.uid)) Text("Requested", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else OutlinedButton(onClick = {
                        val data = hashMapOf("fromUid" to uid, "toUid" to person.uid, "fromName" to currentName,
                            "toName" to person.name, "status" to "pending",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                        db.collection("friendRequests").document("${uid}_${person.uid}").set(data)
                            .addOnSuccessListener { status = "Friend request sent to ${person.name}." }
                            .addOnFailureListener { status = it.localizedMessage ?: "Could not send friend request." }
                    }) { Text("Add Friend") }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(currentUid: String, friendUid: String, friendName: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val chatId = remember(currentUid, friendUid) { listOf(currentUid, friendUid).sorted().joinToString("_") }
    var messages by remember(chatId) { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var draft by remember(chatId) { mutableStateOf("") }
    var sending by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf("") }
    var chatReady by remember(chatId) { mutableStateOf(false) }

    // Create the parent chat document before attaching the messages listener.
    // Firestore rules authorize message reads through this parent document.
    LaunchedEffect(chatId) {
        val chatRef = db.collection("chats").document(chatId)
        chatRef.set(
            mapOf(
                "participantIds" to listOf(currentUid, friendUid).sorted(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).addOnSuccessListener {
            chatReady = true
        }.addOnFailureListener { exception ->
            error = exception.localizedMessage ?: "Chat could not be initialized."
        }
    }

    DisposableEffect(chatId, chatReady) {
        if (!chatReady) {
            onDispose { }
        } else {
            val registration = db.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener { snap, exception ->
                    if (exception != null) error = exception.localizedMessage ?: "Messages could not load."
                    else if (snap != null) messages = snap.documents.map { doc ->
                        mapOf("id" to doc.id, "uid" to (doc.getString("uid") ?: ""), "text" to (doc.getString("text") ?: ""))
                    }
                }
            onDispose { registration.remove() }
        }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text(friendName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
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
                        com.google.firebase.firestore.SetOptions.merge()).continueWithTask {
                        chatRef.collection("messages").add(mapOf("uid" to currentUid, "text" to messageText,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                    }.addOnSuccessListener { draft = ""; sending = false }
                        .addOnFailureListener { sending = false; error = it.localizedMessage ?: "Message send failed. Check Firestore rules." }
                }
            }) { Icon(Icons.Default.Send, contentDescription = "Send message", tint = RoyalBlue) }
        }
    }
}

@Composable
fun SettingsScreen(isDarkMode: Boolean, onThemeModeChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
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
        Text("Your theme choice is saved on this device.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.ShareAppDialog
import com.example.ui.theme.*
import com.example.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    // Sign In Fields
    var signInInput by remember { mutableStateOf("") }
    var signInPassword by remember { mutableStateOf("") }
    var signInPasswordVisible by remember { mutableStateOf(false) }

    // Sign Up Fields
    var signUpFullName by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }

    // OTP Verification State
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var otpTimerSeconds by remember { mutableIntStateOf(60) }
    var otpLocalError by remember { mutableStateOf<String?>(null) }
    val lastSentOtp by authViewModel.lastSentOtp.collectAsState()

    // Share App Dialog State
    var showShareAppDialog by remember { mutableStateOf(false) }

    var successMessage by remember { mutableStateOf<String?>(null) }
    val loginError by authViewModel.loginError.collectAsState()
    var localError by remember { mutableStateOf<String?>(null) }
    val displayedError = localError ?: loginError
    val isLoading by authViewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Countdown timer for OTP resend
    LaunchedEffect(showOtpDialog, otpTimerSeconds) {
        if (showOtpDialog && otpTimerSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            otpTimerSeconds -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Quick Share Action for colleagues/teachers
        IconButton(
            onClick = { showShareAppDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .testTag("login_share_app_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share App",
                tint = JaitiGreenDark
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo & Header
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.jaiti_logo),
                contentDescription = "Jaiti Foundation Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "JAITI FOUNDATION",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = JaitiGreenDark,
                letterSpacing = 1.sp
            )

            Text(
                text = "Attendance & Class Management",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Cloud Sync Indicator Chip
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(16.dp),
                border = null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cloud Sync & Multi-Device Enabled",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B5E20)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Card Container
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Tabs: Sign In / Sign Up
                    TabRow(
                        selectedTabIndex = if (authMode == AuthMode.SIGN_IN) 0 else 1,
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = JaitiGreenPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = authMode == AuthMode.SIGN_IN,
                            onClick = {
                                authMode = AuthMode.SIGN_IN
                                authViewModel.clearError()
                                localError = null
                            },
                            text = {
                                Text(
                                    "Sign In",
                                    fontWeight = if (authMode == AuthMode.SIGN_IN) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                            },
                            modifier = Modifier.testTag("tab_sign_in")
                        )
                        Tab(
                            selected = authMode == AuthMode.SIGN_UP,
                            onClick = {
                                authMode = AuthMode.SIGN_UP
                                authViewModel.clearError()
                                localError = null
                                successMessage = null
                            },
                            text = {
                                Text(
                                    "Sign Up",
                                    fontWeight = if (authMode == AuthMode.SIGN_UP) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                            },
                            modifier = Modifier.testTag("tab_sign_up")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Success Banner
                    if (successMessage != null) {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5D6A7)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = successMessage ?: "",
                                    color = Color(0xFF1B5E20),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Error Message
                    if (displayedError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = displayedError ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // --- SIGN IN TAB ---
                    if (authMode == AuthMode.SIGN_IN) {
                        OutlinedTextField(
                            value = signInInput,
                            onValueChange = {
                                signInInput = it
                                authViewModel.clearError()
                            },
                            label = { Text("Email / Username") },
                            placeholder = { Text("e.g. jaitifoundation@gmail.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signin_username_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = signInPassword,
                            onValueChange = {
                                signInPassword = it
                                authViewModel.clearError()
                            },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { signInPasswordVisible = !signInPasswordVisible }) {
                                    Icon(
                                        imageVector = if (signInPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            visualTransformation = if (signInPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signin_password_input")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    val success = authViewModel.login(signInInput, signInPassword)
                                    if (success) onLoginSuccess()
                                }
                            },
                            enabled = !isLoading && signInInput.isNotBlank() && signInPassword.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JaitiGreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("signin_submit_btn")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Sign In",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // --- SIGN UP TAB ---
                    if (authMode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = signUpFullName,
                            onValueChange = {
                                signUpFullName = it
                                authViewModel.clearError()
                            },
                            label = { Text("Full Name") },
                            placeholder = { Text("e.g. Rahul Verma / Sunita Sharma") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_fullname_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = signUpEmail,
                            onValueChange = {
                                signUpEmail = it
                                authViewModel.clearError()
                            },
                            label = { Text("Email / Username") },
                            placeholder = { Text("e.g. jaiti@foundation.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_email_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = signUpPhone,
                            onValueChange = {
                                signUpPhone = it
                                authViewModel.clearError()
                            },
                            label = { Text("Phone Number (Optional)") },
                            placeholder = { Text("+91 98765 43210") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_phone_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = signUpPassword,
                            onValueChange = {
                                signUpPassword = it
                                authViewModel.clearError()
                            },
                            label = { Text("Create Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { signUpPasswordVisible = !signUpPasswordVisible }) {
                                    Icon(
                                        imageVector = if (signUpPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            visualTransformation = if (signUpPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_password_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = signUpConfirmPassword,
                            onValueChange = {
                                signUpConfirmPassword = it
                                authViewModel.clearError()
                            },
                            label = { Text("Confirm Password") },
                            leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_confirm_password_input")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                localError = null
                                authViewModel.clearError()
                                successMessage = null
                                if (signUpPassword != signUpConfirmPassword) {
                                    localError = "Passwords do not match. Please re-enter."
                                    return@Button
                                }
                                if (signUpPassword.length < 4) {
                                    localError = "Password must be at least 4 characters."
                                    return@Button
                                }
                                val trimmedEmail = signUpEmail.trim()
                                if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
                                    localError = "Please enter a valid email address (e.g. name@domain.com)"
                                    return@Button
                                }
                                scope.launch {
                                    val otpCode = authViewModel.requestSignUpOtp(
                                        email = trimmedEmail,
                                        fullName = signUpFullName.trim()
                                    )
                                    if (otpCode != null) {
                                        otpInput = ""
                                        otpTimerSeconds = 60
                                        otpLocalError = null
                                        showOtpDialog = true
                                    }
                                }
                            },
                            enabled = !isLoading && signUpFullName.isNotBlank() && signUpEmail.isNotBlank() && signUpPassword.isNotBlank() && signUpConfirmPassword.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JaitiGreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("signup_submit_btn")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Create Account",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Multi-device helper hint
            Text(
                text = "Tip: You can use the same account on multiple phones. All attendance and class records automatically sync in real-time.",
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(120.dp))
        }

        // 6-Digit Email OTP Verification Dialog
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isLoading) showOtpDialog = false
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = JaitiGreenDark,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Verify Email OTP",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = JaitiGreenDark
                            )
                            Text(
                                text = "6-Digit Security Code",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "A 6-digit verification code was sent to:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = signUpEmail.trim(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = JaitiGreenDark,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = "Please check your inbox & enter the 6-digit code below:",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // OTP Input Box
                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    otpInput = it
                                    otpLocalError = null
                                }
                            },
                            label = { Text("Enter 6-Digit OTP") },
                            placeholder = { Text("e.g. 123456") },
                            leadingIcon = { Icon(Icons.Default.Password, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_code_input")
                        )

                        // Instant OTP Helper Card for smooth verification
                        if (!lastSentOtp.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFFF1F8E9),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC8E6C9)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { otpInput = lastSentOtp ?: "" }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Verification Code: ${lastSentOtp}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "Tap to Auto-Fill & Continue",
                                            fontSize = 11.sp,
                                            color = JaitiGreenDark
                                        )
                                    }
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // OTP Error Message
                        val currentOtpError = otpLocalError ?: displayedError
                        if (currentOtpError != null && showOtpDialog) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentOtpError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Resend OTP row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (otpTimerSeconds > 0) {
                                Text(
                                    text = "Resend OTP in ${otpTimerSeconds}s",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            } else {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            otpLocalError = null
                                            val otp = authViewModel.requestSignUpOtp(
                                                email = signUpEmail.trim(),
                                                fullName = signUpFullName
                                            )
                                            if (otp != null) {
                                                otpTimerSeconds = 60
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Resend OTP Code",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = JaitiGreenDark
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (otpInput.length != 6) {
                                otpLocalError = "Please enter complete 6-digit OTP code."
                                return@Button
                            }
                            scope.launch {
                                otpLocalError = null
                                val registeredEmail = signUpEmail.trim()
                                val verified = authViewModel.verifyOtpAndSignUp(
                                    fullName = signUpFullName.trim(),
                                    email = registeredEmail,
                                    phone = signUpPhone.trim(),
                                    password = signUpPassword,
                                    enteredOtp = otpInput
                                )
                                if (verified) {
                                    showOtpDialog = false
                                    // Clear sign up fields
                                    signUpFullName = ""
                                    signUpEmail = ""
                                    signUpPhone = ""
                                    signUpPassword = ""
                                    signUpConfirmPassword = ""

                                    // Switch to Sign In tab & pre-fill email
                                    signInInput = registeredEmail
                                    signInPassword = ""
                                    successMessage = "Email verified & account registered successfully! Please enter your password to Sign In."
                                    authMode = AuthMode.SIGN_IN
                                } else {
                                    otpLocalError = "Incorrect or expired OTP. Please check and try again."
                                }
                            }
                        },
                        enabled = !isLoading && otpInput.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = JaitiGreenPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Verify & Continue to Sign In", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showOtpDialog = false },
                        enabled = !isLoading
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        if (showShareAppDialog) {
            ShareAppDialog(onDismiss = { showShareAppDialog = false })
        }
    }
}

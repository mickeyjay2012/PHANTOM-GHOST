package com.phantom.ghost.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.ghost.ui.GlassCard
import com.phantom.ghost.ui.PhantomBrandHeader
import com.phantom.ghost.ui.PhantomButton
import com.phantom.ghost.ui.PhantomColors
import com.phantom.ghost.ui.PhantomOrb
import com.phantom.ghost.ui.PhantomType
import kotlinx.coroutines.launch

private enum class AuthStage { SIGN_IN, SIGN_UP, VERIFY }

@Composable
fun AuthFlowScreen(
    repository: AuthRepository,
    onAuthenticated: (AuthSession) -> Unit
) {
    var stage by remember { mutableStateOf(AuthStage.SIGN_UP) }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        PhantomOrb(size = 112.dp)
        PhantomBrandHeader()
        Text(
            "YOUR INTELLIGENCE. YOUR CONTROL.",
            style = PhantomType.label,
            color = PhantomColors.GhostCyan,
            textAlign = TextAlign.Center
        )
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                when (stage) {
                    AuthStage.SIGN_UP -> "CREATE YOUR ACCOUNT"
                    AuthStage.SIGN_IN -> "WELCOME BACK"
                    AuthStage.VERIFY -> "VERIFY YOUR EMAIL"
                },
                style = PhantomType.label,
                color = PhantomColors.PhantomPurple
            )
            Spacer(Modifier.height(12.dp))
            when (stage) {
                AuthStage.SIGN_UP -> {
                    OutlinedTextField(fullName, { fullName = it; error = null }, Modifier.fillMaxWidth(),
                        label = { Text("Full name") }, singleLine = true)
                    OutlinedTextField(username, { username = it.trimStart(); error = null }, Modifier.fillMaxWidth(),
                        label = { Text("Username") }, singleLine = true)
                }
                AuthStage.VERIFY -> {
                    Text("Enter the six-digit code sent to $email.", color = PhantomColors.Muted, fontSize = 13.sp)
                    OutlinedTextField(
                        code, { value -> code = value.filter(Char::isDigit).take(6); error = null },
                        Modifier.fillMaxWidth(), label = { Text("Six-digit verification code") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                AuthStage.SIGN_IN -> Unit
            }
            if (stage != AuthStage.VERIFY) {
                OutlinedTextField(email, { email = it.trim(); error = null }, Modifier.fillMaxWidth(),
                    label = { Text("Email address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(password, { password = it; error = null }, Modifier.fillMaxWidth(),
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            }
            if (!error.isNullOrBlank()) Text(error!!, color = PhantomColors.Error, fontSize = 12.sp)
            if (!info.isNullOrBlank()) Text(info!!, color = PhantomColors.GhostCyan, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (busy) {
                CircularProgressIndicator(color = PhantomColors.GhostCyan)
            } else {
                PhantomButton(
                    when (stage) {
                        AuthStage.SIGN_UP -> "CREATE ACCOUNT"
                        AuthStage.SIGN_IN -> "SIGN IN"
                        AuthStage.VERIFY -> "VERIFY EMAIL"
                    },
                    Modifier.fillMaxWidth(),
                    enabled = if (stage == AuthStage.VERIFY) code.length == 6
                    else email.isNotBlank() && password.isNotBlank() &&
                        (stage == AuthStage.SIGN_IN || (fullName.isNotBlank() && username.length >= 3))
                ) {
                    busy = true
                    error = null
                    info = null
                    scope.launch {
                        try {
                            val result = when (stage) {
                                AuthStage.SIGN_UP -> repository.signUp(email, password, fullName.trim(), username.trim())
                                AuthStage.SIGN_IN -> repository.signIn(email, password)
                                AuthStage.VERIFY -> repository.verifyEmail(email, code, fullName.trim(), username.trim())
                            }
                            when (result) {
                                is AuthResult.Authenticated -> onAuthenticated(result.session)
                                AuthResult.VerificationRequired -> {
                                    stage = AuthStage.VERIFY
                                    info = "Verification code requested. Check your inbox and spam folder."
                                }
                                is AuthResult.Failure -> error = result.message
                            }
                        } catch (failure: Throwable) {
                            error = failure.message ?: "Authentication failed. Please try again."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
            TextButton(onClick = {
                error = null
                info = null
                stage = when (stage) {
                    AuthStage.SIGN_UP -> AuthStage.SIGN_IN
                    AuthStage.SIGN_IN -> AuthStage.SIGN_UP
                    AuthStage.VERIFY -> AuthStage.SIGN_UP
                }
            }, Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    when (stage) {
                        AuthStage.SIGN_UP -> "Already have an account? Sign in"
                        AuthStage.SIGN_IN -> "New to PHANTOM GHOST? Create account"
                        AuthStage.VERIFY -> "Back to account creation"
                    },
                    color = PhantomColors.GhostCyan
                )
            }
        }
        Text(
            "Your account secures your profile. AI inference and personal memory remain on this device.",
            color = PhantomColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center
        )
    }
}

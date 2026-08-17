package com.daygle.aicamera.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.ui.components.AppLogo

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    var cfSecretVisible by remember { mutableStateOf(false) }
    var customHeaderValueVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember {
        mutableStateOf(
            state.cfAccessClientId.isNotBlank() ||
                state.cfAccessClientSecret.isNotBlank() ||
                state.customHeaderName.isNotBlank() ||
                state.customHeaderValue.isNotBlank()
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(24.dp))
        Text("Daygle AI Camera", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Connect to your Daygle AI Camera Server",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::onBaseUrl,
            label = { Text("Server URL") },
            placeholder = { Text("https://cam.yourdomain.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsername,
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { showAdvanced = !showAdvanced },
        ) {
            Text(
                "Advanced Options (Optional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (showAdvanced) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showAdvanced) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Cloudflare Access",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Only for servers behind a Cloudflare Tunnel protected by Cloudflare Access. " +
                    "Create a service token in Zero Trust and paste it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.cfAccessClientId,
                onValueChange = viewModel::onCfAccessClientId,
                label = { Text("Client ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.cfAccessClientSecret,
                onValueChange = viewModel::onCfAccessClientSecret,
                label = { Text("Client Secret") },
                singleLine = true,
                visualTransformation = if (cfSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = {
                    IconButton(onClick = { cfSecretVisible = !cfSecretVisible }) {
                        Icon(
                            imageVector = if (cfSecretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (cfSecretVisible) "Hide client secret" else "Show client secret",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp), thickness = 0.5.dp)
            Text(
                "Custom Header",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Send a custom HTTP header with every request. " +
                    "Useful for servers behind a reverse proxy that requires an extra token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.customHeaderName,
                onValueChange = viewModel::onCustomHeaderName,
                label = { Text("Header name") },
                placeholder = { Text("X-Api-Key") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.customHeaderValue,
                onValueChange = viewModel::onCustomHeaderValue,
                label = { Text("Header value") },
                singleLine = true,
                visualTransformation = if (customHeaderValueVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { customHeaderValueVisible = !customHeaderValueVisible }) {
                        Icon(
                            imageVector = if (customHeaderValueVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (customHeaderValueVisible) "Hide header value" else "Show header value",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        }

        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.connect(onConnected) },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Connect")
            }
        }
    }
}

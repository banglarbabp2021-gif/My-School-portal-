package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.SchoolEntity
import com.example.ui.components.SchoolSelectorDropdown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLoginScreen(
    availableSchools: List<SchoolEntity> = emptyList(),
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit,
    onLoginByClassRoll: suspend (school: String, className: String, section: String, roll: String, pass: String) -> Boolean,
    onLoginByBarcodeId: suspend (barcodeId: String, pass: String) -> Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    var loginMethodTab by remember { mutableStateOf(0) } // 0: Class & Roll, 1: Barcode ID

    // Form starts completely blank - no pre-filled values
    var schoolInput by remember { mutableStateOf("") }
    var classInput by remember { mutableStateOf("") }
    var sectionInput by remember { mutableStateOf("") }
    var rollNumberInput by remember { mutableStateOf("") }

    // Fields for Barcode ID mode
    var barcodeIdInput by remember { mutableStateOf("") }

    // Password field starts blank
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun performStudentLogin() {
        if (loginMethodTab == 0) {
            if (schoolInput.isBlank() || classInput.isBlank() || sectionInput.isBlank() || rollNumberInput.isBlank() || passwordInput.isBlank()) {
                errorMessage = "অনুগ্রহ করে সকল তথ্য সঠিকভাবে লিখুন"
                return
            }
        } else {
            if (barcodeIdInput.isBlank() || passwordInput.isBlank()) {
                errorMessage = "অনুগ্রহ করে বারকোড আইডি এবং পাসওয়ার্ড লিখুন"
                return
            }
        }

        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            val success = if (loginMethodTab == 0) {
                onLoginByClassRoll(
                    schoolInput.trim(),
                    classInput.trim(),
                    sectionInput.trim(),
                    rollNumberInput.trim(),
                    passwordInput
                )
            } else {
                onLoginByBarcodeId(barcodeIdInput.trim(), passwordInput)
            }
            isLoading = false
            if (success) {
                onLoginSuccess()
            } else {
                errorMessage = "ভুল তথ্য বা পাসওয়ার্ড। রেকর্ড মেলেনি।"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Login", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("btn_back_student_login")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Student Portal Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View your attendance, digital ID card, exam report card, and teacher announcements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Method Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = loginMethodTab == 0,
                    onClick = { loginMethodTab = 0; errorMessage = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("tab_login_class_roll")
                ) {
                    Text("Class & Roll No.")
                }
                SegmentedButton(
                    selected = loginMethodTab == 1,
                    onClick = { loginMethodTab = 1; errorMessage = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("tab_login_barcode_id")
                ) {
                    Text("Barcode ID")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (loginMethodTab == 0) {
                // School Selector from Registered Schools
                SchoolSelectorDropdown(
                    availableSchools = availableSchools,
                    selectedSchoolNameOrId = schoolInput,
                    onSchoolSelected = { school ->
                        schoolInput = school.name
                        errorMessage = null
                    },
                    onSchoolCustomTyped = {
                        schoolInput = it
                        errorMessage = null
                    },
                    errorMessage = errorMessage
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Class, Section, Roll in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = classInput,
                        onValueChange = { classInput = it; errorMessage = null },
                        label = { Text("Class") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_student_class")
                            .semantics { set(AutocompleteKey, "off") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = sectionInput,
                        onValueChange = { sectionInput = it; errorMessage = null },
                        label = { Text("Section") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_student_section")
                            .semantics { set(AutocompleteKey, "off") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = rollNumberInput,
                        onValueChange = { rollNumberInput = it; errorMessage = null },
                        label = { Text("Roll No.") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_student_roll")
                            .semantics { set(AutocompleteKey, "off") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )
                }
            } else {
                // Barcode ID field
                OutlinedTextField(
                    value = barcodeIdInput,
                    onValueChange = { barcodeIdInput = it; errorMessage = null },
                    label = { Text("Student Barcode ID") },
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_student_barcode_id")
                        .semantics { set(AutocompleteKey, "off") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Password field with autocomplete new-password
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it; errorMessage = null },
                label = { Text("Student Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { performStudentLogin() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_student_password")
                    .semantics { set(AutocompleteKey, "new-password") },
                singleLine = true
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
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
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { performStudentLogin() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_submit_student_login"),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Login to Student Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

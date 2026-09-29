package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SchoolEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolSelectorDropdown(
    availableSchools: List<SchoolEntity>,
    selectedSchoolNameOrId: String,
    onSchoolSelected: (SchoolEntity) -> Unit,
    onSchoolCustomTyped: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    label: String = "Select Registered School / স্কুল নির্বাচন করুন",
    placeholder: String = "Choose your school from registered list",
    errorMessage: String? = null
) {
    val effectiveSchools = availableSchools

    // If the currently selected school was deleted by admin, automatically clear the input
    LaunchedEffect(availableSchools) {
        if (selectedSchoolNameOrId.isNotBlank() &&
            availableSchools.none { it.name.equals(selectedSchoolNameOrId, ignoreCase = true) || it.id.equals(selectedSchoolNameOrId, ignoreCase = true) }
        ) {
            onSchoolCustomTyped("")
        }
    }

    var expanded by remember { mutableStateOf(false) }

    // Filter schools based on current query if user typed something
    val filteredSchools = remember(selectedSchoolNameOrId, effectiveSchools) {
        val query = selectedSchoolNameOrId.trim()
        if (query.isEmpty()) {
            effectiveSchools
        } else {
            val matched = effectiveSchools.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.id.contains(query, ignoreCase = true) ||
                        it.address.contains(query, ignoreCase = true)
            }
            matched
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedSchoolNameOrId,
                onValueChange = {
                    onSchoolCustomTyped(it)
                    expanded = true
                },
                readOnly = false,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Apartment,
                        contentDescription = "School",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedSchoolNameOrId.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onSchoolCustomTyped("")
                                    expanded = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                isError = errorMessage != null,
                singleLine = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .testTag("dropdown_school_selector")
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .testTag("menu_school_selector")
            ) {
                if (filteredSchools.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "কোনো নিবন্ধিত স্কুল নেই / No registered schools found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { expanded = false },
                        enabled = false
                    )
                } else {
                    filteredSchools.forEach { school ->
                        val isSelected = school.name.equals(selectedSchoolNameOrId, ignoreCase = true) ||
                                school.id.equals(selectedSchoolNameOrId, ignoreCase = true)

                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = school.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "ID: ${school.id} • ${school.address}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            onClick = {
                                onSchoolSelected(school)
                                expanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("menu_item_school_${school.id}")
                        )
                    }
                }
            }
        }

        // Quick selection chips for registered schools
        if (effectiveSchools.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registered:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 6.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(effectiveSchools) { sch ->
                        val isSelected = sch.name.equals(selectedSchoolNameOrId, ignoreCase = true) ||
                                sch.id.equals(selectedSchoolNameOrId, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSchoolSelected(sch) },
                            label = { Text(sch.name, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("chip_school_${sch.id}")
                        )
                    }
                }
            }
        }
    }
}

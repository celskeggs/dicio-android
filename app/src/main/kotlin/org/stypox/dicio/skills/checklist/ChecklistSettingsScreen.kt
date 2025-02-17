package org.stypox.dicio.skills.checklist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.job
import org.stypox.dicio.R
import org.stypox.dicio.settings.ui.StringSetting
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ChecklistSettingsScreen(
    navigationIcon: @Composable () -> Unit,
    viewModel: ChecklistSettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            (TopAppBar(
        title = { Text("Checklists") },
        navigationIcon = navigationIcon
    ))
        }
    ) {
        ChecklistSettingsScreen(viewModel = viewModel, modifier = Modifier.padding(it))
    }
}

@Composable
fun ChecklistSettingsScreen(
    viewModel: ChecklistSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val checklists by viewModel.checklists.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { viewModel.exportChecklists(it) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.importChecklists(it) }

    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
        modifier = modifier,
    ) {
        checklists.checklistsList.forEachIndexed { index, checklist ->
            item {
                var expanded by rememberSaveable { mutableStateOf(false) }
                ChecklistSettingsItem(checklist, { viewModel.replaceChecklist(index, it) }, expanded, { expanded = !expanded })
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateContentSize()
            ) {
                TextButton(
                    onClick = {
                        viewModel.addChecklist(Checklist.getDefaultInstance())
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Create new checklist")
                }
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateContentSize()
            ) {
                TextButton(
                    onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Import checklists")
                }
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateContentSize()
            ) {
                TextButton(
                    onClick = {
                        exportLauncher.launch("checklists.pb.json")
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Export checklists")
                }
            }
        }
    }
}

@Composable
fun ChecklistSettingsItem(
    checklist: Checklist,
    updateChecklist: (Checklist?) -> Unit,
    expanded: Boolean,
    toggleExpanded: () -> Unit,
) {
    var deleteDialogOpen by rememberSaveable { mutableStateOf(false) }
    var dialogOpenIndex: Int? by rememberSaveable { mutableStateOf(null) }
    var attemptingItemDelete by rememberSaveable { mutableStateOf(false) }
    // FIXME: Include locale
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize()
    ) {
        ChecklistSettingsItemHeader(
            expanded = expanded,
            toggleExpanded = toggleExpanded,
            checklist = checklist,
        )

        if (expanded) {
            StringSetting(
                title = "Checklist Name",
                descriptionWhenEmpty = "Set the name to be used for the checklist",
            ).Render(
                value = checklist.checklistName,
                onValueChange = { newName ->
                    updateChecklist(checklist.copy {
                        checklistName = newName
                    })
                },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (checklist.executionState) {
                        ChecklistState.NOT_STARTED -> "Not started."
                        ChecklistState.IN_PROGRESS -> "Started at ${
                            formatter.format(
                                ChecklistSkill.timestampToLocal(
                                    checklist.executionStartedAt
                                )
                            )
                        }\nIn progress."

                        ChecklistState.COMPLETE -> "Started at ${
                            formatter.format(
                                ChecklistSkill.timestampToLocal(
                                    checklist.executionStartedAt
                                )
                            )
                        }\nCompleted at ${formatter.format(ChecklistSkill.timestampToLocal(checklist.executionEndedAt))}"

                        else -> "Unknown State"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(1.0f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = LocalContentColor.current,
                )
            }

            checklist.checklistItemList.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            dialogOpenIndex = index
                            attemptingItemDelete = false
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = when (item.executionState) {
                            ItemState.NOT_ASKED -> Icons.Default.CheckBoxOutlineBlank
                            ItemState.ASKED -> Icons.Default.RecordVoiceOver
                            ItemState.COMPLETED -> Icons.Default.CheckBox
                            ItemState.SKIPPED -> Icons.Default.Pause
                            else -> Icons.Default.QuestionMark
                        },
                        contentDescription = when (item.executionState) {
                            ItemState.NOT_ASKED -> "Not Asked"
                            ItemState.ASKED -> "Asked"
                            ItemState.COMPLETED -> "Completed"
                            ItemState.SKIPPED -> "Skipped"
                            else -> "Unknown"
                        },
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    Text(
                        text = if (item.itemName.isBlank()) { "Set the description for item ${index + 1}" } else { "${index + 1}. ${item.itemName}" } +
                                "\nState last changed at ${formatter.format(ChecklistSkill.timestampToLocal(item.executionLastChanged))}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (dialogOpenIndex != null && dialogOpenIndex!! < checklist.checklistItemCount) {
                var value by rememberSaveable { mutableStateOf(checklist.checklistItemList[dialogOpenIndex!!].itemName) }

                if (attemptingItemDelete) {
                    AlertDialog(
                        icon = {
                            Icon(Icons.Default.Warning, contentDescription = "Warning")
                        },
                        title = {
                            Text(text = "Item ${dialogOpenIndex!! + 1}. ${value.ifBlank { "No Description" }}")
                        },
                        text = {
                            Text(text = "Are you sure you want to delete this checklist item?")
                        },
                        onDismissRequest = {
                            attemptingItemDelete = false
                            dialogOpenIndex = null
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    updateChecklist(
                                        checklist.toBuilder().clearChecklistItem()
                                        .addAllChecklistItem(checklist.checklistItemList.filterIndexed { index, _ -> index != dialogOpenIndex!! }).build()
                                    )
                                    attemptingItemDelete = false
                                    dialogOpenIndex = null
                                }
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    attemptingItemDelete = false
                                    dialogOpenIndex = null
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                } else {
                    Dialog(onDismissRequest = { dialogOpenIndex = null }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (dialogOpenIndex!! < checklist.checklistItemCount - 1) {
                                            updateChecklist(checklist.copy {
                                                val current = checklistItem[dialogOpenIndex!!]
                                                checklistItem[dialogOpenIndex!!] =
                                                    checklistItem[dialogOpenIndex!! + 1]
                                                checklistItem[dialogOpenIndex!! + 1] = current
                                            })
                                            dialogOpenIndex = dialogOpenIndex!! + 1
                                        }
                                    },
                                    enabled = (dialogOpenIndex!! < checklist.checklistItemCount - 1)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down",
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Text(
                                    text = "Item ${dialogOpenIndex!! + 1}",
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1.0f)
                                        .padding(
                                            start = 16.dp,
                                            top = 16.dp,
                                            end = 16.dp,
                                            bottom = 8.dp
                                        ),
                                )
                                IconButton(onClick = {
                                    if (dialogOpenIndex!! > 0) {
                                        updateChecklist(checklist.copy {
                                            val current = checklistItem[dialogOpenIndex!!]
                                            checklistItem[dialogOpenIndex!!] =
                                                checklistItem[dialogOpenIndex!! - 1]
                                            checklistItem[dialogOpenIndex!! - 1] = current
                                        })
                                        dialogOpenIndex = dialogOpenIndex!! - 1
                                    }
                                }, enabled = (dialogOpenIndex!! > 0)) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up",
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }

                            Box(
                                contentAlignment = Alignment.TopCenter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                val focusRequester = remember { FocusRequester() }
                                TextField(
                                    value = value,
                                    onValueChange = { value = it },
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.focusRequester(focusRequester),
                                )
                                LaunchedEffect(null) {
                                    coroutineContext.job.invokeOnCompletion {
                                        focusRequester.requestFocus()
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                TextButton(onClick = { attemptingItemDelete = true }) {
                                    Text("Delete")
                                }
                                Spacer(modifier = Modifier.weight(1.0f))
                                TextButton(onClick = { dialogOpenIndex = null }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                                TextButton(
                                    onClick = {
                                        // only send value changes when the user presses ok
                                        updateChecklist(checklist.copy {
                                            checklistItem[dialogOpenIndex!!] = checklistItem[dialogOpenIndex!!].copy {
                                                itemName = value
                                            }
                                        })
                                        dialogOpenIndex = null
                                    }
                                ) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = {
                    updateChecklist(checklist.copy {
                        checklistItem.add(ChecklistItem.getDefaultInstance())
                        dialogOpenIndex = checklistItem.size - 1
                        attemptingItemDelete = false
                    })
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Add item to checklist")
            }

            TextButton(
                onClick = {
                    deleteDialogOpen = true
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Delete checklist")
            }
        }

        if (deleteDialogOpen) {
            AlertDialog(
                icon = {
                    Icon(Icons.Default.Warning, contentDescription = "Warning")
                },
                title = {
                    Text(text = checklist.checklistName.ifBlank { "Unspecified" } + " Checklist")
                },
                text = {
                    Text(text = "Are you sure you want to delete this checklist?")
                },
                onDismissRequest = {
                    deleteDialogOpen = false
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            updateChecklist(null)
                            deleteDialogOpen = false
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            deleteDialogOpen = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ChecklistSettingsItemHeader(
    expanded: Boolean,
    toggleExpanded: () -> Unit,
    checklist: Checklist,
) {
    val expandedAnimation by animateFloatAsState(
        label = "checklist ${checklist.checklistName} card expanded",
        targetValue = if (expanded) 180f else 0f
    )

    Row(
        modifier = Modifier
            .clickable(onClick = toggleExpanded)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = ChecklistInfo.icon(),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(24.dp),
            tint = LocalContentColor.current,
        )
        Text(
            text = "${checklist.checklistName.ifEmpty() { "Unspecified" }} Checklist (${checklist.checklistItemCount} item(s))",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .weight(1.0f)
                .padding(start = 12.dp),
            color = LocalContentColor.current,
        )
        IconButton(
            onClick = toggleExpanded,
        ) {
            Icon(
                modifier = Modifier.rotate(expandedAnimation),
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(
                    if (expanded) R.string.reduce else R.string.expand
                )
            )
        }
    }
}

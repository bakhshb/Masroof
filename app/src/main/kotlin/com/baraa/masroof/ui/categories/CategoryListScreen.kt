package com.baraa.masroof.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.data.db.Category
import com.baraa.masroof.data.repository.CategoryRepository
import com.baraa.masroof.data.repository.DeleteResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val repo: CategoryRepository = app.categoryRepository
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var transactionCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var expandedParents by remember { mutableStateOf(mutableSetOf<Long>()) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var isAddingParent by remember { mutableStateOf(false) }
    var addingChildOf by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repo.observeAll().collectLatest { list ->
            categories = list
            transactionCounts = app.transactionRepository.countByCategory()
                .mapNotNull { (id, n) -> id?.let { it to n } }
                .toMap()
        }
    }

    val parents = remember(categories) { categories.filter { it.parentId == null } }
    val childrenByParent = remember(categories) {
        categories.groupBy { it.parentId }.mapValues { it.value }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { isAddingParent = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.category_add_parent))
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (categories.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    parents.forEach { parent ->
                        item(key = "parent-${parent.id}") {
                            CategoryRow(
                                category = parent,
                                isExpanded = expandedParents.contains(parent.id),
                                onToggleExpand = {
                                    expandedParents = expandedParents.toMutableSet().apply {
                                        if (contains(parent.id)) remove(parent.id) else add(parent.id)
                                    }
                                },
                                transactionCount = transactionCounts[parent.id] ?: 0,
                                onClick = { editing = parent },
                                onAddChild = { addingChildOf = parent.id },
                                onToggleEnabled = { enabled ->
                                    scope.launch { repo.setEnabled(parent.id, enabled) }
                                },
                            )
                        }
                        if (expandedParents.contains(parent.id)) {
                            val children = childrenByParent[parent.id].orEmpty()
                            children.forEach { child ->
                                item(key = "child-${child.id}") {
                                    CategoryRow(
                                        category = child,
                                        isExpanded = false,
                                        onToggleExpand = {},
                                        transactionCount = transactionCounts[child.id] ?: 0,
                                        onClick = { editing = child },
                                        onAddChild = null,
                                        onToggleEnabled = { enabled ->
                                            scope.launch { repo.setEnabled(child.id, enabled) }
                                        },
                                        isChild = true,
                                    )
                                }
                            }
                        }
                        item(key = "spacer-${parent.id}") { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    item(key = "bottom-spacer") { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (isAddingParent) {
        CategoryEditDialog(
            existing = null,
            parentId = null,
            parentName = null,
            onDismiss = { isAddingParent = false },
            onSave = { nameAr, nameEn, sortOrder ->
                scope.launch {
                    repo.add(nameAr, parentId = null, nameEn = nameEn, sortOrder = sortOrder)
                    isAddingParent = false
                }
            },
        )
    }

    addingChildOf?.let { parentId ->
        val parent = categories.firstOrNull { it.id == parentId }
        CategoryEditDialog(
            existing = null,
            parentId = parentId,
            parentName = parent?.nameAr,
            onDismiss = { addingChildOf = null },
            onSave = { nameAr, nameEn, sortOrder ->
                scope.launch {
                    repo.add(nameAr, parentId = parentId, nameEn = nameEn, sortOrder = sortOrder)
                    addingChildOf = null
                }
            },
        )
    }

    editing?.let { cat ->
        CategoryEditDialog(
            existing = cat,
            parentId = cat.parentId,
            parentName = cat.parentId?.let { pid -> categories.firstOrNull { it.id == pid }?.nameAr },
            onDismiss = { editing = null },
            onSave = { nameAr, nameEn, sortOrder ->
                scope.launch {
                    repo.rename(cat.id, nameAr, nameEn)
                    repo.setSortOrder(cat.id, sortOrder)
                    editing = null
                }
            },
            onDelete = {
                pendingDelete = cat
                deleteError = null
                editing = null
            },
        )
    }

    pendingDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(id = R.string.category_delete_title)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.category_delete_body))
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = repo.delete(cat.id)
                        if (result is DeleteResult.Failure) {
                            deleteError = result.reason
                            pendingDelete = cat
                        } else {
                            deleteError = null
                            pendingDelete = null
                        }
                    }
                }) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    transactionCount: Int,
    onClick: () -> Unit,
    onAddChild: (() -> Unit)?,
    onToggleEnabled: (Boolean) -> Unit,
    isChild: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (category.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isChild) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                } else {
                    Spacer(modifier = androidx.compose.ui.Modifier.padding(start = 16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.nameAr,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (category.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    category.nameEn?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.category_tx_count, transactionCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onAddChild != null) {
                    IconButton(onClick = onAddChild) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.category_add_child))
                    }
                }
                Switch(
                    checked = category.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }
            if (!category.enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = stringResource(id = R.string.category_disabled_badge),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.categories_empty),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

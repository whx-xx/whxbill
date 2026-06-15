package com.whxbill.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whxbill.android.data.Account
import com.whxbill.android.data.Book
import com.whxbill.android.data.Category
import com.whxbill.android.ui.BillUiState
import com.whxbill.android.ui.BillViewModel
import com.whxbill.android.ui.BillViewModelFactory

private val Brand = Color(0xFF26A69A)
private val BrandDark = Color(0xFF408D86)
private val BrandSoft = Color(0xFFE6F7F1)
private val TextMain = Color(0xFF17252E)
private val TextMuted = Color(0xFF728F9E)
private val LayoutBg = Color(0xFFF1F3F5)
private val Expense = Color(0xFFFF474D)
private val Income = Color(0xFF24A17D)

class MainActivity : ComponentActivity() {
    private val viewModel: BillViewModel by viewModels {
        val app = application as WhxBillApplication
        BillViewModelFactory(app.repository, app.sessionStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhxBillTheme {
                WhxBillApp(viewModel)
            }
        }
    }
}

@Composable
private fun WhxBillTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            secondary = BrandDark,
            background = LayoutBg,
            surface = Color.White,
            onSurface = TextMain
        ),
        content = content
    )
}

@Composable
private fun WhxBillApp(viewModel: BillViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = LayoutBg
        ) {
            if (state.session.isLoggedIn) {
                BillEntryScreen(state = state, viewModel = viewModel)
            } else {
                LoginScreen(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun LoginScreen(state: BillUiState, viewModel: BillViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFEFFBF9), Color(0xFFD0EBEA), Color(0xFFEBFBF8))
                )
            )
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WHX Bill",
                color = TextMain,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "轻量记账入口，延续网页端清爽的青绿色工作台风格。",
                color = TextMuted,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(26.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("欢迎使用", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("登录后即可新增一笔账单", color = TextMuted, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { viewModel.updateLogin(username = it) },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.updateLogin(password = it) },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = viewModel::login,
                        enabled = !state.isLoggingIn,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (state.isLoggingIn) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text("登录", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillEntryScreen(state: BillUiState, viewModel: BillViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LayoutBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TopBar(state, viewModel)
        Spacer(Modifier.height(14.dp))
        SummaryCard(state)
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.AddCard)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("新增账单", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("保存后会同步写入服务器账户流水", color = TextMuted, fontSize = 13.sp)
                    }
                }
                TypeSwitch(state.billType, viewModel::selectBillType)
                SelectField(
                    label = "归属账本",
                    icon = Icons.Outlined.Book,
                    value = state.books.firstOrNull { it.id == state.selectedBookId }?.bookName.orEmpty(),
                    emptyText = "暂无账本",
                    options = state.books,
                    optionLabel = { it.bookName },
                    onSelected = viewModel::selectBook
                )
                SelectField(
                    label = "账户",
                    icon = Icons.Outlined.CreditCard,
                    value = state.accounts.firstOrNull { it.id == state.selectedAccountId }?.accountName.orEmpty(),
                    emptyText = "暂无账户",
                    options = state.accounts,
                    optionLabel = { it.accountName },
                    onSelected = { viewModel.updateForm(accountId = it.id) }
                )
                SelectField(
                    label = "分类",
                    icon = Icons.Outlined.Category,
                    value = state.currentCategories.firstOrNull { it.id == state.selectedCategoryId }?.categoryName.orEmpty(),
                    emptyText = "暂无分类",
                    options = state.currentCategories,
                    optionLabel = { it.categoryName },
                    onSelected = { viewModel.updateForm(categoryId = it.id) }
                )
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = { viewModel.updateForm(amount = it) },
                    label = { Text("金额") },
                    leadingIcon = { Text("¥", color = Brand, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.billTime,
                    onValueChange = { viewModel.updateForm(billTime = it) },
                    label = { Text("日期时间") },
                    supportingText = { Text("格式：yyyy-MM-dd HH:mm:ss") },
                    singleLine = true,
                    isError = !state.isBillTimeValid,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.merchantName,
                    onValueChange = { viewModel.updateForm(merchantName = it) },
                    label = { Text("商户") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.remark,
                    onValueChange = { viewModel.updateForm(remark = it) },
                    label = { Text("备注") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = viewModel::saveBill,
                    enabled = state.canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存账单", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: BillUiState, viewModel: BillViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("WHX Bill", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                text = "你好，${state.session.displayName}",
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = viewModel::refresh, enabled = !state.isLoadingReferences) {
            if (state.isLoadingReferences) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = BrandDark)
            }
        }
        IconButton(onClick = viewModel::logout) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "退出", tint = TextMuted)
        }
    }
}

@Composable
private fun SummaryCard(state: BillUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BrandSoft),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(Icons.Outlined.AccountBalanceWallet)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.books.firstOrNull { it.id == state.selectedBookId }?.bookName ?: "未选择账本",
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.accounts.size} 个账户 · ${state.expenseCategories.size + state.incomeCategories.size} 个分类",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun TypeSwitch(value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF6F8FA))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TypeButton(
            label = "支出",
            selected = value == "EXPENSE",
            color = Expense,
            modifier = Modifier.weight(1f),
            onClick = { onChange("EXPENSE") }
        )
        TypeButton(
            label = "收入",
            selected = value == "INCOME",
            color = Income,
            modifier = Modifier.weight(1f),
            onClick = { onChange("INCOME") }
        )
    }
}

@Composable
private fun TypeButton(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val container = if (selected) color else Color.Transparent
    val content = if (selected) Color.White else TextMain
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun <T> SelectField(
    label: String,
    icon: ImageVector,
    value: String,
    emptyText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = options.isNotEmpty() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Icon(icon, contentDescription = null, tint = Brand)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = value.ifBlank { emptyText },
                    color = if (value.isBlank()) TextMuted else TextMain,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(item)) },
                        onClick = {
                            expanded = false
                            onSelected(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Brand)
    }
}

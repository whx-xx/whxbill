package com.whxbill.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whxbill.android.data.Account
import com.whxbill.android.data.ApiClient
import com.whxbill.android.data.ApiException
import com.whxbill.android.data.BillRepository
import com.whxbill.android.data.Book
import com.whxbill.android.data.Category
import com.whxbill.android.data.Session
import com.whxbill.android.data.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BillViewModel(
    private val repository: BillRepository,
    sessionStore: SessionStore
) : ViewModel() {
    private val _state = MutableStateFlow(BillUiState())
    val state: StateFlow<BillUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.session.collect { session ->
                ApiClient.accessToken = session.accessToken
                _state.update { it.copy(session = session) }
                if (session.isLoggedIn && _state.value.books.isEmpty() && !_state.value.isLoadingReferences) {
                    loadReferences()
                }
            }
        }
    }

    fun updateLogin(username: String? = null, password: String? = null) {
        _state.update {
            it.copy(
                username = username ?: it.username,
                password = password ?: it.password
            )
        }
    }

    fun login() {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            showMessage("请输入用户名和密码")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoggingIn = true, message = null) }
            runCatching { repository.login(current.username.trim(), current.password) }
                .onSuccess {
                    _state.update { state -> state.copy(isLoggingIn = false, message = "登录成功") }
                    loadReferences()
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoggingIn = false, message = error.readableMessage()) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _state.update { BillUiState(message = "已退出登录") }
        }
    }

    fun selectBook(book: Book) {
        _state.update {
            it.copy(
                selectedBookId = book.id,
                selectedAccountId = null,
                selectedCategoryId = null,
                accounts = emptyList(),
                expenseCategories = emptyList(),
                incomeCategories = emptyList()
            )
        }
        loadBookDetails(book.id)
    }

    fun selectBillType(type: String) {
        _state.update {
            val categories = if (type == "INCOME") it.incomeCategories else it.expenseCategories
            it.copy(
                billType = type,
                selectedCategoryId = categories.firstOrNull()?.id
            )
        }
    }

    fun updateForm(
        accountId: Long? = null,
        categoryId: Long? = null,
        amount: String? = null,
        billTime: String? = null,
        merchantName: String? = null,
        remark: String? = null
    ) {
        _state.update {
            it.copy(
                selectedAccountId = accountId ?: it.selectedAccountId,
                selectedCategoryId = categoryId ?: it.selectedCategoryId,
                amount = amount ?: it.amount,
                billTime = billTime ?: it.billTime,
                merchantName = merchantName ?: it.merchantName,
                remark = remark ?: it.remark
            )
        }
    }

    fun saveBill() {
        val current = _state.value
        val amount = current.amount.toBigDecimalOrNull()
        val bookId = current.selectedBookId
        val accountId = current.selectedAccountId
        val categoryId = current.selectedCategoryId
        when {
            bookId == null -> showMessage("请选择账本")
            accountId == null -> showMessage("请选择账户")
            categoryId == null -> showMessage("请选择分类")
            amount == null || amount <= BigDecimal.ZERO -> showMessage("金额必须大于 0")
            !current.isBillTimeValid -> showMessage("时间格式应为 yyyy-MM-dd HH:mm:ss")
            else -> viewModelScope.launch {
                _state.update { it.copy(isSaving = true, message = null) }
                runCatching {
                    repository.saveBill(
                        bookId = bookId,
                        accountId = accountId,
                        categoryId = categoryId,
                        billType = current.billType,
                        amount = amount,
                        billDate = current.billTime.take(10),
                        billTime = current.backendBillTime,
                        merchantName = current.merchantName.trim(),
                        remark = current.remark.trim()
                    )
                }.onSuccess {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            amount = "",
                            merchantName = "",
                            remark = "",
                            billTime = nowText(),
                            message = "账单已新增"
                        )
                    }
                }.onFailure { error ->
                    if ((error as? ApiException)?.code == 401) repository.logout()
                    _state.update { it.copy(isSaving = false, message = error.readableMessage()) }
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun refresh() {
        loadReferences(force = true)
    }

    private fun loadReferences(force: Boolean = false) {
        if (!force && _state.value.isLoadingReferences) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingReferences = true, message = null) }
            runCatching { repository.books() }
                .onSuccess { books ->
                    val selected = books.firstOrNull { it.isDefault == 1 } ?: books.firstOrNull()
                    _state.update {
                        it.copy(
                            isLoadingReferences = false,
                            books = books,
                            selectedBookId = selected?.id,
                            message = if (books.isEmpty()) "暂无账本，请先到网页端创建账本" else null
                        )
                    }
                    selected?.id?.let(::loadBookDetails)
                }
                .onFailure { error ->
                    if ((error as? ApiException)?.code == 401) repository.logout()
                    _state.update { it.copy(isLoadingReferences = false, message = error.readableMessage()) }
                }
        }
    }

    private fun loadBookDetails(bookId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingReferences = true, message = null) }
            runCatching {
                Triple(
                    repository.accounts(bookId),
                    repository.categories(bookId, "EXPENSE"),
                    repository.categories(bookId, "INCOME")
                )
            }.onSuccess { (accounts, expenses, incomes) ->
                _state.update {
                    val categories = if (it.billType == "INCOME") incomes else expenses
                    it.copy(
                        isLoadingReferences = false,
                        accounts = accounts,
                        expenseCategories = expenses,
                        incomeCategories = incomes,
                        selectedAccountId = accounts.firstOrNull()?.id,
                        selectedCategoryId = categories.firstOrNull()?.id,
                        message = emptyReferenceMessage(accounts, expenses, incomes)
                    )
                }
            }.onFailure { error ->
                if ((error as? ApiException)?.code == 401) repository.logout()
                _state.update { it.copy(isLoadingReferences = false, message = error.readableMessage()) }
            }
        }
    }

    private fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private fun Throwable.readableMessage(): String = message?.takeIf { it.isNotBlank() } ?: "请求失败"

    private fun emptyReferenceMessage(accounts: List<Account>, expenses: List<Category>, incomes: List<Category>): String? {
        return when {
            accounts.isEmpty() -> "当前账本暂无账户，请先到网页端创建账户"
            expenses.isEmpty() -> "当前账本暂无支出分类，请先到网页端创建分类"
            incomes.isEmpty() -> "当前账本暂无收入分类，请先到网页端创建分类"
            else -> null
        }
    }

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        fun nowText(): String = LocalDateTime.now().format(formatter)
    }
}

data class BillUiState(
    val session: Session = Session(),
    val username: String = "demo",
    val password: String = "Demo@123456",
    val isLoggingIn: Boolean = false,
    val isLoadingReferences: Boolean = false,
    val isSaving: Boolean = false,
    val books: List<Book> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val selectedBookId: Long? = null,
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val billType: String = "EXPENSE",
    val amount: String = "",
    val billTime: String = BillViewModel.nowText(),
    val merchantName: String = "",
    val remark: String = "",
    val message: String? = null
) {
    val currentCategories: List<Category>
        get() = if (billType == "INCOME") incomeCategories else expenseCategories

    val isBillTimeValid: Boolean
        get() = runCatching {
            LocalDateTime.parse(billTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }.isSuccess

    val backendBillTime: String
        get() = LocalDateTime
            .parse(billTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    val canSave: Boolean
        get() = !isSaving && selectedBookId != null && selectedAccountId != null && selectedCategoryId != null
}

class BillViewModelFactory(
    private val repository: BillRepository,
    private val sessionStore: SessionStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BillViewModel(repository, sessionStore) as T
    }
}

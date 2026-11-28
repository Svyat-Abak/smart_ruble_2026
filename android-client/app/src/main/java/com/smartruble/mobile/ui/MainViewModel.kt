package com.smartruble.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartruble.mobile.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
 val user: User? = null,
 val items: List<Counterparty> = emptyList(),
 val detail: PartyDetail? = null,
 val search: String = "",
 val page: Int = 0,
 val totalPages: Int = 0,
 val loading: Boolean = false,
 val error: String? = null
)

class MainViewModel : ViewModel() {
 private val api = SmartRubleApi()
 private val mutable = MutableStateFlow(UiState())
 val state = mutable.asStateFlow()

 private fun operation(block: suspend () -> Unit) {
  if(mutable.value.loading) return
  mutable.update { it.copy(loading = true, error = null) }
  viewModelScope.launch {
   try { block() }
   catch(e: CancellationException) { throw e }
   catch(e: SessionExpired) { mutable.value = UiState(error = e.message) }
   catch(e: Exception) { mutable.update { it.copy(error = e.message ?: "Ошибка соединения") } }
   finally { mutable.update { it.copy(loading = false) } }
  }
 }
 fun login(phone: String, password: String) = operation {
  val user = api.login(phone, password)
  mutable.update { it.copy(user = user) }
  load("", 0)
 }
 fun search(query: String) = operation { load(query, 0) }
 fun page(number: Int) = operation { load(mutable.value.search, number) }
 private suspend fun load(query: String, page: Int) {
  val result = api.parties(query, page)
  mutable.update { it.copy(items = result.content, search = query, page = result.page.number, totalPages = result.page.totalPages) }
 }
 fun open(id: String) = operation {
  val detail = api.detail(id)
  mutable.update { it.copy(detail = detail) }
 }
 fun back() { mutable.update { it.copy(detail = null) } }
 fun logout() = operation {
  try { api.logout() } finally { mutable.value = UiState() }
 }
}

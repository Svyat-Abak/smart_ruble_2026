package com.smartruble.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartruble.mobile.data.statusLabel
import com.smartruble.mobile.ui.MainViewModel

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2563EB), background = Color(0xFFF5F7FA))) {
    SmartRuble()
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartRuble(vm: MainViewModel = viewModel()) {
 val state by vm.state.collectAsStateWithLifecycle()
 var phone by remember { mutableStateOf("") }
 var password by remember { mutableStateOf("") }
 var search by remember { mutableStateOf("") }
 LaunchedEffect(state.user) { if(state.user != null) password = "" }
 BackHandler(state.detail != null) { vm.back() }
 Scaffold(topBar = {
  TopAppBar(title = { Text("SmartRuble") }, actions = {
   if(state.user != null) TextButton(enabled = !state.loading, onClick = { vm.logout() }) { Text("Выйти") }
  })
 }) { padding ->
  Column(Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize()) {
   if(state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
   state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
   if(state.user == null) {
    Spacer(Modifier.height(48.dp))
    Text("Вход в кабинет", style = MaterialTheme.typography.headlineMedium)
    Text("Контроль расходов вашей компании", modifier = Modifier.padding(vertical = 12.dp))
    OutlinedTextField(phone, {phone=it}, label={Text("Номер телефона")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone), modifier=Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(password, {password=it}, label={Text("Пароль")}, singleLine=true, visualTransformation=PasswordVisualTransformation(), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password), modifier=Modifier.fillMaxWidth())
    Button(onClick={vm.login(phone,password)}, enabled=!state.loading && phone.isNotBlank() && password.isNotBlank(), modifier=Modifier.fillMaxWidth().padding(top=20.dp)) { Text("Войти") }
   } else if(state.detail != null) {
    val detail = state.detail!!
    TextButton(onClick={vm.back()}) { Text("К списку") }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
     item {
      Text(detail.counterparty.name, style=MaterialTheme.typography.headlineSmall)
      Text(statusLabel(detail.counterparty.verificationStatus), color=MaterialTheme.colorScheme.primary, modifier=Modifier.padding(vertical=12.dp))
      Text("ИНН  ${detail.counterparty.inn}")
      detail.counterparty.kpp?.let { Text("КПП  $it") }
      detail.counterparty.legalAddress?.let { Text(it, modifier=Modifier.padding(vertical=12.dp)) }
      detail.counterparty.contactEmail?.let { Text(it) }
      detail.counterparty.contactPhone?.let { Text(it) }
      Text("Банковские счета", style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(top=24.dp))
      if(detail.bankAccounts.isEmpty()) Text("Счета ещё не добавлены")
     }
     items(detail.bankAccounts, key={it.id}) { bank ->
      Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
       Text(bank.bankName)
       Text(bank.accountNumber)
       Text("БИК  ${bank.bik}")
       Text(if(!bank.active) "Отключён" else if(bank.primary) "Основной" else "Действующий")
      } }
     }
    }
   } else {
    Text("Контрагенты", style=MaterialTheme.typography.headlineMedium, modifier=Modifier.padding(vertical=20.dp))
    OutlinedTextField(search, {search=it}, label={Text("Название или ИНН")}, singleLine=true, modifier=Modifier.fillMaxWidth())
    Row {
     TextButton(onClick={vm.search(search)}, enabled=!state.loading) { Text("Найти") }
     TextButton(onClick={search="";vm.search("")}, enabled=!state.loading) { Text("Сбросить") }
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(10.dp)) {
     if(state.items.isEmpty() && !state.loading) item { Text("Контрагенты не найдены") }
     items(state.items, key={it.id}) { party ->
      Card(onClick={vm.open(party.id)}, enabled=!state.loading, modifier=Modifier.fillMaxWidth()) {
       Column(Modifier.padding(18.dp)) {
        Text(party.shortName ?: party.name, style=MaterialTheme.typography.titleMedium)
        Text("ИНН  ${party.inn}", modifier=Modifier.padding(vertical=6.dp))
        Text(statusLabel(party.verificationStatus), color=MaterialTheme.colorScheme.primary)
       }
      }
     }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
     TextButton(onClick={vm.page(state.page-1)},enabled=!state.loading && state.page>0) { Text("Назад") }
     TextButton(onClick={vm.page(state.page+1)},enabled=!state.loading && state.page+1<state.totalPages) { Text("Далее") }
    }
   }
  }
 }
}

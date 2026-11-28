package com.smartruble.mobile.data

data class User(val id: String, val phoneNumber: String, val roles: List<String>)
data class Session(val accessToken: String, val refreshToken: String, val user: User)
data class Counterparty(
 val id: String, val name: String, val shortName: String?,
 val inn: String, val kpp: String?, val verificationStatus: String,
 val contactEmail: String?, val contactPhone: String?, val legalAddress: String?
)
data class PageInfo(val totalPages: Int, val totalElements: Long, val number: Int)
data class PartyPage(val content: List<Counterparty>, val page: PageInfo)
data class BankAccount(val id: String, val bankName: String, val accountNumber: String, val bik: String, val primary: Boolean, val active: Boolean)
data class PartyDetail(val counterparty: Counterparty, val bankAccounts: List<BankAccount>)
fun statusLabel(status: String) = when(status) {
 "NEW" -> "Новый"
 "VERIFIED" -> "Проверен"
 "SUSPICIOUS" -> "Требует внимания"
 "BLOCKED" -> "Заблокирован"
 else -> status
}

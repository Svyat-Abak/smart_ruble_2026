package com.smartruble.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smartruble.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SessionExpired : IOException("Сессия истекла. Войдите снова.")

/** All network operations use IO. The mutex serializes refresh-token rotation.
 * Tokens live only in memory in this starter. Restarting the app requires login.
 */
class SmartRubleApi {
 private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
 private val gson = Gson()
 private val mutex = Mutex()
 private var session: Session? = null
 private val json = "application/json".toMediaType()

 suspend fun login(phone: String, password: String): User = withContext(Dispatchers.IO) {
  mutex.withLock {
   val value = call("auth/login", "POST", mapOf("phoneNumber" to phone, "password" to password), null)
   val auth = gson.fromJson(value, Session::class.java)
   session = auth
   auth.user
  }
 }

 suspend fun parties(search: String, page: Int): PartyPage = gson.fromJson(
  authorized("billing/counterparties", mapOf("search" to search, "page" to page.toString(), "size" to "20", "sort" to "createdAt,desc")),
  PartyPage::class.java
 )
 suspend fun detail(id: String): PartyDetail = gson.fromJson(authorized("billing/counterparties/$id"), PartyDetail::class.java)

 suspend fun logout() = withContext(Dispatchers.IO) {
  mutex.withLock {
   val current = session
   session = null
   if(current != null) call("auth/logout", "POST", mapOf("refreshToken" to current.refreshToken), current.accessToken)
  }
 }

 private suspend fun authorized(path: String, query: Map<String,String> = emptyMap()): String = withContext(Dispatchers.IO) {
  mutex.withLock {
   val current = session ?: throw SessionExpired()
   try { call(path, token = current.accessToken, query = query) }
   catch(error: HttpFailure) {
    if(error.status != 401) throw error
    try {
     session = gson.fromJson(call("auth/refresh", "POST", mapOf("refreshToken" to current.refreshToken), null), Session::class.java)
    } catch(refreshError: HttpFailure) {
     if(refreshError.status == 401 || refreshError.status == 403) { session = null; throw SessionExpired() }
     throw refreshError
    }
    call(path, token = session!!.accessToken, query = query)
   }
  }
 }

 private fun call(path: String, method: String = "GET", body: Any? = null, token: String? = null, query: Map<String,String> = emptyMap()): String {
  val url = (BuildConfig.API_URL + path).toHttpUrl().newBuilder()
  query.forEach { (key,value) -> url.addQueryParameter(key,value) }
  val builder = Request.Builder().url(url.build())
  if(token != null) builder.header("Authorization", "Bearer $token")
  if(method != "GET") builder.method(method, gson.toJson(body).toRequestBody(json))
  client.newCall(builder.build()).execute().use { response ->
   val text = response.body?.string().orEmpty()
   if(!response.isSuccessful) {
    val message = runCatching { gson.fromJson(text, JsonObject::class.java).get("message")?.asString }.getOrNull()
    throw HttpFailure(response.code, message ?: "Ошибка сервиса (${response.code})")
   }
   return text
  }
 }
 private class HttpFailure(val status: Int, message: String) : IOException(message)
}

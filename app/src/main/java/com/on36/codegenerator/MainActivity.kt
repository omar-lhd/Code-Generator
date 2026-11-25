package com.on36.codegenerator

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val PROJECT_URL = "https://qoujujwecszmwcabevmv.supabase.co"
private const val PUBLIC_KEY = "sb_publishable_YNGPNPX7wGrSsaIRbweYIw_uxWaoLZy"
private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
private fun expiryAtEndOfLocalDay(dateText:String):String = LocalDate.parse(dateText.trim()).atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toString()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { GeneratorApp() } }
}
private suspend fun request(path: String, token: String? = null, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
    val url = "$PROJECT_URL/$path"
    val builder = Request.Builder().url(url).header("apikey", PUBLIC_KEY)
        .header("Accept", "application/json")
    if (token != null) builder.header("Authorization", "Bearer $token")
    if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType())) else builder.get()
    val response = http.newCall(builder.build()).execute()
    val text = response.body?.string().orEmpty()
    val json = try { JSONObject(text) } catch (_: Exception) { JSONObject().put("message", text) }
    if (!response.isSuccessful) throw IllegalStateException(json.optString("error", json.optString("message", "Request failed (${response.code})")))
    json
}

private suspend fun admin(token: String, payload: JSONObject): JSONObject =
    request("functions/v1/invite-code-admin", token, payload)


@Composable
private fun GeneratorApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("Home") }
    var drawer by remember { mutableStateOf(false) }
    var arabic by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(JSONObject()) }
    var codeList by remember { mutableStateOf(JSONObject()) }
    var adminList by remember { mutableStateOf(JSONObject()) }
    var auditList by remember { mutableStateOf(JSONObject()) }
    var groups by remember { mutableStateOf(JSONArray()) }
    var selectedGroup by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf("Premium") }
    var mode by remember { mutableStateOf("Random") }
    var customCodes by remember { mutableStateOf("") }
    var batchName by remember { mutableStateOf("Premium batch") }
    var quantity by remember { mutableStateOf("10") }
    var codeLength by remember { mutableStateOf("16") }
    var prefix by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("Premium access") }
    var bio by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var forever by remember { mutableStateOf(true) }
    var expiryDate by remember { mutableStateOf("") }
    var newCodes by remember { mutableStateOf(listOf<String>()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }

    val bg = if (light) Color(0xFFF3F6F8) else Color(0xFF0B1018)
    val panel = if (light) Color.White else Color(0xFF151E2B)
    val muted = if (light) Color(0xFF647488) else Color(0xFF93A5B8)
    val accent = Color(0xFF48D9B5)
    val danger = Color(0xFFE47770)
    fun tr(en: String, ar: String) = if (arabic) ar else en
    fun task(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = ""
            try { block() } catch (e: Exception) { message = e.message ?: "Unexpected error" }
            busy = false
        }
    }
    fun refreshOverview() = task {
        overview = admin(token, JSONObject().put("action", "overview"))
        groups = overview.optJSONArray("groups") ?: JSONArray()
        if (selectedGroup.isBlank() && groups.length() > 0) selectedGroup = groups.getJSONObject(0).optString("id")
    }
    fun refreshCodes() = task { codeList = admin(token, JSONObject().put("action", "list_codes").put("page", 1).put("pageSize", 50)) }
    fun refreshAdmins() = task { adminList = admin(token, JSONObject().put("action", "list_admins")) }
    fun refreshAudit() = task { auditList = admin(token, JSONObject().put("action", "audit").put("page", 1).put("pageSize", 50)) }

    val pages = listOf("Home", "Generate", "Codes", "Admins", "Activity", "Settings")
    val pageLabels = if (arabic) listOf("الرئيسية", "إنشاء كود", "الأكواد", "المسؤولون", "النشاط", "الإعدادات") else pages
    val colors = if (light) lightColorScheme(primary = accent, background = bg, surface = panel, onSurface = Color(0xFF172330), onBackground = Color(0xFF172330), onSurfaceVariant = muted)
        else darkColorScheme(primary = accent, background = bg, surface = panel, onSurface = Color(0xFFEAF0F5), onBackground = Color(0xFFEAF0F5), onSurfaceVariant = muted)

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Surface(Modifier.fillMaxSize(), color = bg) {
                if (token.isBlank()) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { arabic = !arabic }) { Text(if (arabic) "EN" else "عربي") }
                            IconButton(onClick = { light = !light }) { Icon(if (light) Icons.Default.DarkMode else Icons.Default.LightMode, null) }
                        }
                        Text("ON36", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                        Text(tr("Secure code control", "إدارة الأكواد الآمنة"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text(tr("Private administrator workspace", "مساحة تحكم خاصة بالمسؤول"), color = muted, fontSize = 12.sp)
                        Spacer(Modifier.height(22.dp))
                        GlassCard {
                            SectionHeading(Icons.Default.Lock, tr("Administrator sign in", "دخول المسؤول"), accent)
                            OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Email address", "البريد الإلكتروني")) }, singleLine = true)
                            OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Password", "كلمة المرور")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                            Button(onClick = {
                                task {
                                    val auth = request("auth/v1/token?grant_type=password", body = JSONObject().put("email", email.trim()).put("password", password))
                                    val newToken = auth.optString("access_token")
                                    if (newToken.isBlank()) error("Login failed")
                                    admin(newToken, JSONObject().put("action", "check_access"))
                                    token = newToken
                                    userEmail = auth.optJSONObject("user")?.optString("email", email) ?: email
                                    overview = admin(token, JSONObject().put("action", "overview"))
                                    groups = overview.optJSONArray("groups") ?: JSONArray()
                                    if (groups.length() > 0) selectedGroup = groups.getJSONObject(0).optString("id")
                                }
                            }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                                Text(if (busy) tr("VERIFYING…", "جارٍ التحقق…") else tr("SIGN IN", "دخول"))
                            }
                        }
                        if (message.isNotBlank()) Text(message, color = danger)
                        Text(tr("Server-side administrator access checks", "التحقق من صلاحيات المسؤول من الخادم"), color = muted, fontSize = 11.sp)
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { drawer = true }) { Icon(Icons.Default.Menu, null, tint = accent) }
                                Column(Modifier.weight(1f)) {
                                    Text("ON36 / CONTROL", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                                    Text(tr("Hi, Omar", "أهلاً، عمر"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { arabic = !arabic }) { Text(if (arabic) "EN" else "ع") }
                                IconButton(onClick = { light = !light }) { Icon(if (light) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = muted) }
                            }
                            HorizontalDivider(color = muted.copy(alpha = .2f))
                            AnimatedContent(targetState = page, transitionSpec = { fadeIn() + scaleIn(initialScale = .99f) togetherWith fadeOut() }, label = "page-switch", modifier = Modifier.weight(1f)) { screen ->
                                when (screen) {
                                    "Home" -> {
                                        val s = overview.optJSONObject("summary") ?: JSONObject()
                                        val events = overview.optJSONArray("recentActivity") ?: overview.optJSONArray("activity") ?: JSONArray()
                                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                            item {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        Text(tr("YOUR WORKSPACE", "مساحة عملك"), color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                                        Text(tr("Command center", "لوحة التحكم"), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                                                    }
                                                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .14f)) {
                                                        Text(tr("● SECURE", "● آمن"), color = accent, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            item {
                                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                                    MetricTile(Icons.Default.Key, tr("Total keys", "إجمالي المفاتيح"), s.optString("total", "0"), Modifier.weight(1f), panel, muted, accent)
                                                    MetricTile(Icons.Default.CheckCircle, tr("Available", "متاح"), s.optString("available", "0"), Modifier.weight(1f), panel, muted, accent)
                                                }
                                            }
                                            item {
                                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                                    MetricTile(Icons.Default.VerifiedUser, tr("Activated", "مُفعّل"), s.optString("redeemed", "0"), Modifier.weight(1f), panel, muted, accent)
                                                    MetricTile(Icons.Default.Block, tr("Revoked", "ملغي"), s.optString("revoked", "0"), Modifier.weight(1f), panel, muted, danger)
                                                }
                                            }
                                            item {
                                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                                    QuickAction(Icons.Default.Add, tr("Create key", "إنشاء مفتاح"), Modifier.weight(1f), panel, accent) { page = "Generate" }
                                                    QuickAction(Icons.Default.VpnKey, tr("View codes", "عرض الأكواد"), Modifier.weight(1f), panel, accent) { page = "Codes"; refreshCodes() }
                                                }
                                            }
                                            item {
                                                GlassCard {
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        SectionHeading(Icons.Default.History, tr("Recent activity", "آخر النشاط"), accent)
                                                        TextButton(onClick = { page = "Activity"; refreshAudit() }) { Text(tr("See all", "عرض الكل")) }
                                                    }
                                                    if (events.length() == 0) Text(tr("No recent activity yet", "لا يوجد نشاط حديث"), color = muted, fontSize = 12.sp)
                                                    else (0 until minOf(3, events.length())).forEach { idx ->
                                                        val e = events.getJSONObject(idx)
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Circle, null, tint = accent, modifier = Modifier.size(7.dp))
                                                            Spacer(Modifier.width(9.dp))
                                                            Column {
                                                                Text(e.optString("action").replace("_", " ").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                                Text(e.optString("created_at").take(16).replace("T", " "), color = muted, fontSize = 10.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            item { TextButton(onClick = { refreshOverview() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Text(tr("Refresh overview", "تحديث الملخص")) } }
                                        }
                                    }
                                    "Generate" -> {
                                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                            item {
                                                Text(tr("CREATE A KEY", "إنشاء مفتاح"), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                                                Text(tr("Choose membership", "اختر العضوية"), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                                                Text(tr("Pick a tier, then configure the key.", "حدد العضوية ثم اضبط المفتاح."), color = muted, fontSize = 11.sp)
                                            }
                                            item {
                                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                                    TierChoice(tr("Regular", "عادية"), Icons.Default.Person, tier == "Regular", Modifier.weight(1f), panel, accent) { tier = "Regular" }
                                                    TierChoice(tr("Premium", "مميزة"), Icons.Default.Star, tier == "Premium", Modifier.weight(1f), panel, accent) { tier = "Premium" }
                                                    TierChoice(tr("Professional", "احترافية"), Icons.Default.WorkspacePremium, tier == "Professional", Modifier.weight(1f), panel, accent) { tier = "Professional" }
                                                }
                                            }
                                            item {
                                                GlassCard {
                                                    SectionHeading(Icons.Default.Tune, tr("Key configuration", "إعداد المفتاح"), accent)
                                                    LabelledField(tr("Batch name", "اسم المجموعة"))
                                                    OutlinedTextField(batchName, { batchName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    LabelledField(tr("Code format", "طريقة إنشاء الكود"))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        FilterChip(selected = mode == "Random", onClick = { mode = "Random" }, label = { Text(tr("Automatic", "تلقائي")) })
                                                        FilterChip(selected = mode == "Custom", onClick = { mode = "Custom" }, label = { Text(tr("Custom", "مخصص")) })
                                                    }
                                                    if (mode == "Random") {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Column(Modifier.weight(1f)) {
                                                                LabelledField(tr("Quantity", "الكمية"))
                                                                OutlinedTextField(quantity, { quantity = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                            }
                                                            Column(Modifier.weight(1f)) {
                                                                LabelledField(tr("Length", "الطول"))
                                                                OutlinedTextField(codeLength, { codeLength = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                            }
                                                        }
                                                        LabelledField(tr("Prefix", "بادئة الكود"))
                                                        OutlinedTextField(prefix, { prefix = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    } else {
                                                        LabelledField(tr("Custom codes · one per line", "الأكواد المخصصة · كود بكل سطر"))
                                                        OutlinedTextField(customCodes, { customCodes = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("ON36-PREM-001\nVIP-OMAR-002") })
                                                    }
                                                }
                                            }
                                            item {
                                                GlassCard {
                                                    SectionHeading(Icons.Default.Badge, tr("Membership profile", "بيانات العضوية"), accent)
                                                    LabelledField(tr("Purpose", "الغرض"))
                                                    OutlinedTextField(purpose, { purpose = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                                    LabelledField(tr("Bio", "النبذة"))
                                                    OutlinedTextField(bio, { bio = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text(tr("Short membership description", "نبذة مختصرة عن العضوية")) })
                                                    LabelledField(tr("Internal note", "ملاحظة داخلية"))
                                                    OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                                    LabelledField(tr("Study group", "مجموعة Study"))
                                                    if (groups.length() == 0) Text(tr("No groups available", "لا توجد مجموعات"), color = muted)
                                                    else {
                                                        var expanded by remember { mutableStateOf(false) }
                                                        val groupName = (0 until groups.length()).map { groups.optJSONObject(it) }.firstOrNull { it?.optString("id") == selectedGroup }?.optString("name") ?: tr("Choose group", "اختر المجموعة")
                                                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(groupName); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ExpandMore, null) }
                                                        DropdownMenu(expanded, { expanded = false }) {
                                                            (0 until groups.length()).forEach { idx ->
                                                                val g = groups.getJSONObject(idx)
                                                                DropdownMenuItem(text = { Text(g.optString("name")) }, onClick = { selectedGroup = g.optString("id"); expanded = false })
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            item {
                                                GlassCard {
                                                    SectionHeading(Icons.Default.Schedule, tr("Validity", "الصلاحية"), accent)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(tr("Never expires", "بدون انتهاء"), fontWeight = FontWeight.SemiBold)
                                                            Text(if (forever) tr("Forever", "دائم") else tr("Set expiry date", "حدد الانتهاء"), color = muted, fontSize = 11.sp)
                                                        }
                                                        Switch(checked = forever, onCheckedChange = { forever = it })
                                                    }
                                                    if (!forever) OutlinedTextField(expiryDate, { expiryDate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("YYYY-MM-DD") }, singleLine = true)
                                                }
                                            }
                                            item {
                                                Button(onClick = {
                                                    runTask {
                                                        if (selectedGroup.isBlank()) error("Select group")
                                                        val expiryAt = if (forever) null else expiryAtEndOfLocalDay(expiryDate)
                                                        if (mode == "Custom") {
                                                            val values = customCodes.lines().map { it.trim() }.filter { it.isNotBlank() }
                                                            if (values.isEmpty() || values.size > 200) error("Enter 1–200 codes")
                                                            val array = JSONArray()
                                                            values.forEach { array.put(it) }
                                                            val payload = JSONObject().put("action", "custom_generate").put("groupId", selectedGroup).put("codes", array).put("batchName", batchName).put("tier", tier).put("purpose", purpose).put("label", bio).put("note", note).put("expiresAt", expiryAt ?: JSONObject.NULL)
                                                            admin(token, payload)
                                                            message = tr("Custom codes created", "تم إنشاء الأكواد المخصصة")
                                                            customCodes = ""
                                                        } else {
                                                            val count = quantity.toIntOrNull() ?: error("Invalid quantity")
                                                            val len = codeLength.toIntOrNull() ?: error("Invalid length")
                                                            val payload = JSONObject().put("action", "generate").put("groupId", selectedGroup).put("count", count).put("codeLength", len).put("prefix", prefix).put("batchName", batchName).put("tier", tier).put("purpose", purpose).put("label", bio).put("note", note).put("expiresAt", expiryAt ?: JSONObject.NULL)
                                                            val result = admin(token, payload)
                                                            newCodes = (0 until (result.optJSONArray("codes")?.length() ?: 0)).map { result.optJSONArray("codes")!!.optString(it) }
                                                            message = tr("Codes created", "تم إنشاء الأكواد")
                                                        }
                                                        codeList = admin(token, JSONObject().put("action", "list_codes").put("page", 1).put("pageSize", 50))
                                                        page = "Codes"
                                                    }
                                                }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !busy && groups.length() > 0) {
                                                    Icon(Icons.Default.VpnKey, null); Spacer(Modifier.width(8.dp)); Text(tr("CREATE KEY", "إنشاء المفتاح"))
                                                }
                                            }
                                        }
                                    }
                                    "Codes" -> {
                                        val array = codeList.optJSONArray("items") ?: JSONArray()
                                        val filtered = (0 until array.length()).map { array.getJSONObject(it) }.filter { row ->
                                            val q = search.trim().lowercase()
                                            val queryMatches = q.isBlank() || row.optString("code_value").lowercase().contains(q) || row.optString("redeemer_email").lowercase().contains(q)
                                            val statusMatches = statusFilter == "All" || (statusFilter == "Active" && row.optBoolean("active_now")) || (statusFilter == "Available" && row.optString("status") == "available") || (statusFilter == "Revoked" && row.optString("status") == "revoked")
                                            queryMatches && statusMatches
                                        }
                                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                            item {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column { Text(tr("YOUR REGISTRY", "سجل الأكواد"), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(tr("Keys & memberships", "المفاتيح والعضويات"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
                                                    IconButton(onClick = { refreshCodes() }) { Icon(Icons.Default.Refresh, null, tint = accent) }
                                                }
                                            }
                                            item { OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(tr("Search code or email", "ابحث بالكود أو البريد")) }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
                                            item {
                                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    listOf("All", "Active", "Available", "Revoked").forEach { value ->
                                                        FilterChip(selected = statusFilter == value, onClick = { statusFilter = value }, label = { Text(when (value) { "All" -> tr("All", "الكل"); "Active" -> tr("Active", "نشط"); "Available" -> tr("Unused", "غير مستخدم"); else -> tr("Revoked", "ملغي") }, fontSize = 9.sp) })
                                                    }
                                                }
                                            }
                                            if (newCodes.isNotEmpty()) item {
                                                GlassCard {
                                                    SectionHeading(Icons.Default.CheckCircle, tr("Created successfully", "تم الإنشاء بنجاح"), accent)
                                                    newCodes.forEach { Text(it, color = accent, fontWeight = FontWeight.Bold) }
                                                    Button(onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("ON36", newCodes.joinToString("\n")))
                                                        message = tr("Copied", "تم النسخ")
                                                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, null); Text(tr("Copy all", "نسخ الكل")) }
                                                }
                                            }
                                            items(filtered) { row ->
                                                var expanded by remember(row.optString("id")) { mutableStateOf(false) }
                                                var history by remember(row.optString("id")) { mutableStateOf<JSONArray?>(null) }
                                                GlassCard {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text(row.optString("code_value").ifBlank { "••••••••" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                            Text(row.optString("tier").ifBlank { "Regular" } + " · " + row.optString("batch_name"), fontSize = 10.sp, color = muted)
                                                        }
                                                        StatusPill(row.optString("status"), row.optBoolean("active_now"), accent, danger)
                                                        IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Schedule, null, tint = muted, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(5.dp))
                                                        val statusText = if (row.optBoolean("active_now")) tr("Active now", "نشط الآن") else if (row.optString("activated_at").isNotBlank()) tr("Active", "نشط") + " " + row.optInt("active_minutes") + "m " + tr("ago", "منذ") else tr("Not activated", "لم يُفعّل")
                                                        Text(statusText, fontSize = 10.sp, color = muted)
                                                        Spacer(Modifier.weight(1f))
                                                        Text(row.optString("redeemer_email").ifBlank { tr("Not redeemed", "لم يسترد") }, fontSize = 9.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    if (row.optString("code_label").isNotBlank()) Text(row.optString("code_label").take(90), fontSize = 11.sp, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                    AnimatedVisibility(expanded) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            HorizontalDivider()
                                                            Text(tr("Redeemed by", "استرده") + ": " + row.optString("redeemer_email").ifBlank { "—" }, fontSize = 11.sp)
                                                            Text(tr("Activated", "التفعيل") + ": " + row.optString("activated_at").ifBlank { "—" }, fontSize = 10.sp, color = muted)
                                                            Text(tr("Expires", "ينتهي") + ": " + row.optString("expires_at").ifBlank { tr("Never", "دائم") }, fontSize = 10.sp, color = muted)
                                                            Text(tr("Bio", "النبذة") + ": " + row.optString("code_label").ifBlank { "—" }, fontSize = 11.sp)
                                                            OutlinedButton(onClick = { runTask { history = admin(token, JSONObject().put("action", "code_history").put("codeId", row.optString("id"))).optJSONArray("items") ?: JSONArray() } }, modifier = Modifier.fillMaxWidth()) { Text(tr("Show history", "عرض السجل")) }
                                                            if (history != null) (0 until history!!.length()).forEach { index ->
                                                                val event = history!!.getJSONObject(index)
                                                                Text(event.optString("action") + " · " + event.optString("created_at"), fontSize = 9.sp, color = muted)
                                                            }
                                                            if (row.optString("status") == "available") OutlinedButton(onClick = { runTask { admin(token, JSONObject().put("action", "revoke_code").put("codeId", row.optString("id"))); codeList = admin(token, JSONObject().put("action", "list_codes").put("page", 1).put("pageSize", 50)) } }, modifier = Modifier.fillMaxWidth()) { Text(tr("Revoke key", "إلغاء المفتاح"), color = danger) }
                                                        }
                                                    }
                                                }
                                            }
                                            if (filtered.isEmpty()) item { EmptyState(Icons.Default.Search, tr("No matching codes", "لا توجد أكواد مطابقة"), muted) }
                                        }
                                    }
                                    "Admins" -> {
                                        val adminItems = adminList.optJSONArray("admins") ?: JSONArray()
                                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                            item { Text(tr("ACCESS CONTROL", "إدارة الوصول"), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(tr("Administrators", "المسؤولون"), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
                                            item {
                                                GlassCard {
                                                    SectionHeading(Icons.Default.PersonAdd, tr("Add administrator", "إضافة مسؤول"), accent)
                                                    OutlinedTextField(adminEmail, { adminEmail = it }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Existing account email", "بريد الحساب الحالي")) })
                                                    Button(onClick = { runTask { admin(token, JSONObject().put("action", "add_admin").put("email", adminEmail.trim())); adminEmail = ""; adminList = admin(token, JSONObject().put("action", "list_admins")) } }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text(tr("Add admin", "إضافة مسؤول")) }
                                                }
                                            }
                                            items((0 until adminItems.length()).map { adminItems.getJSONObject(it) }) { item ->
                                                GlassCard {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.AdminPanelSettings, null, tint = accent)
                                                        Spacer(Modifier.width(8.dp))
                                                        Column(Modifier.weight(1f)) { Text(item.optString("email")); Text(tr("Administrator", "مسؤول"), fontSize = 10.sp, color = muted) }
                                                        IconButton(onClick = { runTask { admin(token, JSONObject().put("action", "revoke_admin").put("userId", item.optString("user_id"))); adminList = admin(token, JSONObject().put("action", "list_admins")) } }) { Icon(Icons.Default.DeleteOutline, null, tint = danger) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "Activity" -> {
                                        val events = auditList.optJSONArray("items") ?: JSONArray()
                                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                            item { Text(tr("AUDIT LOG", "سجل الأحداث"), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(tr("Activity", "النشاط"), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
                                            items((0 until events.length()).map { events.getJSONObject(it) }) { event ->
                                                GlassCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = accent); Spacer(Modifier.width(8.dp)); Column { Text(event.optString("action").replace("_", " ").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(event.optString("created_at"), fontSize = 10.sp, color = muted) } } }
                                            }
                                        }
                                    }
                                    else -> {
                                        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            item { Text(tr("PREFERENCES", "التفضيلات"), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(tr("Settings", "الإعدادات"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
                                            item {
                                                GlassCard {
                                                    SettingRow(Icons.Default.Language, tr("Language", "اللغة"), if (arabic) "العربية" else "English") { arabic = !arabic }
                                                    HorizontalDivider()
                                                    SettingRow(if (light) Icons.Default.LightMode else Icons.Default.DarkMode, tr("Appearance", "المظهر"), if (light) tr("Light", "فاتح") else tr("Dark", "داكن")) { light = !light }
                                                    HorizontalDivider()
                                                    SettingRow(Icons.Default.Logout, tr("Sign out", "تسجيل الخروج"), userEmail) { token = ""; password = "" }
                                                }
                                            }
                                            item { Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Creator: Omar Elhady", color = muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("2026®", color = muted, fontSize = 11.sp) } }
                                        }
                                    }
                                }
                            }
                            if (message.isNotBlank()) Text(message, color = danger, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp))
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                        }
                        AnimatedVisibility(drawer, enter = fadeIn(), exit = fadeOut()) {
                            Row(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f))) {
                                Column(Modifier.fillMaxHeight().fillMaxWidth(.82f).background(panel).padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("ON36", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { drawer = false }) { Icon(Icons.Default.Close, null) }
                                    }
                                    Text(userEmail, color = muted, fontSize = 10.sp)
                                    Spacer(Modifier.height(20.dp))
                                    pages.forEachIndexed { index, name ->
                                        NavigationDrawerItem(
                                            icon = { Icon(when (name) { "Home" -> Icons.Default.Home; "Generate" -> Icons.Default.AddCircle; "Codes" -> Icons.Default.VpnKey; "Admins" -> Icons.Default.People; "Activity" -> Icons.Default.History; else -> Icons.Default.Settings }, null) },
                                            label = { Text(pageLabels[index]) },
                                            selected = page == name,
                                            onClick = { drawer = false; page = name; when (name) { "Home" -> refreshOverview(); "Codes" -> refreshCodes(); "Admins" -> refreshAdmins(); "Activity" -> refreshAudit() } }
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text("Creator: Omar Elhady · 2026®", color = muted, fontSize = 10.sp)
                                }
                                Spacer(Modifier.fillMaxSize().clickable { drawer = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MetricTile(icon: ImageVector, title: String, value: String, modifier: Modifier, panel: Color, muted: Color, accent: Color) {
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = panel)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}
@Composable private fun QuickAction(icon: ImageVector, title: String, modifier: Modifier, panel: Color, accent: Color, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = panel)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent); Spacer(Modifier.width(7.dp)); Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
    }
}
@Composable private fun TierChoice(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, panel: Color, accent: Color, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = if (selected) accent.copy(alpha = .15f) else panel), border = if (selected) BorderStroke(1.5.dp, accent) else null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (selected) accent else Color.Gray, modifier = Modifier.size(21.dp))
            Text(title, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(13.dp))
        }
    }
}
@Composable private fun LabelledField(label: String) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
@Composable private fun SectionHeading(icon: ImageVector, title: String, accent: Color) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = accent); Spacer(Modifier.width(8.dp)); Text(title, fontWeight = FontWeight.Bold) } }
@Composable private fun StatusPill(status: String, active: Boolean, accent: Color, danger: Color) {
    val color = when { active -> accent; status == "revoked" -> danger; status == "redeemed" -> Color(0xFF69A4E4); else -> Color(0xFF94A6B8) }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .15f)) { Text(if (active) "ACTIVE" else status.uppercase(), color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
}
@Composable private fun EmptyState(icon: ImageVector, title: String, muted: Color) { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = muted, modifier = Modifier.size(30.dp)); Text(title, color = muted, textAlign = TextAlign.Center) } }
@Composable private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, null) }
}
@Composable private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

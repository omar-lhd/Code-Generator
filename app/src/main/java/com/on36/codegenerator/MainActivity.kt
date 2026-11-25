package com.on36.codegenerator

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId

private const val PROJECT_URL = "https://qoujujwecszmwcabevmv.supabase.co"
private const val PUBLIC_KEY = "sb_publishable_YNGPNPX7wGrSsaIRbweYIw_uxWaoLZy"
private val ink = Color(0xFF080D18)
private val panel = Color(0xFF111B2B)
private val cyan = Color(0xFF57E7F4)
private val muted = Color(0xFF94A6BE)
private fun expiryAtEndOfLocalDay(dateText: String): String {
    val date = LocalDate.parse(dateText.trim())
    return date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toString()
}
private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GeneratorApp() }
    }
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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("Overview") }
    var overview by remember { mutableStateOf(JSONObject()) }
    var codeList by remember { mutableStateOf(JSONObject()) }
    var adminList by remember { mutableStateOf(JSONObject()) }
    var auditList by remember { mutableStateOf(JSONObject()) }
    var groups by remember { mutableStateOf(JSONArray()) }
    var selectedGroup by remember { mutableStateOf("") }
    var batchName by remember { mutableStateOf("Premium batch") }
    var quantity by remember { mutableStateOf("10") }
    var codeLength by remember { mutableStateOf("16") }
    var prefix by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("Premium access") }
    var customLabel by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var freshCodes by remember { mutableStateOf(listOf<String>()) }
    var generationMode by remember { mutableStateOf("Random") }
    var customCodesText by remember { mutableStateOf("") }
    var forever by remember { mutableStateOf(true) }
    var expiryDateText by remember { mutableStateOf("") }
    val context = LocalContext.current

    fun runTask(task: suspend () -> Unit) {
        scope.launch {
            busy = true; message = ""
            try { task() } catch (e: Exception) { message = e.message ?: "Something went wrong" }
            busy = false
        }
    }
    fun loadOverview() = runTask {
        overview = admin(token, JSONObject().put("action", "overview"))
        groups = overview.optJSONArray("groups") ?: JSONArray()
        if (selectedGroup.isBlank() && groups.length() > 0) selectedGroup = groups.getJSONObject(0).optString("id")
    }
    fun loadCodes() = runTask { codeList = admin(token, JSONObject().put("action","list_codes").put("page",1).put("pageSize",50)) }
    fun loadAdmins() = runTask { adminList = admin(token, JSONObject().put("action","list_admins")) }
    fun loadAudit() = runTask { auditList = admin(token, JSONObject().put("action","audit").put("page",1).put("pageSize",50)) }

    MaterialTheme(colorScheme = darkColorScheme(background = ink, surface = panel, primary = cyan, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = ink) {
            if (token.isBlank()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("ON36", color = cyan, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("CODE CONTROL", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Secure administrator console", color = muted)
                    Spacer(Modifier.height(28.dp))
                    GlassCard {
                        OutlinedTextField(email, { email = it }, label = { Text("Admin email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = {
                            runTask {
                                val result = request("auth/v1/token?grant_type=password", body = JSONObject().put("email",email.trim()).put("password",password))
                                val newToken = result.optString("access_token")
                                if (newToken.isBlank()) throw IllegalStateException("Login did not return a session")
                                admin(newToken, JSONObject().put("action","check_access"))
                                token = newToken
                                userEmail = result.optJSONObject("user")?.optString("email",email) ?: email
                                overview = admin(token, JSONObject().put("action","overview"))
                                groups = overview.optJSONArray("groups") ?: JSONArray()
                                if (groups.length()>0) selectedGroup = groups.getJSONObject(0).optString("id")
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text(if (busy) "VERIFYING…" else "SIGN IN SECURELY") }
                    }
                    if (message.isNotBlank()) Text(message, color = Color(0xFFFF8295), modifier = Modifier.padding(top=12.dp))
                    Text("Access is verified by the Supabase administrator allowlist.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top=22.dp))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal=20.dp, vertical=16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("ON36 / CONTROL", color = cyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Text(userEmail, color = muted, fontSize = 12.sp)
                            }
                            TextButton(onClick = { token = ""; password = ""; message = "" }) { Text("LOG OUT") }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(page.uppercase(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Overview","Generate","Codes","Admins","Activity").forEach { tab ->
                            val active = page == tab
                            TextButton(onClick = {
                                page = tab
                                when(tab) { "Overview" -> loadOverview(); "Codes" -> loadCodes(); "Admins" -> loadAdmins(); "Activity" -> loadAudit() }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = if(active) cyan else muted)) {
                                Text(tab, fontSize = 10.sp, fontWeight = if(active) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = Color(0xFFFF8295), modifier = Modifier.padding(horizontal=20.dp, vertical=8.dp))
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color=cyan)
                    when(page) {
                        "Overview" -> {
                            val summary = overview.optJSONObject("summary") ?: JSONObject()
                            LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    StatCard("TOTAL",summary.optString("total","0"),Modifier.weight(1f))
                                    StatCard("AVAILABLE",summary.optString("available","0"),Modifier.weight(1f))
                                } }
                                item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    StatCard("REDEEMED",summary.optString("redeemed","0"),Modifier.weight(1f))
                                    StatCard("REVOKED",summary.optString("revoked","0"),Modifier.weight(1f))
                                } }
                                item { GlassCard { Text("BATCHES",color=cyan,fontWeight=FontWeight.Bold); Text("${overview.optJSONArray("batches")?.length() ?: 0} total batches",color=muted); Text("Group-backed code issuance",color=muted,fontSize=12.sp) } }
                                item { Button(onClick={loadOverview()},modifier=Modifier.fillMaxWidth(),enabled=!busy){Text("REFRESH DASHBOARD")} }
                            }
                        }
                        "Generate" -> {
                            LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                item { GlassCard {
                                    Text("NEW CODE BATCH",color=cyan,fontWeight=FontWeight.Bold)
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        listOf("Random","Custom").forEach { mode ->
                                            if (generationMode == mode) Button(onClick={generationMode=mode},modifier=Modifier.weight(1f)){Text(mode.uppercase())}
                                            else OutlinedButton(onClick={generationMode=mode},modifier=Modifier.weight(1f)){Text(mode.uppercase())}
                                        }
                                    }
                                    if (generationMode == "Custom") {
                                        OutlinedTextField(customCodesText,{customCodesText=it},label={Text("Custom codes — one per line")},placeholder={Text("ON36-PREMIUM-2026\\nVIP_OMAR_01")},modifier=Modifier.fillMaxWidth(),minLines=4)
                                        Text("4–64 chars per code: A–Z, 0–9, underscore or hyphen. Duplicate codes are rejected.",color=muted,fontSize=12.sp)
                                    }
                                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Never expires",fontWeight=FontWeight.SemiBold)
                                            Text(if(forever) "Forever" else "Set expiry date",color=muted,fontSize=12.sp)
                                        }
                                        Switch(checked=forever,onCheckedChange={forever=it})
                                    }
                                    if (!forever) {
                                        OutlinedTextField(expiryDateText,{expiryDateText=it},label={Text("Expiry date (YYYY-MM-DD)")},placeholder={Text("2027-12-31")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                                    }
                                    if(groups.length()==0) Text("No study groups exist yet. Create a group in the Study admin tools first.",color=muted)
                                    else {
                                        var expanded by remember { mutableStateOf(false) }
                                        val groupName = (0 until groups.length()).map { groups.optJSONObject(it) }.firstOrNull { it?.optString("id")==selectedGroup }?.optString("name") ?: "Choose group"
                                        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(groupName)}
                                        DropdownMenu(expanded,{expanded=false}) { (0 until groups.length()).forEach { i -> val g=groups.getJSONObject(i); DropdownMenuItem(text={Text(g.optString("name"))},onClick={selectedGroup=g.optString("id");expanded=false}) } }
                                    }
                                    OutlinedTextField(batchName,{batchName=it},label={Text("Batch name")},modifier=Modifier.fillMaxWidth())
                                    if (generationMode == "Random") {
                                        OutlinedTextField(quantity,{quantity=it},label={Text("Quantity (1–200)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                                        OutlinedTextField(codeLength,{codeLength=it},label={Text("Code length (12–32)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                                        OutlinedTextField(prefix,{prefix=it},label={Text("Prefix (optional, up to 8)")},modifier=Modifier.fillMaxWidth())
                                    }
                                    OutlinedTextField(purpose,{purpose=it},label={Text("Purpose")},modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(customLabel,{customLabel=it},label={Text("Code label")},modifier=Modifier.fillMaxWidth())
                                    OutlinedTextField(note,{note=it},label={Text("Internal note")},modifier=Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick={runTask {
                                        if(selectedGroup.isBlank()) throw IllegalStateException("Select a group")
                                        val expiresAt = if (forever) null else expiryAtEndOfLocalDay(expiryDateText)
                                        if (generationMode == "Custom") {
                                            val codeValues = customCodesText.lines().map { it.trim() }.filter { it.isNotBlank() }
                                            if (codeValues.isEmpty()) throw IllegalStateException("Enter at least one custom code")
                                            if (codeValues.size > 200) throw IllegalStateException("Maximum 200 custom codes per batch")
                                            val codeArray = JSONArray()
                                            codeValues.forEach { codeArray.put(it) }
                                            val payload = JSONObject().put("action","custom_generate").put("groupId",selectedGroup).put("codes",codeArray).put("batchName",batchName.trim()).put("purpose",purpose.trim()).put("label",customLabel.trim()).put("note",note.trim())
                                            payload.put("expiresAt", expiresAt ?: JSONObject.NULL)
                                            val result = admin(token,payload)
                                            freshCodes = emptyList()
                                            customCodesText = ""
                                            message="Created custom batch ${result.optJSONObject("batch")?.optString("name") ?: batchName}. ${result.optInt("count")} codes saved."
                                        } else {
                                            val n=quantity.toIntOrNull() ?: throw IllegalStateException("Quantity must be a number")
                                            val len=codeLength.toIntOrNull() ?: throw IllegalStateException("Code length must be a number")
                                            val payload=JSONObject().put("action","generate").put("groupId",selectedGroup).put("count",n).put("codeLength",len).put("prefix",prefix.trim()).put("batchName",batchName.trim()).put("purpose",purpose.trim()).put("label",customLabel.trim()).put("note",note.trim())
                                            payload.put("expiresAt",expiresAt ?: JSONObject.NULL)
                                            val result=admin(token,payload)
                                            freshCodes = (0 until (result.optJSONArray("codes")?.length() ?: 0)).map { result.optJSONArray("codes")!!.optString(it) }
                                            message="Created batch ${result.optJSONObject("batch")?.optString("name") ?: batchName}. ${freshCodes.size} codes generated."
                                        }
                                        page="Codes"
                                        codeList=admin(token,JSONObject().put("action","list_codes").put("page",1).put("pageSize",50))
                                    }},modifier=Modifier.fillMaxWidth(),enabled=!busy && groups.length()>0){Text(if(generationMode=="Custom") "SAVE CUSTOM CODES" else "GENERATE CODES")}
                                    Text("Codes are generated securely on the server and stored as hashes.",color=muted,fontSize=12.sp)
                                } }
                            }
                        }
                        "Codes" -> {
                            val items = codeList.optJSONArray("items") ?: JSONArray()
                            LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                item { Button(onClick={loadCodes()},modifier=Modifier.fillMaxWidth()){Text("REFRESH CODE LIST")} }
                                if (freshCodes.isNotEmpty()) item { GlassCard {
                                    Text("JUST GENERATED — SAVE THESE CODES", color=cyan, fontWeight=FontWeight.Bold)
                                    Text("They are shown only now; the backend stores a hash, not the original code.", color=muted, fontSize=12.sp)
                                    freshCodes.forEach { Text(it, fontSize=16.sp, fontWeight=FontWeight.Bold, letterSpacing=1.sp) }
                                    Button(onClick={
                                        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Generated invite codes",freshCodes.joinToString("\\n")))
                                        message="Codes copied to clipboard."
                                    },modifier=Modifier.fillMaxWidth()){Text("COPY ALL CODES")}
                                } }
                                items((0 until items.length()).map { items.getJSONObject(it) }) { row ->
                                    GlassCard {
                                        var historyRows by remember(row.optString("id")) { mutableStateOf<JSONArray?>(null) }
                                        Text(row.optString("code_label").ifBlank { "Invite code" },color=cyan,fontWeight=FontWeight.Bold)
                                        Text("CODE: ${row.optString("code_value","").ifBlank { "Unavailable for legacy hash-only codes" }}",color=cyan,fontSize=15.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                                        Text("ID: ${row.optString("id")}",color=muted,fontSize=11.sp)
                                        Text("Status: ${row.optString("status").uppercase()}",fontWeight=FontWeight.SemiBold)
                                        if (row.optString("redeemer_email").isNotBlank()) Text("Redeemed by: ${row.optString("redeemer_email")}",color=cyan,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                                        if (row.optString("activated_at").isNotBlank()) {
                                            Text("Activated: ${row.optString("activated_at").take(19).replace("T"," ")} UTC",color=muted,fontSize=12.sp)
                                            Text("Active ${row.optInt("active_minutes")} min ago" + if(row.optBoolean("active_now")) " • ACTIVE NOW" else "",color=if(row.optBoolean("active_now")) cyan else muted,fontSize=12.sp)
                                        }
                                        Text("Created: ${row.optString("created_at").take(16).replace("T"," ")}",color=muted,fontSize=12.sp)
                                        Text("Expires: ${row.optString("expires_at","").take(19).replace("T"," ").ifBlank { "Never" }}",color=muted,fontSize=12.sp)
                                        Text("Note: ${row.optString("internal_note").ifBlank { "—" }}",color=muted,fontSize=12.sp)
                                        OutlinedButton(onClick={runTask {
                                            val history=admin(token,JSONObject().put("action","code_history").put("codeId",row.optString("id")))
                                            historyRows=history.optJSONArray("items") ?: JSONArray()
                                        }},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(historyRows==null) "VIEW CODE HISTORY" else "REFRESH CODE HISTORY")}
                                        if(historyRows!=null) {
                                            if(historyRows!!.length()==0) Text("No history events recorded.",color=muted,fontSize=12.sp)
                                            else (0 until historyRows!!.length()).forEach { h ->
                                                val event=historyRows!!.getJSONObject(h)
                                                Text("${event.optString("action").replace("_"," ").uppercase()} • ${event.optString("created_at").take(19).replace("T"," ")} UTC",color=muted,fontSize=12.sp)
                                            }
                                        }
                                        if(row.optString("status")=="available") Button(onClick={runTask {
                                            admin(token,JSONObject().put("action","revoke_code").put("codeId",row.optString("id")))
                                            codeList=admin(token,JSONObject().put("action","list_codes").put("page",1).put("pageSize",50))
                                            message="Code revoked."
                                        }},enabled=!busy){Text("REVOKE")}
                                    }
                                }
                            }
                        }
                        "Admins" -> {
                            val admins=adminList.optJSONArray("admins")?:JSONArray()
                            LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                item { GlassCard {
                                    Text("ADD ADMINISTRATOR",color=cyan,fontWeight=FontWeight.Bold)
                                    OutlinedTextField(adminEmail,{adminEmail=it},label={Text("Administrator email")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                                    Button(onClick={runTask {
                                        admin(token,JSONObject().put("action","add_admin").put("email",adminEmail.trim()))
                                        adminEmail=""
                                        adminList=admin(token,JSONObject().put("action","list_admins"))
                                        message="Administrator added."
                                    }},modifier=Modifier.fillMaxWidth(),enabled=!busy){Text("ADD ADMIN")}
                                    Text("Add an existing Study account by email. Admin privileges are independent of group membership and checked server-side on every operation.",color=muted,fontSize=12.sp)
                                } }
                                items((0 until admins.length()).map{admins.getJSONObject(it)}) { a ->
                                    GlassCard {
                                        Text(a.optString("email","Unknown account"),fontWeight=FontWeight.Bold)
                                        Text(a.optString("user_id"),color=muted,fontSize=11.sp)
                                        Text("Added: ${a.optString("created_at").take(10)}",color=muted,fontSize=12.sp)
                                        Button(onClick={runTask {
                                            admin(token,JSONObject().put("action","revoke_admin").put("userId",a.optString("user_id")))
                                            adminList=admin(token,JSONObject().put("action","list_admins"))
                                            message="Admin access revoked."
                                        }},enabled=!busy){Text("REMOVE ADMIN")}
                                    }
                                }
                            }
                        }
                        else -> {
                            val events=auditList.optJSONArray("items")?:JSONArray()
                            LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                item { Button(onClick={loadAudit()},modifier=Modifier.fillMaxWidth()){Text("REFRESH AUDIT LOG")} }
                                items((0 until events.length()).map{events.getJSONObject(it)}) { e ->
                                    GlassCard {
                                        Text(e.optString("action").replace("_"," ").uppercase(),color=cyan,fontWeight=FontWeight.Bold)
                                        Text(e.optString("created_at").take(19).replace("T"," "),color=muted,fontSize=12.sp)
                                        Text("Actor: ${e.optString("actor_id")}",color=muted,fontSize=11.sp)
                                        Text(e.optJSONObject("metadata")?.toString() ?: "",color=muted,fontSize=12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=panel),elevation=CardDefaults.cardElevation(defaultElevation=3.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable
private fun StatCard(title:String,value:String,modifier:Modifier=Modifier) {
    Card(modifier=modifier,shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=panel)) {
        Column(Modifier.padding(16.dp)) {
            Text(title,color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
            Text(value,color=cyan,fontSize=30.sp,fontWeight=FontWeight.ExtraBold)
        }
    }
}

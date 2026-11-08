package id.co.sinergi.opsi

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val SinergiCyan = Color(0xFF03B7CA)
private val SinergiNavy = Color(0xFF0F172A)
private val PageBg = Color(0xFFF5F8FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("opsi_private", MODE_PRIVATE)
        val api = OpsiApi(prefs)
        setContent { OpsiTheme { OpsiRoot(api) } }
    }
}

@Composable
private fun OpsiTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = SinergiCyan,
        onPrimary = Color.White,
        secondary = SinergiNavy,
        onSecondary = Color.White,
        background = PageBg,
        surface = Color.White,
        error = Color(0xFFB91C1C)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpsiRoot(api: OpsiApi) {
    var loggedIn by remember { mutableStateOf(api.isLoggedIn) }
    var user by remember { mutableStateOf(api.savedUser) }
    var syncData by remember { mutableStateOf<JSONObject?>(null) }
    var visitData by remember { mutableStateOf<JSONObject?>(null) }
    var screen by remember { mutableStateOf("dashboard") }
    var visitPeriod by remember { mutableStateOf(currentPeriod()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var visitDialog by remember { mutableStateOf(false) }
    var reportDialog by remember { mutableStateOf(false) }
    var movementDialog by remember { mutableStateOf("") }
    var mrDialog by remember { mutableStateOf(false) }
    var rkbDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<JSONObject?>(null) }
    var createUserDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun handleApiFailure(error: Exception, fallback: String) {
        message = error.message ?: fallback
        if (!api.isLoggedIn) {
            loggedIn = false
            user = null
            syncData = null
            visitData = null
            screen = "dashboard"
        }
    }

    val reloadAll: (Boolean) -> Unit = { withVisits ->
        scope.launch {
            loading = true
            message = ""
            try {
                syncData = api.syncAll()
                if (withVisits && user?.canVisit() == true) visitData = api.getVisits(visitPeriod)
            } catch (e: Exception) {
                handleApiFailure(e, "Gagal mengambil data OPSi")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn && user != null && syncData == null) reloadAll(false)
    }

    // Filter periode langsung mengambil data yang sesuai dari API, setelah format YYYY-MM valid.
    LaunchedEffect(visitPeriod, loggedIn) {
        if (!loggedIn || user?.canVisit() != true) return@LaunchedEffect
        if (!visitPeriod.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) {
            visitData = null
            return@LaunchedEffect
        }
        try {
            visitData = api.getVisits(visitPeriod)
        } catch (e: Exception) {
            visitData = null
            handleApiFailure(e, "Gagal memuat periode laporan kunjungan")
        }
    }

    if (!loggedIn || user == null) {
        LoginScreen(api = api, onLogin = { username, password, baseUrl ->
            scope.launch {
                loading = true
                message = ""
                try {
                    api.baseUrl = baseUrl
                    val auth = api.login(username, password)
                    user = auth.obj("user")
                    loggedIn = true
                    syncData = null
                    screen = "dashboard"
                } catch (e: Exception) {
                    message = e.message ?: "Login gagal"
                } finally { loading = false }
            }
        }, loading = loading, message = message)
        return
    }

    val currentUser = user ?: JSONObject()
    val areas = syncData?.array("areas")?.toObjects().orEmpty().filter { it.text("status", "Aktif") == "Aktif" }
    val inventory = syncData?.array("inventory")?.toObjects().orEmpty()
    val reports = syncData?.array("reports")?.toObjects().orEmpty()
    val users = syncData?.array("users")?.toObjects().orEmpty()
    val visits = visitData?.array("reports")?.toObjects().orEmpty()
    val mrRequests = syncData?.array("mrRequests")?.toObjects().orEmpty()
    val mrItems = syncData?.array("mrItems")?.toObjects().orEmpty()
    val stockMovements = syncData?.array("stockMovements")?.toObjects().orEmpty()
    val targets = syncData?.array("rkbTargets")?.toObjects().orEmpty()

    val canManage = currentUser.isHeadOffice()
    val canVisit = currentUser.canVisit()

    @Suppress("UNUSED_VARIABLE") val keepState = loading

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(screenTitle(screen), fontWeight = FontWeight.ExtraBold, color = SinergiNavy, fontSize = 17.sp)
                        Text("OPSi • PT. SINERGI Integra Services", fontSize = 10.sp, color = SinergiCyan)
                    }
                },
                actions = {
                    IconButton(onClick = { reloadAll(true) }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                    IconButton(onClick = {
                        api.logout(); loggedIn = false; user = null; syncData = null; visitData = null
                    }) { Icon(Icons.Default.Logout, contentDescription = "Keluar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val nav: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>> = buildList {
                    add(Triple("dashboard", "Home", Icons.Default.Home))
                    if (canVisit) add(Triple("visits", "Kunjungan", Icons.Default.Place))
                    add(Triple("reports", "Laporan", Icons.Default.Description))
                    add(Triple("inventory", "Stok", Icons.Default.Inventory2))
                    add(Triple("more", "Lainnya", Icons.Default.MoreHoriz))
                }
                nav.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item.first,
                        onClick = { screen = item.first },
                        icon = { Icon(item.third, contentDescription = item.second) },
                        label = { Text(item.second, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = SinergiCyan, selectedTextColor = SinergiCyan)
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (message.isNotBlank()) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFFFF1F2)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, tint = Color(0xFFBE123C), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(message, color = Color(0xFF9F1239), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { message = "" }) { Icon(Icons.Default.Close, contentDescription = "Tutup pesan") }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SinergiCyan)
            when (screen) {
                "dashboard" -> DashboardScreen(syncData, currentUser, onGo = { screen = it })
                "visits" -> VisitScreen(
                    rows = visits,
                    period = visitPeriod,
                    onPeriodChange = { visitPeriod = it },
                    onRefresh = { reloadAll(true) },
                    onAdd = { visitDialog = true },
                    onExportPdf = { if (visits.isNotEmpty()) exportReportsPdf(context, "Rekap Laporan Kunjungan • $visitPeriod", visits, true) },
                    onExportExcel = { if (visits.isNotEmpty()) exportVisitXlsx(context, visits) }
                )
                "reports" -> ReportScreen(
                    rows = filterReportsForUser(reports, currentUser),
                    onAdd = { reportDialog = true },
                    onExportPdf = { val list = filterReportsForUser(reports, currentUser); if (list.isNotEmpty()) exportReportsPdf(context, "Rekap Laporan Pekerjaan", list, false) },
                    onExportCsv = { exportReportsCsv(context, filterReportsForUser(reports, currentUser)) }
                )
                "inventory" -> InventoryScreen(
                    items = filterInventoryForUser(inventory, currentUser),
                    onMovement = { movementDialog = it },
                    canManage = canManage,
                    movements = stockMovements.filter { currentUser.isHeadOffice() || it.text("area") == currentUser.text("area") },
                    onAddItem = { movementDialog = "newItem" },
                    onExport = { exportInventoryCsv(context, filterInventoryForUser(inventory, currentUser)) }
                )
                "more" -> MoreScreen(currentUser, canManage, canVisit, onNavigate = { screen = it })
                "personil" -> PersonilScreen(
                    users = users,
                    currentUser = currentUser,
                    onEdit = { selectedUser = it },
                    onAdd = { createUserDialog = true },
                    onDelete = { target ->
                        scope.launch {
                            try { api.deleteUser(target.long("id")); syncData = api.syncAll(); message = "Data personil berhasil dihapus" }
                            catch (e: Exception) { handleApiFailure(e, "Gagal menghapus personil") }
                        }
                    },
                    onExport = { exportPersonilCsv(context, users) }
                )
                "areas" -> AreaScreen(areas)
                "rkb" -> RkbScreen(
                    if (currentUser.isHeadOffice()) targets else targets.filter { it.text("area") == currentUser.text("area") },
                    filterReportsForUser(reports, currentUser), onAdd = { rkbDialog = true }
                )
                "mr" -> MrScreen(mrRequests, mrItems, currentUser, canManage, onAdd = { mrDialog = true }, onApprove = { mr ->
                    scope.launch {
                        try { api.approveMr(mr.long("id"), jsonObjectOf("approve_oleh" to currentUser.text("nama"), "diketahui_logistik" to mr.text("diketahui_logistik"))); syncData = api.syncAll(); message = "Material Request disetujui" }
                        catch (e: Exception) { handleApiFailure(e, "Gagal memproses MR") }
                    }
                })
                "profile" -> ProfileScreen(currentUser, api.baseUrl, onSaveUrl = { api.baseUrl = it; message = "Alamat server disimpan" })
                else -> DashboardScreen(syncData, currentUser, onGo = { screen = it })
            }
        }
    }

    if (visitDialog) {
        VisitFormDialog(areas, currentUser, onDismiss = { visitDialog = false }, onSave = { payload ->
            scope.launch {
                try {
                    loading = true
                    val result = api.createVisit(payload)
                    visitDialog = false
                    visitPeriod = payload.optString("tanggal").take(7)
                    visitData = api.getVisits(visitPeriod)
                    syncData = api.syncAll()
                    message = result.optString("message", "Laporan kunjungan tersimpan")
                 } catch (e: Exception) { handleApiFailure(e, "Gagal menyimpan laporan kunjungan") }
                finally { loading = false }
            }
        })
    }
    if (reportDialog) {
        ReportFormDialog(areas, currentUser, onDismiss = { reportDialog = false }, onSave = { payload ->
            scope.launch {
                try { loading = true; val result = api.createReport(payload); reportDialog = false; syncData = api.syncAll(); message = result.optString("message", "Laporan pekerjaan tersimpan") }
                catch (e: Exception) { handleApiFailure(e, "Gagal menyimpan laporan") }
                finally { loading = false }
            }
        })
    }
    if (movementDialog.isNotBlank()) {
        when (movementDialog) {
            "newItem" -> NewItemDialog(areas, currentUser, onDismiss = { movementDialog = "" }, onSave = { payload ->
                scope.launch { try { loading = true; api.createItem(payload); movementDialog = ""; syncData = api.syncAll(); message = "Master barang berhasil disimpan" } catch (e: Exception) { handleApiFailure(e, "Gagal menyimpan barang") } finally { loading = false } }
            })
            else -> MovementDialog(movementDialog, filterInventoryForUser(inventory, currentUser), currentUser, onDismiss = { movementDialog = "" }, onSave = { payload ->
                scope.launch {
                    try {
                        loading = true
                        if (movementDialog == "in") api.createBarangMasuk(payload) else if (movementDialog == "out") api.createBarangKeluar(payload) else api.adjustStock(payload)
                        movementDialog = ""
                        syncData = api.syncAll()
                        message = "Transaksi stok berhasil disimpan"
                     } catch (e: Exception) { handleApiFailure(e, "Gagal menyimpan transaksi stok") }
                    finally { loading = false }
                }
            })
        }
    }
    if (mrDialog) {
        MrFormDialog(filterInventoryForUser(inventory, currentUser), areas, currentUser, onDismiss = { mrDialog = false }, onSave = { payload ->
            scope.launch { try { loading = true; val result = api.createMr(payload); mrDialog = false; syncData = api.syncAll(); message = result.optString("message", "Material Request diajukan") } catch (e: Exception) { handleApiFailure(e, "Gagal mengajukan MR") } finally { loading = false } }
        })
    }
    if (rkbDialog) {
        RkbFormDialog(areas, currentUser, onDismiss = { rkbDialog = false }, onSave = { payload ->
            scope.launch { try { loading = true; val result = api.saveRkbTarget(payload); rkbDialog = false; syncData = api.syncAll(); message = result.optString("message", "Target RKB disimpan") } catch (e: Exception) { handleApiFailure(e, "Gagal menyimpan target") } finally { loading = false } }
        })
    }
    if (createUserDialog) {
        UserCreateDialog(areas, onDismiss = { createUserDialog = false }, onSave = { payload ->
            scope.launch {
                try { loading = true; api.createUser(payload); createUserDialog = false; syncData = api.syncAll(); message = "Akun personil berhasil dibuat" }
                catch (e: Exception) { handleApiFailure(e, "Gagal membuat personil") }
                finally { loading = false }
            }
        })
    }
    selectedUser?.let { target ->
        UserEditDialog(target, areas, onDismiss = { selectedUser = null }, onSave = { payload ->
            scope.launch { try { loading = true; api.updateUser(target.long("id"), payload); selectedUser = null; syncData = api.syncAll(); message = "Data personil berhasil diperbarui" } catch (e: Exception) { handleApiFailure(e, "Gagal memperbarui personil") } finally { loading = false } }
        })
    }
}

@Composable
private fun LoginScreen(api: OpsiApi, onLogin: (String, String, String) -> Unit, loading: Boolean, message: String) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(api.baseUrl) }
    Box(Modifier.fillMaxSize().background(PageBg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(SinergiCyan), contentAlignment = Alignment.Center) {
                Text("OPSi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(14.dp))
            Text("OPSi Mobile", fontSize = 24.sp, fontWeight = FontWeight.Black, color = SinergiNavy)
            Text("Operational Performance System", color = SinergiCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("PT. SINERGI Integra Services", color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.height(25.dp))
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username / NIK") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("Alamat Server OPSi") }, singleLine = true)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onLogin(username, password, server) },
                enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)
            ) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("MASUK KE OPSi", fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(10.dp))
            Text("Terhubung langsung ke server OPSi perusahaan", color = Color.Gray, fontSize = 10.sp)
        }
    }
}

private fun screenTitle(screen: String): String = when (screen) {
    "dashboard" -> "Dashboard Operasional"
    "visits" -> "Laporan Kunjungan"
    "reports" -> "Laporan Pekerjaan"
    "inventory" -> "Inventory & Asset"
    "more" -> "Menu Operasional"
    "personil" -> "Data Personil"
    "areas" -> "List Area"
    "rkb" -> "Rencana Kerja (RKB)"
    "mr" -> "Material Request"
    "profile" -> "Profil Pengguna"
    else -> "OPSi Mobile"
}

@Composable
private fun DashboardScreen(data: JSONObject?, user: JSONObject, onGo: (String) -> Unit) {
    val reports = data?.array("reports")?.toObjects().orEmpty()
    val areas = data?.array("areas")?.toObjects().orEmpty().filter { it.text("status", "Aktif") == "Aktif" }
    val people = data?.array("users")?.toObjects().orEmpty()
    val stock = data?.array("inventory")?.toObjects().orEmpty()
    val visiblePeople = if (user.isHeadOffice()) people else people.filter { it.text("area") == user.text("area") }
    val visibleAreas = if (user.isHeadOffice()) areas else areas.filter { it.text("nama") == user.text("area") }
    val visibleStock = filterInventoryForUser(stock, user)
    val shownReports = filterReportsForUser(reports, user).take(6)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAFBFD)), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("EXECUTIVE OVERVIEW", color = SinergiCyan, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Selamat datang, ${user.text("nama", "Pengguna")}", color = SinergiNavy, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("${user.text("role")} • ${user.text("area")}", color = Color(0xFF475569), fontSize = 12.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Personil", visiblePeople.size.toString(), Icons.Default.Groups, Modifier.weight(1f))
                    StatCard("Laporan", filterReportsForUser(reports, user).size.toString(), Icons.Default.Description, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Area Aktif", visibleAreas.size.toString(), Icons.Default.Business, Modifier.weight(1f))
                    StatCard("Item Stok", visibleStock.size.toString(), Icons.Default.Inventory2, Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader("Akses Cepat", "Buka modul OPSi") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickButton("Kunjungan", Icons.Default.Place, Modifier.weight(1f), enabled = user.canVisit()) { onGo("visits") }
                QuickButton("Laporan", Icons.Default.Assignment, Modifier.weight(1f)) { onGo("reports") }
                QuickButton("Inventory", Icons.Default.Inventory2, Modifier.weight(1f)) { onGo("inventory") }
            }
        }
        item { SectionHeader("Laporan Pekerjaan Terbaru", "Data dari server OPSi") }
        if (shownReports.isEmpty()) item { EmptyState("Belum ada laporan pekerjaan") }
        items(shownReports) { ReportCard(it) }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label, fontSize = 11.sp, color = Color.Gray); Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = SinergiNavy) }
            Icon(icon, contentDescription = null, tint = SinergiCyan, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun VisitScreen(
    rows: List<JSONObject>, period: String, onPeriodChange: (String) -> Unit,
    onRefresh: () -> Unit, onAdd: () -> Unit, onExportPdf: () -> Unit, onExportExcel: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(period, onPeriodChange, Modifier.weight(1f), label = { Text("Periode YYYY-MM") }, singleLine = true)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Input Kunjungan") }
            OutlinedButton(onClick = onExportPdf, enabled = rows.isNotEmpty()) { Icon(Icons.Default.PictureAsPdf, null); Text("PDF") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onExportExcel, enabled = rows.isNotEmpty()) { Icon(Icons.Default.TableView, null); Text("Excel + Foto") }
        }
        Text("${rows.size} laporan pada periode $period", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (rows.isEmpty()) EmptyState("Belum ada laporan kunjungan pada periode ini")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(rows) { VisitCard(it) }
        }
    }
}

@Composable
private fun VisitCard(row: JSONObject) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(row.text("area"), color = SinergiNavy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text("${row.text("tanggal")} • ${row.text("client")}", color = Color.Gray, fontSize = 11.sp)
            Text("Penginput: ${row.text("area_koordinator_nama")}", color = Color(0xFF475569), fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
            Text(parseVisitTags(row.text("keterangan", "[]")).joinToString(" • "), color = SinergiCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Text(row.text("notice"), color = Color(0xFF334155), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.height(8.dp))
            PhotoPreview(row.text("foto_kunjungan", ""), Modifier.fillMaxWidth().heightIn(max = 240.dp))
        }
    }
}

@Composable
private fun ReportScreen(rows: List<JSONObject>, onAdd: () -> Unit, onExportPdf: () -> Unit, onExportCsv: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Icon(Icons.Default.Add, null); Text("Buat Laporan") }
            OutlinedButton(onClick = onExportPdf, enabled = rows.isNotEmpty()) { Icon(Icons.Default.PictureAsPdf, null); Text("PDF") }
            OutlinedButton(onClick = onExportCsv, enabled = rows.isNotEmpty()) { Icon(Icons.Default.TableView, null); Text("CSV") }
        }
        Text("${rows.size} laporan pekerjaan", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 9.dp))
        if (rows.isEmpty()) EmptyState("Belum ada laporan pekerjaan")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(rows) { ReportCard(it) } }
    }
}

@Composable
private fun ReportCard(row: JSONObject) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.text("area"), color = SinergiNavy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("${row.text("tanggal")} • ${row.text("nama")}", color = Color.Gray, fontSize = 11.sp)
                }
                StatusPill(row.text("status", "Done"))
            }
            Text(row.text("uraian"), color = Color(0xFF334155), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PhotoPreview(row.text("foto_before", ""), Modifier.weight(1f).height(92.dp))
                PhotoPreview(row.text("foto_progress", ""), Modifier.weight(1f).height(92.dp))
                PhotoPreview(row.text("foto_after", ""), Modifier.weight(1f).height(92.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("Before", fontSize = 9.sp, color = Color.Gray); Text("Progress", fontSize = 9.sp, color = Color.Gray); Text("After", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun InventoryScreen(
    items: List<JSONObject>,
    onMovement: (String) -> Unit,
    canManage: Boolean,
    movements: List<JSONObject>,
    onAddItem: () -> Unit,
    onExport: () -> Unit
) {
    var type by remember { mutableStateOf("all") }
    val shown = items.filter { type == "all" || it.text("type", "Inventory") == type }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == "all", { type = "all" }, label = { Text("Semua") })
            FilterChip(type == "Inventory", { type = "Inventory" }, label = { Text("Inventory") })
            FilterChip(type == "Asset", { type = "Asset" }, label = { Text("Asset") })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onMovement("in") }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Text("Barang Masuk", fontSize = 11.sp) }
            Button(onClick = { onMovement("out") }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SinergiNavy)) { Text("Barang Keluar", fontSize = 11.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMovement("adjust") }, Modifier.weight(1f)) { Text("Penyesuaian", fontSize = 11.sp) }
            OutlinedButton(onClick = onExport, Modifier.weight(1f)) { Text("Export CSV", fontSize = 11.sp) }
            if (canManage) IconButton(onClick = onAddItem) { Icon(Icons.Default.AddBox, contentDescription = "Tambah item", tint = SinergiCyan) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { Text("${shown.size} item", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            if (shown.isEmpty()) item { EmptyState("Belum ada item pada kategori ini") }
            items(shown) { item ->
                val stock = item.double("sisa")
                val min = item.double("min_stock")
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.text("nama"), fontWeight = FontWeight.Bold, color = SinergiNavy)
                            Text("${item.text("kode")} • ${item.text("kategori")} • ${item.text("area")}", color = Color.Gray, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stock.toString().removeSuffix(".0") + " " + item.text("satuan"), color = if (stock <= min) Color(0xFFBE123C) else Color(0xFF059669), fontWeight = FontWeight.Black)
                            Text("Min. ${min.toString().removeSuffix(".0")}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Riwayat Pergerakan Stok", "Maksimal 20 transaksi terbaru dari server")
            }
            if (movements.isEmpty()) item { EmptyState("Belum ada riwayat pergerakan stok") }
            items(movements.take(20)) { movement ->
                val movementLabel = when (movement.text("movement_type")) {
                    "OPENING" -> "Saldo Awal"
                    "IN" -> "Barang Masuk"
                    "OUT" -> "Barang Keluar"
                    "ADJUST" -> "Penyesuaian"
                    else -> movement.text("movement_type")
                }
                val delta = movement.double("qty_delta")
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(movement.text("nama_barang"), Modifier.weight(1f), color = SinergiNavy, fontWeight = FontWeight.Bold)
                            StatusPill(movementLabel)
                        }
                        Text("${movement.text("area")} • ${movement.text("created_at")}", color = Color.Gray, fontSize = 10.sp)
                        Text("Perubahan: ${if (delta > 0) "+" else ""}${delta.toString().removeSuffix(".0")} • Sisa: ${movement.double("qty_after").toString().removeSuffix(".0")}", color = Color(0xFF334155), fontSize = 11.sp)
                        Text("Petugas: ${movement.text("nama_pelapor")} • ${movement.text("keterangan")}", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(user: JSONObject, canManage: Boolean, canVisit: Boolean, onNavigate: (String) -> Unit) {
    val menus = buildList {
        // Sama seperti website: User Area dapat melihat data personil pada areanya sendiri.
        add(Triple("personil", "Data Personil", Icons.Default.Groups))
        if (canManage) add(Triple("areas", "List Area", Icons.Default.Business))
        add(Triple("rkb", "Rencana Kerja (RKB)", Icons.Default.CalendarMonth))
        add(Triple("mr", "Material Request", Icons.Default.Inventory))
        add(Triple("profile", "Profil Pengguna", Icons.Default.AccountCircle))
        if (canVisit) add(Triple("visits", "Laporan Kunjungan", Icons.Default.Place))
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAFBFD))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(user.text("nama"), fontWeight = FontWeight.Black, color = SinergiNavy, fontSize = 17.sp)
                    Text("${user.text("role")} • ${user.text("area")}", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
        items(menus) { menu ->
            Card(Modifier.fillMaxWidth().clickable { onNavigate(menu.first) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(menu.third, null, tint = SinergiCyan)
                    Spacer(Modifier.width(12.dp))
                    Text(menu.second, Modifier.weight(1f), color = SinergiNavy, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun PersonilScreen(users: List<JSONObject>, currentUser: JSONObject, onEdit: (JSONObject) -> Unit, onAdd: () -> Unit, onDelete: (JSONObject) -> Unit, onExport: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<JSONObject?>(null) }
    val shown = users.filter { user ->
        (currentUser.isHeadOffice() || user.text("area") == currentUser.text("area")) &&
            listOf("nama", "nik", "role", "area").any { user.text(it, "").contains(search, true) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Cari nama / NIK / jabatan / area") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} personil", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onExport) { Icon(Icons.Default.Download, null); Text("Export CSV") }
            if (currentUser.text("role") == "Admin") Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Icon(Icons.Default.Add, null); Text("Tambah") }
        }
        if (shown.isEmpty()) EmptyState("Data personil tidak ditemukan")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown) { person ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(person.text("nama"), color = SinergiNavy, fontWeight = FontWeight.ExtraBold)
                        Text("${person.text("nik")} • ${person.text("role")}", color = Color.Gray, fontSize = 11.sp)
                        Text("Area: ${person.text("area")} • Atasan: ${person.text("atasan")}", color = Color(0xFF475569), fontSize = 11.sp)
                        if (currentUser.isHeadOffice()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onEdit(person) }) { Icon(Icons.Default.Edit, null); Text("Edit") }
                            TextButton(onClick = { deleteTarget = person }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBE123C))) { Icon(Icons.Default.Delete, null); Text("Hapus") }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Hapus personil?") }, text = { Text("Data ${target.text("nama")} akan dihapus dari sistem.") }, confirmButton = { TextButton(onClick = { onDelete(target); deleteTarget = null }) { Text("Hapus", color = Color(0xFFBE123C)) } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } })
    }
}

@Composable
private fun AreaScreen(areas: List<JSONObject>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { Text("${areas.size} area aktif", color = Color.Gray, fontSize = 11.sp) }
        items(areas) { area ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(area.text("nama"), fontWeight = FontWeight.Bold, color = SinergiNavy)
                    Text(area.text("client"), color = SinergiCyan, fontSize = 12.sp)
                    Text("${area.text("kota")} • Koordinator: ${area.text("area_koordinator_nama")}", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun RkbScreen(targets: List<JSONObject>, reports: List<JSONObject>, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onAdd, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Icon(Icons.Default.Add, null); Text("Atur Target RKB") }
        Text("Target per area dan periode", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 9.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(targets) { item ->
                val actual = reports.count { it.text("area") == item.text("area") && it.text("keterangan") == "RKB" && it.text("status") == "Done" && it.text("tanggal").startsWith(item.text("periode")) }
                val target = item.optInt("target", 0)
                val progress = if (target > 0) (actual.toFloat() / target).coerceIn(0f, 1f) else 0f
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(item.text("area"), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = SinergiNavy); Text(item.text("periode"), color = Color.Gray, fontSize = 10.sp) }
                        Text("Realisasi $actual / Target $target", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 7.dp))
                        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = SinergiCyan)
                    }
                }
            }
            if (targets.isEmpty()) item { EmptyState("Belum ada target RKB") }
        }
    }
}

@Composable
private fun MrScreen(
    requests: List<JSONObject>,
    requestItems: List<JSONObject>,
    user: JSONObject,
    canManage: Boolean,
    onAdd: () -> Unit,
    onApprove: (JSONObject) -> Unit
) {
    val visibleRequests = requests.filter { user.isHeadOffice() || it.text("area") == user.text("area") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onAdd, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Icon(Icons.Default.Add, null); Text("Ajukan Material Request") }
        Text("${visibleRequests.size} pengajuan Material Request", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 9.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(visibleRequests) { mr ->
                val mrLines = requestItems.filter { it.long("mr_id") == mr.long("id") }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(mr.text("nomor"), Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, color = SinergiNavy); StatusPill(mr.text("status")) }
                        Text("${mr.text("area")} • ${mr.text("tanggal")}", color = Color.Gray, fontSize = 11.sp)
                        Text("Diajukan oleh: ${mr.text("dibuat_oleh")}", color = Color(0xFF475569), fontSize = 11.sp)
                        if (mrLines.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))
                            Text("Rincian barang", color = SinergiNavy, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            mrLines.forEach { line ->
                                Text("• ${line.text("nama_barang")} — ${line.double("jumlah").toString().removeSuffix(".0")} ${line.text("satuan")}", color = Color(0xFF475569), fontSize = 11.sp)
                                if (line.text("keterangan", "").isNotBlank()) Text("  ${line.text("keterangan")}", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                        if (mr.text("catatan", "").isNotBlank()) Text("Catatan: ${mr.text("catatan")}", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        if (canManage && mr.text("status") == "PENDING") TextButton(onClick = { onApprove(mr) }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.CheckCircle, null); Text("Setujui") }
                    }
                }
            }
            if (visibleRequests.isEmpty()) item { EmptyState("Belum ada Material Request") }
        }
    }
}

@Composable
private fun ProfileScreen(user: JSONObject, baseUrl: String, onSaveUrl: (String) -> Unit) {
    var url by remember(baseUrl) { mutableStateOf(baseUrl) }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(user.text("nama"), color = SinergiNavy, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(user.text("role"), color = SinergiCyan, fontWeight = FontWeight.Bold)
                Text("NIK: ${user.text("nik")}\nArea: ${user.text("area")}\nUsername: ${user.text("username")}", color = Color.Gray, fontSize = 12.sp)
            }
        }
        Text("Koneksi Server", color = SinergiNavy, fontWeight = FontWeight.ExtraBold)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL API OPSi") }, singleLine = true)
        Button(onClick = { onSaveUrl(url) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Text("Simpan Alamat Server") }
        Text("Perubahan alamat server berlaku untuk permintaan berikutnya.", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
private fun VisitFormDialog(areas: List<JSONObject>, user: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var areaId by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayIso()) }
    var notice by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(setOf("Kunjungan")) }
    var photo by remember { mutableStateOf("") }
    var areaExpanded by remember { mutableStateOf(false) }
    FormDialogFrame(
        title = "Input Laporan Kunjungan", onDismiss = onDismiss, confirmLabel = "Simpan Laporan",
        confirmEnabled = areaId.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
            runCatching { java.time.LocalDate.parse(date) }.isSuccess && notice.isNotBlank() && photo.isNotBlank(),
        confirmAction = { onSave(jsonObjectOf("area_id" to areaId.toLongOrNull(), "tanggal" to date, "notice" to notice.trim(), "foto_kunjungan" to photo, "keterangan" to JSONArray(tags.toList()))) }
    ) {
        OutlinedButton(onClick = { areaExpanded = true }, Modifier.fillMaxWidth()) {
            Text(areas.firstOrNull { it.long("id").toString() == areaId }?.text("nama") ?: "Pilih Area Kunjungan")
        }
        DropdownMenu(expanded = areaExpanded, onDismissRequest = { areaExpanded = false }, modifier = Modifier.heightIn(max = 260.dp)) {
            areas.forEach { area -> DropdownMenuItem(text = { Text("${area.text("nama")} • ${area.text("client")}") }, onClick = { areaId = area.long("id").toString(); areaExpanded = false }) }
        }
        OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("Tanggal (YYYY-MM-DD)") }, singleLine = true)
        OutlinedTextField(user.text("nama"), {}, Modifier.fillMaxWidth(), label = { Text("Nama Penginput") }, readOnly = true, singleLine = true)
        OutlinedTextField(notice, { notice = it }, Modifier.fillMaxWidth(), label = { Text("Notice / Catatan Kunjungan") }, minLines = 3)
        Text("Jenis Kegiatan", fontWeight = FontWeight.Bold, color = SinergiNavy, fontSize = 12.sp)
        listOf("Meeting", "Kunjungan", "Morning Briefing").forEach { tag ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = tag in tags, onCheckedChange = { checked -> tags = if (checked) tags + tag else tags - tag })
                Text(tag, fontSize = 13.sp)
            }
        }
        PhotoPickerField("Foto Kunjungan (wajib)", photo, { photo = it })
        Text("Foto dikompresi otomatis agar pengiriman ke server lebih stabil.", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
private fun ReportFormDialog(areas: List<JSONObject>, user: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var date by remember { mutableStateOf(todayIso()) }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Done") }
    var category by remember { mutableStateOf("Daily") }
    var before by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var after by remember { mutableStateOf("") }
    val area = areas.firstOrNull { it.long("id") == user.long("area_id") || it.text("nama") == user.text("area") }
    FormDialogFrame(
        title = "Buat Laporan Pekerjaan", onDismiss = onDismiss, confirmLabel = "Simpan Laporan",
        confirmEnabled = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && runCatching { java.time.LocalDate.parse(date) }.isSuccess && description.isNotBlank() && before.isNotBlank() && progress.isNotBlank() && after.isNotBlank(),
        confirmAction = {
            onSave(jsonObjectOf(
                "nomor" to "", "tanggal" to date, "nama" to user.text("nama"), "user_id" to user.long("id"),
                "area" to user.text("area"), "area_id" to area?.long("id"), "lokasi" to location,
                "uraian" to description, "foto_before" to before, "foto_progress" to progress,
                "foto_after" to after, "status" to status, "keterangan" to category, "note" to note
            ))
        }
    ) {
        OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("Tanggal (YYYY-MM-DD)") }, singleLine = true)
        OutlinedTextField(user.text("area"), {}, Modifier.fillMaxWidth(), label = { Text("Area") }, readOnly = true)
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Lokasi pekerjaan") }, singleLine = true)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Uraian pekerjaan") }, minLines = 3)
        ChoiceField("Kategori laporan", category, listOf("Daily", "RKB", "Other")) { category = it }
        ChoiceField("Status", status, listOf("Done", "Progress", "Pending")) { status = it }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Catatan tambahan") }, minLines = 2)
        PhotoPickerField("Foto Before (wajib)", before, { before = it })
        PhotoPickerField("Foto Progress (wajib)", progress, { progress = it })
        PhotoPickerField("Foto After (wajib)", after, { after = it })
    }
}

@Composable
private fun MovementDialog(type: String, inventory: List<JSONObject>, user: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var selected by remember { mutableStateOf(inventory.firstOrNull()?.long("id")?.toString().orEmpty()) }
    var qty by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("Head Office") }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val item = inventory.firstOrNull { it.long("id").toString() == selected }
    val title = when (type) { "in" -> "Barang Masuk"; "out" -> "Barang Keluar"; else -> "Penyesuaian Stok" }
    FormDialogFrame(
        title = title, onDismiss = onDismiss, confirmLabel = "Simpan Transaksi",
        confirmEnabled = selected.isNotBlank() && qty.toDoubleOrNull() != null &&
            if (type == "adjust") qty.toDoubleOrNull()!! >= 0 else qty.toDoubleOrNull()!! > 0,
        confirmAction = {
            if (item != null) {
                val payload = if (type == "adjust") jsonObjectOf("inventory_id" to item.long("id"), "actual_stock" to qty.toDouble(), "keterangan" to note)
                else jsonObjectOf(
                    "tanggal" to todayIso(), "nama_pelapor" to user.text("nama"), "user_id" to user.long("id"),
                    "area" to item.text("area"), "area_id" to item.optLong("area_id"), "inventory_id" to item.long("id"),
                    "nama_barang" to item.text("nama"), "kategori" to item.text("kategori"), "satuan" to item.text("satuan"),
                    "jumlah" to qty.toDouble(), "supplier" to supplier, "keterangan" to note
                )
                onSave(payload)
            }
        }
    ) {
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text(item?.text("nama") ?: "Pilih Barang") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) {
            inventory.forEach { inv -> DropdownMenuItem(text = { Text("${inv.text("nama")} • sisa ${inv.text("sisa")} ${inv.text("satuan")}") }, onClick = { selected = inv.long("id").toString(); expanded = false }) }
        }
        OutlinedTextField(qty, { qty = it }, Modifier.fillMaxWidth(), label = { Text(if (type == "adjust") "Stok fisik aktual" else "Jumlah") }, singleLine = true)
        if (type == "in") OutlinedTextField(supplier, { supplier = it }, Modifier.fillMaxWidth(), label = { Text("Supplier") }, singleLine = true)
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Keterangan") }, minLines = 2)
    }
}

@Composable
private fun NewItemDialog(areas: List<JSONObject>, user: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Chemical") }
    var type by remember { mutableStateOf("Inventory") }
    var unit by remember { mutableStateOf("PCS") }
    var min by remember { mutableStateOf("0") }
    var selectedAreaName by remember { mutableStateOf(user.text("area", areas.firstOrNull()?.text("nama") ?: "")) }
    var areaExpanded by remember { mutableStateOf(false) }
    val selectedArea = areas.firstOrNull { it.text("nama") == selectedAreaName }
    FormDialogFrame(
        title = "Tambah Master Barang", onDismiss = onDismiss, confirmLabel = "Simpan Barang",
        confirmEnabled = name.isNotBlank() && selectedArea != null && (min.isBlank() || (min.toDoubleOrNull() != null && min.toDoubleOrNull()!! >= 0)),
        confirmAction = { if (selectedArea != null) onSave(jsonObjectOf("kode" to code, "nama" to name, "kategori" to category, "type" to type, "area" to selectedArea.text("nama"), "area_id" to selectedArea.long("id"), "satuan" to unit, "min_stock" to (min.toDoubleOrNull() ?: 0.0))) }
    ) {
        OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("Kode barang (opsional)") })
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama barang") })
        ChoiceField("Jenis", type, listOf("Inventory", "Asset")) { type = it }
        if (user.isHeadOffice()) {
            OutlinedButton(onClick = { areaExpanded = true }, Modifier.fillMaxWidth()) { Text(selectedAreaName.ifBlank { "Pilih Area" }) }
            DropdownMenu(expanded = areaExpanded, onDismissRequest = { areaExpanded = false }, modifier = Modifier.heightIn(max = 260.dp)) {
                areas.forEach { area -> DropdownMenuItem(text = { Text(area.text("nama")) }, onClick = { selectedAreaName = area.text("nama"); areaExpanded = false }) }
            }
        } else {
            OutlinedTextField(selectedAreaName, {}, Modifier.fillMaxWidth(), label = { Text("Area") }, readOnly = true)
        }
        OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("Kategori") })
        OutlinedTextField(unit, { unit = it }, Modifier.fillMaxWidth(), label = { Text("Satuan") })
        OutlinedTextField(min, { min = it }, Modifier.fillMaxWidth(), label = { Text("Minimum stock") })
    }
}

private data class MrDraftLine(
    val inventoryId: String,
    val amount: String = "1",
    val note: String = ""
)

@Composable
private fun MrFormDialog(inventory: List<JSONObject>, areas: List<JSONObject>, user: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var lines by remember { mutableStateOf(listOf(MrDraftLine(inventory.firstOrNull()?.long("id")?.toString().orEmpty()))) }
    var expandedIndex by remember { mutableStateOf(-1) }
    var note by remember { mutableStateOf("") }
    val area = areas.firstOrNull { it.long("id") == user.long("area_id") || it.text("nama") == user.text("area") }
    val linesValid = lines.isNotEmpty() && lines.all { line ->
        inventory.any { it.long("id").toString() == line.inventoryId } &&
            (line.amount.toDoubleOrNull()?.let { it > 0 } == true)
    }
    FormDialogFrame(
        title = "Ajukan Material Request", onDismiss = onDismiss, confirmLabel = "Ajukan MR",
        confirmEnabled = linesValid && area != null,
        confirmAction = {
            val detailArray = JSONArray()
            lines.forEach { line ->
                val item = inventory.firstOrNull { it.long("id").toString() == line.inventoryId }
                if (item != null) {
                    detailArray.put(jsonObjectOf(
                        "inventory_id" to item.long("id"),
                        "nama_barang" to item.text("nama"),
                        "satuan" to item.text("satuan"),
                        "jumlah" to (line.amount.toDoubleOrNull() ?: 1.0),
                        "sisa" to item.double("sisa"),
                        "keterangan" to line.note.trim()
                    ))
                }
            }
            onSave(jsonObjectOf(
                "area" to user.text("area"), "area_id" to area?.long("id"),
                "bulan" to currentPeriod(), "tanggal" to todayIso(),
                "dibuat_oleh" to user.text("nama"), "diketahui_oleh" to user.text("atasan"),
                "items" to detailArray, "catatan" to note.trim()
            ))
        }
    ) {
        OutlinedTextField(user.text("area"), {}, Modifier.fillMaxWidth(), label = { Text("Area") }, readOnly = true)
        Text("Daftar barang diminta", color = SinergiNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        lines.forEachIndexed { index, line ->
            val selectedItem = inventory.firstOrNull { it.long("id").toString() == line.inventoryId }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Barang ${index + 1}", Modifier.weight(1f), color = SinergiNavy, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        if (lines.size > 1) TextButton(onClick = {
                            lines = lines.filterIndexed { lineIndex, _ -> lineIndex != index }
                            if (expandedIndex == index) expandedIndex = -1
                        }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFBE123C)); Text("Hapus", color = Color(0xFFBE123C)) }
                    }
                    OutlinedButton(onClick = { expandedIndex = index }, Modifier.fillMaxWidth()) {
                        Text(selectedItem?.let { "${it.text("nama")} • stok ${it.text("sisa")} ${it.text("satuan")}" } ?: "Pilih Barang")
                    }
                    DropdownMenu(expanded = expandedIndex == index, onDismissRequest = { expandedIndex = -1 }, modifier = Modifier.heightIn(max = 240.dp)) {
                        inventory.forEach { inv ->
                            DropdownMenuItem(text = { Text("${inv.text("nama")} • ${inv.text("sisa")} ${inv.text("satuan")}") }, onClick = {
                                lines = lines.mapIndexed { lineIndex, old -> if (lineIndex == index) old.copy(inventoryId = inv.long("id").toString()) else old }
                                expandedIndex = -1
                            })
                        }
                    }
                    OutlinedTextField(line.amount, { value -> lines = lines.mapIndexed { lineIndex, old -> if (lineIndex == index) old.copy(amount = value) else old } }, Modifier.fillMaxWidth(), label = { Text("Jumlah diminta") }, singleLine = true)
                    OutlinedTextField(line.note, { value -> lines = lines.mapIndexed { lineIndex, old -> if (lineIndex == index) old.copy(note = value) else old } }, Modifier.fillMaxWidth(), label = { Text("Keterangan barang (opsional)") }, minLines = 1)
                }
            }
        }
        OutlinedButton(onClick = { lines = lines + MrDraftLine(inventory.firstOrNull()?.long("id")?.toString().orEmpty()) }, Modifier.fillMaxWidth(), enabled = inventory.isNotEmpty()) {
            Icon(Icons.Default.Add, null); Text("Tambah Barang")
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Catatan pengajuan") }, minLines = 2)
    }
}

@Composable
private fun RkbFormDialog(areas: List<JSONObject>, user: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    val roleCanChooseAllAreas = user.isHeadOffice() || user.text("role") in setOf(
        "Admin", "Direktur Operasional", "Operasional Manager", "Manager Operasional", "Area Koordinator", "Admin Operasional"
    )
    val areaOptions = if (roleCanChooseAllAreas) areas else areas.filter { it.text("nama") == user.text("area") }
    var selectedArea by remember(user) {
        mutableStateOf((areaOptions.firstOrNull { it.long("id") == user.long("area_id") }
            ?: areaOptions.firstOrNull())?.long("id")?.toString().orEmpty())
    }
    var target by remember { mutableStateOf("30") }
    var period by remember { mutableStateOf(currentPeriod()) }
    var expanded by remember { mutableStateOf(false) }
    val area = areaOptions.firstOrNull { it.long("id").toString() == selectedArea }
    FormDialogFrame(
        title = "Atur Target RKB", onDismiss = onDismiss, confirmLabel = "Simpan Target",
        confirmEnabled = area != null && target.toIntOrNull()?.let { it >= 0 } == true && period.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")),
        confirmAction = { if (area != null) onSave(jsonObjectOf("area" to area.text("nama"), "area_id" to area.long("id"), "target" to target.toInt(), "periode" to period)) }
    ) {
        if (roleCanChooseAllAreas) {
            OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text(area?.text("nama") ?: "Pilih Area") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 260.dp)) {
                areaOptions.forEach { option -> DropdownMenuItem(text = { Text(option.text("nama")) }, onClick = { selectedArea = option.long("id").toString(); expanded = false }) }
            }
        } else {
            OutlinedTextField(area?.text("nama") ?: user.text("area"), {}, Modifier.fillMaxWidth(), label = { Text("Area") }, readOnly = true)
        }
        OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Target") })
        OutlinedTextField(period, { period = it }, Modifier.fillMaxWidth(), label = { Text("Periode YYYY-MM") })
    }
}

@Composable
private fun UserCreateDialog(areas: List<JSONObject>, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var nik by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Supervisor") }
    var areaName by remember { mutableStateOf(areas.firstOrNull()?.text("nama") ?: "") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val area = areas.firstOrNull { it.text("nama") == areaName }
    FormDialogFrame(
        title = "Tambah Personil", onDismiss = onDismiss, confirmLabel = "Buat Akun",
        confirmEnabled = nik.isNotBlank() && name.isNotBlank() && role.isNotBlank() && username.isNotBlank() && password.length >= 6 && area != null,
        confirmAction = { onSave(jsonObjectOf("nik" to nik, "nama" to name, "role" to role, "level" to "User Area", "area" to areaName, "area_id" to area?.long("id"), "atasan" to "-", "atasan_id" to null, "username" to username, "password" to password)) }
    ) {
        OutlinedTextField(nik, { nik = it }, Modifier.fillMaxWidth(), label = { Text("NIK") })
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama lengkap") })
        ChoiceField("Jabatan", role, listOf(
            "Chief Supervisor", "Supervisor", "Team Leader", "Admin", "Gondola", "Receptionis", "OB", "CSO", "Gardener", "Operator",
            "Area Koordinator", "Direktur Operasional", "Operasional Manager", "Manager Operasional", "Admin Operasional"
        )) { role = it }
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text(areaName.ifBlank { "Pilih Area" }) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) { areas.forEach { DropdownMenuItem(text = { Text(it.text("nama")) }, onClick = { areaName = it.text("nama"); expanded = false }) } }
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") })
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password minimal 6 karakter") }, visualTransformation = PasswordVisualTransformation())
    }
}

@Composable
private fun UserEditDialog(person: JSONObject, areas: List<JSONObject>, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var nik by remember(person) { mutableStateOf(person.text("nik", "")) }
    var name by remember(person) { mutableStateOf(person.text("nama", "")) }
    var role by remember(person) { mutableStateOf(person.text("role", "")) }
    var areaName by remember(person) { mutableStateOf(person.text("area", "")) }
    var username by remember(person) { mutableStateOf(person.text("username", "")) }
    var password by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    FormDialogFrame(
        title = "Edit Data Personil", onDismiss = onDismiss, confirmLabel = "Simpan Perubahan",
        confirmEnabled = nik.isNotBlank() && name.isNotBlank() && role.isNotBlank() && username.isNotBlank(),
        confirmAction = {
            val area = areas.firstOrNull { it.text("nama") == areaName }
            val headOfficeRoles = setOf("Admin", "Direktur Operasional", "Operasional Manager", "Manager Operasional", "Area Koordinator", "Admin Operasional")
            val newLevel = if (role in headOfficeRoles) "Head Office" else "User Area"
            val atasanId = person.optLong("atasan_id").takeIf { it > 0 }
            val payload = jsonObjectOf("nik" to nik, "nama" to name, "role" to role, "level" to newLevel, "area" to areaName, "area_id" to (area?.long("id") ?: person.optLong("area_id").takeIf { it > 0 }), "atasan" to person.text("atasan"), "atasan_id" to atasanId, "username" to username, "foto_profile" to person.text("foto_profile", ""))
            if (password.isNotBlank()) payload.put("password", password)
            onSave(payload)
        }
    ) {
        OutlinedTextField(nik, { nik = it }, Modifier.fillMaxWidth(), label = { Text("NIK") })
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nama lengkap") })
        OutlinedTextField(role, { role = it }, Modifier.fillMaxWidth(), label = { Text("Jabatan") })
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text(areaName) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) { areas.forEach { DropdownMenuItem(text = { Text(it.text("nama")) }, onClick = { areaName = it.text("nama"); expanded = false }) } }
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") })
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password baru (kosongkan jika tidak berubah)") }, visualTransformation = PasswordVisualTransformation())
    }
}

@Composable
private fun FormDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    confirmAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val dialogMaxHeight = (maxHeight * 0.88f).coerceAtMost(690.dp)
            Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = dialogMaxHeight), shape = RoundedCornerShape(22.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = SinergiNavy, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup") }
                }
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text("Batal") }
                    Button(onClick = confirmAction, enabled = confirmEnabled, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SinergiCyan)) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
                }
            }
            }
        }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
            Text(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false }) }
        }
    }
}

@Composable
private fun PhotoPickerField(label: String, data: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var pickerError by remember { mutableStateOf("") }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { uriToDataUrl(context, uri) }.onSuccess(onChange).onFailure { pickerError = it.message ?: "Foto tidak dapat dibaca" }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) runCatching { uriToDataUrl(context, uri) }.onSuccess(onChange).onFailure { pickerError = it.message ?: "Foto kamera gagal diproses" }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = SinergiNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch("image/*") }, Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Text("Galeri", fontSize = 11.sp) }
            OutlinedButton(onClick = { runCatching { cameraUri = cameraOutputUri(context); camera.launch(cameraUri) }.onFailure { pickerError = it.message ?: "Kamera tidak dapat dibuka" } }, Modifier.weight(1f)) { Icon(Icons.Default.PhotoCamera, null); Text("Kamera", fontSize = 11.sp) }
        }
        if (data.isNotBlank()) PhotoPreview(data, Modifier.fillMaxWidth().height(150.dp))
        else Text("Belum ada foto dipilih", color = Color.Gray, fontSize = 10.sp)
        if (pickerError.isNotBlank()) Text(pickerError, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
    }
}

@Composable
private fun PhotoPreview(data: String, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { dataUrlToBitmap(data, 1000) }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Foto laporan", modifier = modifier.clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    else Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ImageNotSupported, null, tint = Color.LightGray) }
}

@Composable
private fun StatusPill(text: String) {
    val ok = text.equals("Done", true) || text.equals("APPROVED", true)
    Surface(shape = CircleShape, color = if (ok) Color(0xFFDCFCE7) else Color(0xFFFFF7ED)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = if (ok) Color(0xFF166534) else Color(0xFF9A3412), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = SinergiNavy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
private fun QuickButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(72.dp), shape = RoundedCornerShape(15.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (enabled) SinergiCyan else Color.LightGray)
            Text(title, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inbox, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(38.dp))
            Text(text, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

private fun filterReportsForUser(rows: List<JSONObject>, user: JSONObject): List<JSONObject> =
    if (user.isHeadOffice()) rows else rows.filter { it.text("area") == user.text("area") }

private fun filterInventoryForUser(rows: List<JSONObject>, user: JSONObject): List<JSONObject> =
    if (user.isHeadOffice()) rows else rows.filter { it.text("area") == user.text("area") }

private fun exportReportsCsv(context: android.content.Context, rows: List<JSONObject>) {
    exportCsv(context, "Rekap-Laporan-Pekerjaan-${todayIso()}.csv", listOf("Nomor", "Tanggal", "Nama", "Area", "Lokasi", "Uraian", "Status", "Kategori"), rows.map { listOf(it.text("nomor"), it.text("tanggal"), it.text("nama"), it.text("area"), it.text("lokasi"), it.text("uraian"), it.text("status"), it.text("keterangan")) })
}

private fun exportInventoryCsv(context: android.content.Context, rows: List<JSONObject>) {
    exportCsv(context, "Rekap-Inventory-Asset-${todayIso()}.csv", listOf("Kode", "Nama", "Kategori", "Type", "Area", "Satuan", "Stok", "Sisa", "Minimum Stock"), rows.map { listOf(it.text("kode"), it.text("nama"), it.text("kategori"), it.text("type"), it.text("area"), it.text("satuan"), it.text("stok"), it.text("sisa"), it.text("min_stock")) })
}

private fun exportPersonilCsv(context: android.content.Context, rows: List<JSONObject>) {
    exportCsv(context, "Rekap-Personil-${todayIso()}.csv", listOf("NIK", "Nama", "Jabatan", "Level", "Area", "Atasan", "Username", "Status"), rows.map { listOf(it.text("nik"), it.text("nama"), it.text("role"), it.text("level"), it.text("area"), it.text("atasan"), it.text("username"), it.text("status")) })
}

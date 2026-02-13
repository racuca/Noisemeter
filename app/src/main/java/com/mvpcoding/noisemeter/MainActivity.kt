package com.mvpcoding.noisemeter

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.pow
import com.google.android.gms.ads.MobileAds

lateinit var adManager: InterstitialAdManager

enum class Language { EN, KO }

data class AppStrings(
    val title: String,
    val unit: String,
    val sessionMax: String,
    val sessionAvg: String,
    val globalMax: String,
    val globalAvg: String,
    val setDuration: String,
    val complete: String,
    val resultMax: String,
    val resultAvg: String,
    val allTimeMax: String,
    val allTimeAvg: String,
    val tryAgain: String,
    val clearData: String,
    val settings: String,
    val reset: String,
    val selectLanguage: String,
    val close: String,
    val continuous: String,
    val noiseStandards: String,
    val areaSelection: String,
    val day: String,
    val night: String,
    val limit: String,
    val status: String,
    val statusSafe: String,
    val statusWarning: String,
    val statusExceeded: String,
    val history: String,
    val attempt: String,
    val measured: String
)

val EnStrings = AppStrings(
    title = "Noise Meter",
    unit = "DECIBELS (dB)",
    sessionMax = "Session Max",
    sessionAvg = "Session Avg",
    globalMax = "Global Max",
    globalAvg = "Global Avg",
    setDuration = "Set Duration",
    complete = "Measurement Complete",
    resultMax = "Max Level",
    resultAvg = "Avg Level",
    allTimeMax = "All-time Max",
    allTimeAvg = "All-time Avg",
    tryAgain = "Try Again",
    clearData = "Clear All Data",
    settings = "Settings",
    reset = "Reset",
    selectLanguage = "Select Language",
    close = "Close",
    continuous = "Cont.",
    noiseStandards = "Noise Standards (South Korea)",
    areaSelection = "Select Area Type",
    day = "Day (06:00-22:00)",
    night = "Night (22:00-06:00)",
    limit = "Limit",
    status = "Status",
    statusSafe = "Safe",
    statusWarning = "Warning",
    statusExceeded = "Exceeded",
    history = "Measurement History",
    attempt = "Attempt",
    measured = "Measured"
)

val KoStrings = AppStrings(
    title = "소음 측정기",
    unit = "데시벨 (dB)",
    sessionMax = "세션 최대",
    sessionAvg = "세션 평균",
    globalMax = "전체 최대",
    globalAvg = "전체 평균",
    setDuration = "측정 시간 설정",
    complete = "측정 완료",
    resultMax = "최대 수치",
    resultAvg = "평균 수치",
    allTimeMax = "누적 최대",
    allTimeAvg = "누적 평균",
    tryAgain = "다시 시도",
    clearData = "모든 데이터 초기화",
    settings = "설정",
    reset = "초기화",
    selectLanguage = "언어 선택",
    close = "닫기",
    continuous = "연속",
    noiseStandards = "국내 소음 환경 기준",
    areaSelection = "지역 유형 선택",
    day = "낮 (06:00~22:00)",
    night = "밤 (22:00~06:00)",
    limit = "기준치",
    status = "상태",
    statusSafe = "양호",
    statusWarning = "주의",
    statusExceeded = "기준 초과",
    history = "측정 이력",
    attempt = "회차",
    measured = "측정치"
)

data class NoiseStandard(
    val nameKo: String,
    val nameEn: String,
    val dayLimit: Int,
    val nightLimit: Int,
    val unit: String
)

val noiseStandards = listOf(
    NoiseStandard("주거지역 (전용)", "Residential (Exclusive)", 50, 40, "Leq"),
    NoiseStandard("주거지역 (일반)", "Residential (General)", 55, 45, "Leq"),
    NoiseStandard("상업지역", "Commercial", 65, 55, "Leq"),
    NoiseStandard("공업지역", "Industrial", 70, 65, "Leq"),
    NoiseStandard("교통소음 (도로변)", "Roadside", 65, 55, "Leq"),
    NoiseStandard("항공기소음", "Aircraft", 75, 75, "Lden")
)

data class MeasurementRecord(
    val max: Double,
    val avg: Double
)

class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this) {}

        adManager = InterstitialAdManager(
            this,
            "ca-app-pub-3940256099942544/1033173712"
        )
        adManager.load()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1A73E8),
                    secondary = Color(0xFF5F6368),
                    tertiary = Color(0xFFFBBC04)
                )
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NoiseMeterApp(adManager)
                }
            }
        }
    }
}

@Composable
fun NoiseMeterApp(adManager: InterstitialAdManager) {
    var resultData by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
    var currentLanguage by rememberSaveable { mutableStateOf(Language.EN) }
    var showSettings by remember { mutableStateOf(false) }
    
    val strings = if (currentLanguage == Language.KO) KoStrings else EnStrings

    var totalCumulativeIntensitySum by remember { mutableStateOf(0.0) }
    var totalCumulativeCount by remember { mutableStateOf(0) }
    var totalCumulativeMax by remember { mutableStateOf(0.0) }
    
    val history = remember { mutableStateListOf<MeasurementRecord>() }
    
    val globalCumulativeAvg = if (totalCumulativeCount > 0) 10 * log10(totalCumulativeIntensitySum / totalCumulativeCount) else 0.0

    val resetStats = {
        totalCumulativeIntensitySum = 0.0
        totalCumulativeCount = 0
        totalCumulativeMax = 0.0
        resultData = null
        history.clear()
    }

    if (showSettings) {
        LanguageSettingsDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { currentLanguage = it },
            onDismiss = { showSettings = false },
            strings = strings
        )
    }

    if (resultData == null) {
        NoiseMeterScreen(
            cumulativeAvg = globalCumulativeAvg,
            cumulativeMax = totalCumulativeMax,
            onReset = resetStats,
            onSettingsClick = { showSettings = true },
            strings = strings,
            onFinished = { current, max, avg, intensitySum, sessionCount ->
                totalCumulativeIntensitySum += intensitySum
                totalCumulativeCount += sessionCount
                if (max > totalCumulativeMax) totalCumulativeMax = max
                resultData = Triple(current, max, avg)
                history.add(0, MeasurementRecord(max, avg)) // Add to the beginning
            }
        )
    } else {
        ResultScreen(
            data = resultData!!,
            cumulativeAvg = globalCumulativeAvg,
            cumulativeMax = totalCumulativeMax,
            history = history,
            adManager = adManager,
            strings = strings,
            language = currentLanguage,
            onReset = resetStats,
            onBack = { resultData = null }
        )
    }
}

@Composable
fun LanguageSettingsDialog(
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit,
    strings: AppStrings
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectLanguage) },
        text = {
            Column(Modifier.selectableGroup()) {
                LanguageOption(
                    label = "English",
                    selected = currentLanguage == Language.EN,
                    onClick = { onLanguageSelected(Language.EN) }
                )
                LanguageOption(
                    label = "한국어",
                    selected = currentLanguage == Language.KO,
                    onClick = { onLanguageSelected(Language.KO) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close)
            }
        }
    )
}

@Composable
fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun NoiseMeterScreen(
    cumulativeAvg: Double,
    cumulativeMax: Double,
    onReset: () -> Unit,
    onSettingsClick: () -> Unit,
    strings: AppStrings,
    onFinished: (Double, Double, Double, Double, Int) -> Unit
) {
    var isMeasuring by remember { mutableStateOf(false) }
    var currentDb by remember { mutableStateOf(0.0) }
    var maxDb by remember { mutableStateOf(0.0) }
    var avgDb by remember { mutableStateOf(0.0) }
    
    // -1 represents continuous mode
    var selectedSeconds by remember { mutableStateOf(10) }
    var progress by remember { mutableStateOf(0f) }
    
    val durations = listOf(5, 10, 30, 60, -1)
    val scope = rememberCoroutineScope()
    var measurementJob by remember { mutableStateOf<Job?>(null) }

    val animatedDb by animateFloatAsState(targetValue = currentDb.toFloat())
    val dbColor by animateColorAsState(
        targetValue = when {
            currentDb > 80 -> Color.Red
            currentDb > 60 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                strings.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Settings, contentDescription = strings.settings)
            }
        }

        // 메인 대시보드 카드
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.2f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f".format(animatedDb),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = dbColor
                    )
                    Text(strings.unit, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    
                    if (isMeasuring) {
                        Spacer(modifier = Modifier.height(24.dp))
                        if (selectedSeconds > 0) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.width(150.dp).clip(CircleShape),
                                color = dbColor
                            )
                        } else {
                            // Infinite indicator for continuous mode
                            LinearProgressIndicator(
                                modifier = Modifier.width(150.dp).clip(CircleShape),
                                color = dbColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 상세 통계 그리드
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatItem(strings.sessionMax, "%.1f".format(maxDb), Modifier.weight(1f))
            StatItem(strings.sessionAvg, "%.1f".format(avgDb), Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatItem(strings.globalMax, "%.1f".format(cumulativeMax), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
            StatItem(strings.globalAvg, "%.1f".format(cumulativeAvg), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.weight(1f))

        // 하단 컨트롤러
        if (!isMeasuring) {
            Text(strings.setDuration, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durations.forEach { sec ->
                    FilterChip(
                        selected = selectedSeconds == sec,
                        onClick = { selectedSeconds = sec },
                        label = { Text(if (sec == -1) strings.continuous else "${sec}s") }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = {
                    if (isMeasuring) {
                        measurementJob?.cancel()
                    } else {
                        measurementJob = scope.launch(Dispatchers.IO) {
                            isMeasuring = true
                            currentDb = 0.0
                            maxDb = 0.0
                            avgDb = 0.0
                            var intensitySum = 0.0
                            var count = 0
                            val startTime = System.currentTimeMillis()
                            val durationMillis = if (selectedSeconds > 0) selectedSeconds * 1000L else Long.MAX_VALUE

                            val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                            val buffer = ShortArray(bufferSize)
                            
                            recorder.startRecording()
                            try {
                                while (isActive && (System.currentTimeMillis() - startTime < durationMillis)) {
                                    if (selectedSeconds > 0) {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        progress = elapsed.toFloat() / durationMillis
                                    }
                                    
                                    val read = recorder.read(buffer, 0, buffer.size)
                                    if (read > 0) {
                                        var sumSquares = 0.0
                                        for (i in 0 until read) sumSquares += buffer[i].toDouble() * buffer[i]
                                        val rms = sqrt(sumSquares / read)
                                        val db = if (rms > 0) 20 * log10(rms) else 0.0

                                        currentDb = db
                                        if (db > maxDb) maxDb = db
                                        
                                        // Energy average for Leq
                                        val intensity = 10.0.pow(db / 10.0)
                                        intensitySum += intensity
                                        count++
                                        avgDb = 10 * log10(intensitySum / count)
                                    }
                                    delay(100)
                                }
                            } finally {
                                recorder.stop()
                                recorder.release()
                                isMeasuring = false
                                progress = 0f
                                launch(Dispatchers.Main) { onFinished(currentDb, maxDb, avgDb, intensitySum, count) }
                            }
                        }
                    }
                },
                containerColor = if (isMeasuring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            ) {
                Icon(
                    if (isMeasuring) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
            
            if (!isMeasuring) {
                Spacer(modifier = Modifier.width(24.dp))
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = strings.reset)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.DarkGray) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ResultScreen(
    data: Triple<Double, Double, Double>,
    cumulativeAvg: Double,
    cumulativeMax: Double,
    history: List<MeasurementRecord>,
    adManager: InterstitialAdManager,
    strings: AppStrings,
    language: Language,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { adManager.show {} }
    
    var selectedAreaIndex by remember { mutableStateOf(0) }
    val selectedStandard = noiseStandards[selectedAreaIndex]
    
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(strings.complete, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // All-time Stats at the TOP
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ResultRow(strings.allTimeMax, "%.1f dB".format(cumulativeMax), MaterialTheme.colorScheme.onPrimaryContainer)
                ResultRow(strings.allTimeAvg, "%.1f dB".format(cumulativeAvg), MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current Session Stats
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ResultRow(strings.resultMax, "%.1f dB".format(data.second))
                ResultRow(strings.resultAvg, "%.1f dB".format(data.third))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Measurement History Section
        if (history.isNotEmpty()) {
            Text(strings.history, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    history.forEachIndexed { index, record ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${strings.attempt} ${history.size - index}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("Max: %.1f / Avg: %.1f dB".format(record.max, record.avg), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (index < history.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Noise Standard Comparison Section
        Text(strings.noiseStandards, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ScrollableTabRow(
            selectedTabIndex = selectedAreaIndex,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            noiseStandards.forEachIndexed { index, standard ->
                Tab(
                    selected = selectedAreaIndex == index,
                    onClick = { selectedAreaIndex = index },
                    text = { Text(if (language == Language.KO) standard.nameKo else standard.nameEn) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StandardComparisonRow(strings.day, selectedStandard.dayLimit, data.third, strings, selectedStandard.unit, false)
                Spacer(modifier = Modifier.height(8.dp))
                StandardComparisonRow(strings.night, selectedStandard.nightLimit, data.third, strings, selectedStandard.unit, true)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(strings.tryAgain, fontWeight = FontWeight.Bold)
        }
        
        TextButton(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
            Text(strings.clearData, color = Color.Gray)
        }
    }
}

@Composable
fun StandardComparisonRow(label: String, limit: Int, currentAvg: Double, strings: AppStrings, unit: String, isNightPeriod: Boolean) {
    // Apply penalty for Lden if it's night period (+10dB)
    val convertedValue = if (unit == "Lden" && isNightPeriod) currentAvg + 10.0 else currentAvg
    
    val status = when {
        convertedValue > limit -> strings.statusExceeded
        convertedValue > limit - 5 -> strings.statusWarning
        else -> strings.statusSafe
    }
    
    val statusColor = when (status) {
        strings.statusExceeded -> Color.Red
        strings.statusWarning -> Color(0xFFFBBC04)
        else -> Color(0xFF34A853)
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${strings.limit}: $limit $unit", style = MaterialTheme.typography.bodyMedium)
                Text("${strings.measured}: %.1f $unit".format(convertedValue), fontWeight = FontWeight.Bold)
            }
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = status,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

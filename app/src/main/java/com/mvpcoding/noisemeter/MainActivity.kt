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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val continuous: String
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
    continuous = "Cont."
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
    continuous = "연속"
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

    var totalCumulativeSum by remember { mutableStateOf(0.0) }
    var totalCumulativeCount by remember { mutableStateOf(0) }
    var totalCumulativeMax by remember { mutableStateOf(0.0) }
    
    val globalCumulativeAvg = if (totalCumulativeCount > 0) totalCumulativeSum / totalCumulativeCount else 0.0

    val resetStats = {
        totalCumulativeSum = 0.0
        totalCumulativeCount = 0
        totalCumulativeMax = 0.0
        resultData = null
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
            onFinished = { current, max, avg, sessionSum, sessionCount ->
                totalCumulativeSum += sessionSum
                totalCumulativeCount += sessionCount
                if (max > totalCumulativeMax) totalCumulativeMax = max
                resultData = Triple(current, max, avg)
            }
        )
    } else {
        ResultScreen(
            data = resultData!!,
            cumulativeAvg = globalCumulativeAvg,
            cumulativeMax = totalCumulativeMax,
            adManager = adManager,
            strings = strings,
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
                            var sum = 0.0
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
                                        sum += db
                                        count++
                                        avgDb = sum / count
                                    }
                                    delay(100)
                                }
                            } finally {
                                recorder.stop()
                                recorder.release()
                                isMeasuring = false
                                progress = 0f
                                launch(Dispatchers.Main) { onFinished(currentDb, maxDb, avgDb, sum, count) }
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
    adManager: InterstitialAdManager,
    strings: AppStrings,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { adManager.show {} }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(strings.complete, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ResultRow(strings.resultMax, "%.1f dB".format(data.second))
                ResultRow(strings.resultAvg, "%.1f dB".format(data.third))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ResultRow(strings.allTimeMax, "%.1f dB".format(cumulativeMax), MaterialTheme.colorScheme.tertiary)
                ResultRow(strings.allTimeAvg, "%.1f dB".format(cumulativeAvg), MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

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
fun ResultRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

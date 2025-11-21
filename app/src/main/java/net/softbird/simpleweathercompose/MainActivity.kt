package net.softbird.simpleweathercompose

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.softbird.simpleweathercompose.ui.theme.SimpleWeatherComposeTheme
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale

// ----------------------------------------------------------------
// DATA LAYER (Models)
// ----------------------------------------------------------------

data class WeatherResponse(
    val current: CurrentWeather,
    val forecast: Forecast
)

data class CurrentWeather(
    val temp_c: Double,
    val condition: Condition,
    val last_updated: String
)

data class Condition(
    val text: String,
    val icon: String
)

data class Forecast(
    val forecastday: List<ForecastDay>
)

data class ForecastDay(
    val date: String,
    val day: DaySummary,
    val hour: List<Hour>
)

data class DaySummary(
    val maxtemp_c: Double,
    val mintemp_c: Double,
    val condition: Condition
)

data class Hour(
    val time: String,
    val temp_c: Double,
    val condition: Condition
)

// ----------------------------------------------------------------
// NETWORK LAYER (Retrofit)
// ----------------------------------------------------------------

private const val BASE_URL = "https://api.weatherapi.com/v1/"
private const val API_KEY = "fa8b3df74d4042b9aa7135114252304"

interface WeatherApi {
    @GET("forecast.json")
    suspend fun getForecast(
        @Query("key") key: String = API_KEY,
        @Query("q") query: String = "55.7569,37.6151", // Москва
        @Query("days") days: Int = 3,
        @Query("aqi") aqi: String = "no",
        @Query("alerts") alerts: String = "no"
    ): WeatherResponse
}

object RetrofitClient {
    val api: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }
}

// ----------------------------------------------------------------
// VIEW MODEL
// ----------------------------------------------------------------

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val data: WeatherResponse) : WeatherUiState()
    data class Error(@StringRes val messageResId: Int) : WeatherUiState()
}

class WeatherViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState

    init {
        loadWeather()
    }

    fun loadWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                // A small artificial delay to make the loading indicator visible
                delay(500)
                val response = RetrofitClient.api.getForecast()
                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                Log.e("WeatherApp", "Error: ${e.message}")
                _uiState.value = WeatherUiState.Error(R.string.error_no_internet)
            }
        }
    }
}

// ----------------------------------------------------------------
// UI LAYER (Compose)
// ----------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleWeatherComposeTheme {
                WeatherScreen()
            }
        }
    }
}

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    // State for showing error dialog
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessageId by remember { mutableStateOf<Int?>(null) }

    // Effect for handling error condition
    LaunchedEffect(state) {
        if (state is WeatherUiState.Error) {
            errorMessageId = (state as WeatherUiState.Error).messageResId
            showErrorDialog = true
        } else {
            showErrorDialog = false
        }
    }

    // Main container
    Scaffold(
        bottomBar = {
            // Button at the bottom of the screen
            Button(
                onClick = { viewModel.loadWeather() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp)
            ) {
                Text(stringResource(R.string.reload))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is WeatherUiState.Loading -> {
                    CircularProgressIndicator()
                }

                is WeatherUiState.Success -> {
                    WeatherContent(data = currentState.data)
                }

                is WeatherUiState.Error -> {
                    // The error is handled by the dialog, but a placeholder can be shown here
                    Text("Нет данных", color = Color.Gray)
                }
            }
        }

        // Error dialog
        if (showErrorDialog && errorMessageId != null) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(stringResource(id = errorMessageId!!)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showErrorDialog = false
                            viewModel.loadWeather()
                        }
                    ) {
                        Text(stringResource(R.string.repeat))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun WeatherContent(data: WeatherResponse) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs =
        listOf(stringResource(R.string.current), stringResource(R.string.hourly), stringResource(R.string.forecast3))

    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Text(
            text = stringResource(R.string.city),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
        )

        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        // Tab content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> CurrentWeatherTab(data.current)
                1 -> HourlyWeatherTab(data.forecast.forecastday.firstOrNull()?.hour ?: emptyList())
                2 -> DailyForecastTab(data.forecast.forecastday)
            }
        }
    }
}

@Composable
fun CurrentWeatherTab(current: CurrentWeather) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WeatherIcon(url = current.condition.icon, size = 120.dp)

        Text(
            text = "${current.temp_c}°C",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = current.condition.text,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.updated) + ": ${current.last_updated}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun HourlyWeatherTab(hours: List<Hour>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(hours) { hour ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Formatting time from "2023-11-20 14:00" to "14:00"
                    val time = hour.time.split(" ").lastOrNull() ?: hour.time

                    Text(text = time, style = MaterialTheme.typography.bodyLarge)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(url = hour.condition.icon, size = 40.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${hour.temp_c}°",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyForecastTab(days: List<ForecastDay>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { day ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = formatDate(day.date),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = day.day.condition.text, style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherIcon(url = day.day.condition.icon, size = 48.dp)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Max: ${day.day.maxtemp_c}°",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Min: ${day.day.mintemp_c}°",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherIcon(url: String, size: androidx.compose.ui.unit.Dp) {
    // The API returns icons with "//cdn...", you need to add "https:"
    val fixedUrl = if (url.startsWith("//")) "https:$url" else url

    AsyncImage(
        model = fixedUrl,
        contentDescription = "Weather Icon",
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

// Beautiful date
fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMMM", Locale.forLanguageTag("ru"))
        val date = inputFormat.parse(dateString)
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}
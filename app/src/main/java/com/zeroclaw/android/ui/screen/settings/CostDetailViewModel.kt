// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroclaw.android.ZeroClawApplication
import com.zeroclaw.android.model.CostSummary
import com.zeroclaw.android.service.CostBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-model cost breakdown entry parsed from [CostSummary.modelBreakdownJson].
 *
 * @property model Model identifier (e.g. "gpt-4", "claude-3-opus").
 * @property costUsd Total cost attributed to this model, in USD.
 * @property tokens Total token count for this model.
 * @property requests Number of inference requests made with this model.
 */
data class ModelCostEntry(
    val model: String,
    val costUsd: Double,
    val tokens: Long,
    val requests: Int,
)

/**
 * Aggregated data for the cost detail screen.
 *
 * @property summary The raw cost summary from the bridge.
 * @property modelBreakdown Parsed per-model cost entries.
 */
data class CostDetailData(
    val summary: CostSummary,
    val modelBreakdown: List<ModelCostEntry>,
)

/**
 * UI state for the cost detail screen.
 *
 * @param T The type of content data.
 */
sealed interface CostDetailUiState<out T> {
    /** Data is being loaded from the bridge. */
    data object Loading : CostDetailUiState<Nothing>

    /**
     * Loading failed.
     *
     * @property detail Human-readable error message.
     */
    data class Error(
        val detail: String,
    ) : CostDetailUiState<Nothing>

    /**
     * Data loaded successfully.
     *
     * @param T Content data type.
     * @property data The loaded content.
     */
    data class Content<T>(
        val data: T,
    ) : CostDetailUiState<T>
}

/**
 * ViewModel for the cost detail screen.
 *
 * Loads cost summary data from [CostBridge], parses the per-model breakdown
 * JSON, and exposes a [CostDetailUiState] for the UI layer to collect.
 *
 * @param application Application context for accessing [ZeroClawApplication.costBridge].
 */
class CostDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val costBridge: CostBridge =
        (application as ZeroClawApplication).costBridge

    private val _uiState =
        MutableStateFlow<CostDetailUiState<CostDetailData>>(CostDetailUiState.Loading)

    /** Observable UI state for the cost detail screen. */
    val uiState: StateFlow<CostDetailUiState<CostDetailData>> = _uiState.asStateFlow()

    init {
        loadCostData()
    }

    /** Reloads cost data from the bridge. */
    fun refresh() {
        loadCostData()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadCostData() {
        _uiState.value = CostDetailUiState.Loading
        viewModelScope.launch {
            try {
                val summary = costBridge.getCostSummary()
                val breakdown = parseModelBreakdown(summary.modelBreakdownJson)
                _uiState.value =
                    CostDetailUiState.Content(
                        CostDetailData(
                            summary = summary,
                            modelBreakdown = breakdown,
                        ),
                    )
            } catch (e: Exception) {
                _uiState.value =
                    CostDetailUiState.Error(
                        e.message ?: "Failed to load cost data",
                    )
            }
        }
    }

    /** Constants for [CostDetailViewModel]. */
    companion object {
        /**
         * Parses the model breakdown JSON string into a list of [ModelCostEntry].
         *
         * Expected schema: a JSON array of objects, each with "model", "cost_usd",
         * "tokens", and "requests" fields. Malformed entries are skipped.
         *
         * @param json Raw JSON string from [CostSummary.modelBreakdownJson].
         * @return Parsed list, or empty if the JSON is invalid.
         */
        @Suppress("TooGenericExceptionCaught")
        internal fun parseModelBreakdown(json: String): List<ModelCostEntry> =
            try {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        add(
                            ModelCostEntry(
                                model = obj.optString("model", "unknown"),
                                costUsd = obj.optDouble("cost_usd", 0.0),
                                tokens = obj.optLong("tokens", 0),
                                requests = obj.optInt("requests", 0),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                tryParseObjectBreakdown(json)
            }

        /**
         * Fallback parser when the breakdown JSON is a map keyed by model name.
         *
         * Expected schema: `{"model_name": {"cost_usd": N, "tokens": N, "requests": N}}`.
         *
         * @param json Raw JSON string.
         * @return Parsed list, or empty if the JSON is invalid.
         */
        @Suppress("TooGenericExceptionCaught")
        private fun tryParseObjectBreakdown(json: String): List<ModelCostEntry> =
            try {
                val obj = JSONObject(json)
                buildList {
                    for (key in obj.keys()) {
                        val entry = obj.getJSONObject(key)
                        add(
                            ModelCostEntry(
                                model = key,
                                costUsd = entry.optDouble("cost_usd", 0.0),
                                tokens = entry.optLong("tokens", 0),
                                requests = entry.optInt("requests", 0),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
    }
}

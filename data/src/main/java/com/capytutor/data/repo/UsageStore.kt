package com.capytutor.data.repo

import com.capytutor.ai.UsageRecord
import com.capytutor.ai.UsageSink
import com.capytutor.data.db.CapyDatabase
import com.capytutor.data.db.ProviderUsageEntity
import com.capytutor.data.db.UsageTotal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Records every model call for the parent's "Folosire azi" screen (ProviderUsage). */
class UsageStore(private val db: CapyDatabase, private val scope: CoroutineScope) : UsageSink {

    override fun record(r: UsageRecord) {
        scope.launch {
            db.usage().insert(
                ProviderUsageEntity(
                    date = r.date.toString(), provider = r.provider, model = r.model, callType = r.callType,
                    requests = r.requests, inputTokens = r.inputTokens, outputTokens = r.outputTokens,
                    audioSeconds = 0, rateLimitedCount = if (r.rateLimited) 1 else 0, outcome = r.outcome,
                ),
            )
        }
    }

    fun observeToday(today: LocalDate = LocalDate.now()): Flow<List<UsageTotal>> = db.usage().observeDay(today.toString())
}

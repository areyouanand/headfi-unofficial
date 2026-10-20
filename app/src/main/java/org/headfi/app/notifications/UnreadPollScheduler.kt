package org.headfi.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.headfi.app.model.PollInterval
import java.util.concurrent.TimeUnit

/**
 * Single entry point for arming / disarming the background unread poller.
 * Safe to call repeatedly: the work is unique, so re-enabling with a new
 * interval just replaces the existing schedule.
 */
object UnreadPollScheduler {

    private const val TAG = "HeadFiUnreadPoll"
    const val UNIQUE_WORK_NAME = "headfi-unread-poll"

    fun sync(context: Context, enabled: Boolean, interval: PollInterval) {
        runCatching {
            val workManager = WorkManager.getInstance(context)

            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return@runCatching
            }

            val request = PeriodicWorkRequestBuilder<UnreadPollWorker>(
                interval.minutes.coerceAtLeast(15),
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .setInputData(workDataOf("interval" to interval.name))
                .addTag(UNIQUE_WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }.onFailure {
            Log.w(TAG, "Unable to schedule the unread poller", it)
        }
    }
}

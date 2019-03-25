package com.example.background.workers

import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture

object WorkManagerUtils {
    fun isAnyWorkPending(): Boolean {
        val allWorks = WorkManager.getInstance().getWorkInfosByTag("mz-signature-upload")
        Log.d("UpdateSignatureWorker", "${allWorks.get().size}")
        for(workInfo in allWorks.get()) {
            if(!workInfo.state.isFinished) {
                Log.d("UpdateSignatureWorker", "${workInfo.id} ${workInfo.state}")
                return true
            }
        }

        return false
    }
}
package com.example.background.workers

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class UpdateSignatureWorker(val appContext: Context, workerParams: WorkerParameters) : ListenableWorker(appContext, workerParams) {

    val TAG = "UpdateSignatureWorker"

    companion object {
        const val SIGNATURE_FILE_PATH = "SIGNATURE_FILE_PATH"
        const val AWS_BUCKET_NAME = "mz-mobile"
    }

    override fun startWork(): ListenableFuture<Result> {

        val transferUtility = AWSUtil.getTransferUtility(appContext)
        val signatureFile = File("/storage/emulated/0/Download/TeraYaarHoonMain.mp3")
        val signatureFileName = signatureFile.name

        return CallbackToFutureAdapter.getFuture {
            try {
                val transferObserver = transferUtility.upload(AWS_BUCKET_NAME, signatureFileName, signatureFile)
                val transferListener = getTransferListener(it, signatureFileName, transferObserver.id)
                transferObserver.setTransferListener(transferListener)
            } catch (e: Exception) {
                Log.e(TAG, "Exception in running this service")
            }
        }

    }

    private fun getTransferListener(completer: CallbackToFutureAdapter.Completer<Result>, signatureFileName: String, transferId: Int): TransferListener {

        return object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState) {
                Log.d(TAG, "New status $id $state $isStopped $runAttemptCount")

                if (state == TransferState.COMPLETED) {
                    completer.set(Result.success())
                    Log.d(TAG, "File successfully uploaded $signatureFileName")
                }

                // check if we need to put state == TransferState.PAUSED || state == TransferState.WAITING_FOR_NETWORK
                if(state == TransferState.FAILED) {
                    completer.set(Result.retry())
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                // Nothing to implement
                Log.d(TAG, "onProgressChanged $id $bytesCurrent / $bytesTotal")
            }

            override fun onError(id: Int, ex: Exception) {
                Log.e(TAG, "onError TransferListener for $signatureFileName")

                ex.printStackTrace()
                completer.set(Result.retry())
            }
        }
    }

    override fun onStopped() {
        super.onStopped()
        Log.d(TAG, "onStopped is called")
    }
}
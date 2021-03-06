package com.example.background.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobileconnectors.s3.transferutility.*
import com.amazonaws.services.s3.AmazonS3Client
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class UpdateSignatureWorker(val appContext: Context, workerParams: WorkerParameters) : ListenableWorker(appContext, workerParams) {

    private val transferUtility: TransferUtility = AWSUtil.getTransferUtility(appContext)
    private lateinit var transferListener: TransferListener
    private var transferObserver: TransferObserver? = null
    val sdPath = Environment.getExternalStorageDirectory().path
    // for Oreo emulator
//        val signatureFile = File("$sdPath/Music/TeraYaarHoonMain.mp3")

    // for emulator with version less than Oreo
//    val signatureFile = File("/storage/emulated/0/Music/TeraYaarHoonMain.mp3")

    // for Tenor E device
    val signatureFile = File("/storage/emulated/0/Download/TeraYaarHoonMain.mp3")

    companion object {
        const val TAG = "UpdateSignatureWorker"
        const val SIGNATURE_FILE_PATH = "SIGNATURE_FILE_PATH"
        const val AWS_BUCKET_NAME = "mz-mobile"
    }

    override fun startWork(): ListenableFuture<Result> {

        runService()
        AWSMobileClient.getInstance().initialize(appContext, object : Callback<UserStateDetails> {
            override fun onResult(result: UserStateDetails) {
                Log.i(TAG, "AWSMobileClient initialized. User State is " + result.userState)
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "Initialization error.", e)
            }
        })

        Log.d(TAG, "File exists ${signatureFile.exists()}")

//        val signatureFile = File("/storage/emulated/0/Download/TeraYaarHoonMain.mp3")
        val signatureFileName = signatureFile.name
        transferObserver = getTransferObserver()

        return CallbackToFutureAdapter.getFuture {

            //            it.set(Result.success())

            Log.d(TAG, "Reattempting at ${System.currentTimeMillis()}")

            try {
                transferListener = getTransferListener(it, signatureFileName, transferObserver!!)
                transferObserver!!.setTransferListener(transferListener)

                transferObserver?.let {
                    Log.d(TAG, "Transfer being uploaded is ${it.id}")
                }

            } catch (e: Exception) {
                e.printStackTrace()

                Log.e(TAG, "Exception in running this service ${e.message}")
                transferObserver?.let {
                    Log.d(TAG, "transferObserver is initialized")
                    transferUtility.cancel(it.id)
                    transferObserver?.cleanTransferListener()
                }

                it.set(Result.retry())
            }

        }

    }

    private fun getTransferObserver(): TransferObserver {

        val transferUtility = TransferUtility.builder()
                .context(applicationContext)
                .awsConfiguration(AWSMobileClient.getInstance().configuration)
                .defaultBucket(AWS_BUCKET_NAME)
                .s3Client(AmazonS3Client(AWSMobileClient.getInstance()))
                .build()

        return transferUtility.upload(
                signatureFile.name,
                signatureFile)
    }

    private fun getTransferListener(completer: CallbackToFutureAdapter.Completer<Result>, signatureFileName: String, transferObserver: TransferObserver): TransferListener {

        return object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState) {
                Log.d(TAG, "New status $id $state $isStopped $runAttemptCount")

                if (state == TransferState.COMPLETED) {
                    completer.set(Result.success())
                    Log.d(TAG, "File successfully uploaded $signatureFileName")
                    Log.d(TAG, "any work pending ${WorkManagerUtils.isAnyWorkPending()}")

                }

                // check if we need to put state == TransferState.PAUSED || state == TransferState.WAITING_FOR_NETWORK
                if (state == TransferState.FAILED) {
                    Log.d(TAG, "Inside state == TransferState.FAILED")
                    retry(completer, transferObserver)
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                // Nothing to implement
                Log.d(TAG, "onProgressChanged $id $bytesCurrent / $bytesTotal")
            }

            override fun onError(id: Int, ex: Exception) {
                Log.d(TAG, "onError(id: Int, ex: Exception)")
                Log.e(TAG, "onError TransferListener for id: $id fileName: $signatureFileName")

                ex.printStackTrace()
                retry(completer, transferObserver)
            }
        }
    }

    private fun runService() {

        val intent = Intent(applicationContext, TransferService::class.java)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val notificationId = inputData.getInt("notificationId", 0)

            Log.d(TAG, "NotificationId $notificationId")
            val notification = getNotification(notificationId)
            val notificationManagerCompat = NotificationManagerCompat.from(appContext)
            notificationManagerCompat.notify(notificationId, notification)
            intent.putExtra(TransferService.INTENT_KEY_NOTIFICATION, notification)
            intent.putExtra(TransferService.INTENT_KEY_NOTIFICATION_ID, notificationId)
            intent.putExtra(TransferService.INTENT_KEY_REMOVE_NOTIFICATION, false)

            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun getNotification(notificationId: Int): Notification {
        val channelId = createNotificationChannel()
        return Notification.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentText("Service Content Text")
                .setContentTitle("Service Content Title $notificationId")
                .build()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channel = NotificationChannel("com.example.background", "name", NotificationManager.IMPORTANCE_HIGH)
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        return channel.id
    }

    private fun retry(completer: CallbackToFutureAdapter.Completer<Result>, transferObserver: TransferObserver) {
//        transferUtility.cancel(transferObserver.id)
        Log.d(TAG, "Canceled ${transferObserver.id} new state ${transferObserver.state}")
        transferObserver.cleanTransferListener()

        completer.set(Result.retry())
    }

    override fun onStopped() {
        super.onStopped()
        Log.d(TAG, "onStopped is called")

    }
}
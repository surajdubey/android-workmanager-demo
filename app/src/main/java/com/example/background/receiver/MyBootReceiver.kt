package com.example.background.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService

class MyBootReceiver: BroadcastReceiver() {
    lateinit var context: Context
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("MyBootReceiver", "onReceive")
        intent?.let {
            if(it.action == Intent.ACTION_BOOT_COMPLETED) {
                context?.let {
                    runService(it)
                }
            }
        }
    }

    private fun runService(applicationContext: Context) {

        val intent = Intent(applicationContext, TransferService::class.java)

        /*

        Notification notification = new NotificationCompat.Builder(this, "HIGH")
                .setContentText("Service Content Text")
                .setContentTitle("Service Content Title")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
*/

        val notification = getNotification(applicationContext)
        intent.putExtra(TransferService.INTENT_KEY_NOTIFICATION, notification)
        intent.putExtra(TransferService.INTENT_KEY_NOTIFICATION_ID, 123)
        intent.putExtra(TransferService.INTENT_KEY_REMOVE_NOTIFICATION, true)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }

    }

    private fun getNotification(applicationContext: Context): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = createNotificationChannel(applicationContext)
            return Notification.Builder(applicationContext, channelId)
                    .setContentText("Service Content Text")
                    .setContentTitle("Service Content Title")
                    .build()
        }

        return Notification.Builder(applicationContext)
                .setContentText("Service Content Text")
                .setContentTitle("Service Content Title")
                .build()

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel(applicationContext: Context): String {
        val channel = NotificationChannel("com.example.background", "name", NotificationManager.IMPORTANCE_HIGH)
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        return channel.id
    }

}
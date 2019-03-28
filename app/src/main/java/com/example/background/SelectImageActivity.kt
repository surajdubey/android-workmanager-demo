/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.background

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.work.*
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.mobile.client.Callback
import com.amazonaws.mobile.client.UserStateDetails
import com.amazonaws.mobileconnectors.s3.transferutility.*
import com.amazonaws.services.s3.AmazonS3Client
import com.example.background.workers.UpdateSignatureWorker
import com.example.background.workers.WorkManagerUtils
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class SelectImageActivity : AppCompatActivity() {

    private var mPermissionRequestCount: Int = 0

    private val notification: Notification
        @RequiresApi(api = Build.VERSION_CODES.O)
        get() {
            val channelId = createNotificationChannel()
            return Notification.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.star_on)
                    .setContentText("Service Content Text")
                    .setContentTitle("Service Content Title")
                    .build()

        }


    private val notificationId: Int
        get() {
            val preferences = getSharedPreferences("signature", Context.MODE_PRIVATE)
            val notificationId = preferences.getInt("signature", 100)

            preferences.edit().putInt("signature", notificationId + 1).apply()
            return notificationId
        }

    private val transferObserver: TransferObserver
        get() {


            val transferUtility = TransferUtility.builder()
                    .context(applicationContext)
                    .awsConfiguration(AWSMobileClient.getInstance().configuration)
                    .defaultBucket("mz-mobile")
                    .s3Client(AmazonS3Client(AWSMobileClient.getInstance()))
                    .build()

            val file = File("/storage/emulated/0/Download/TeraYaarHoonMain.mp3")
            return transferUtility.upload(
                    file.name,
                    file)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select)

        if (savedInstanceState != null) {
            mPermissionRequestCount = savedInstanceState.getInt(KEY_PERMISSIONS_REQUEST_COUNT, 0)
        }

        // Make sure the app has correct permissions to run
        requestPermissionsIfNecessary()

        runService()

        // Create request to get image from filesystem when button clicked
        findViewById<View>(R.id.selectImage).setOnClickListener { view ->
            normalUpload();
//            upload()
            /*            Intent chooseIntent = new Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(chooseIntent, REQUEST_CODE_IMAGE);*/
        }
    }

    private fun runService() {

        val intent = Intent(applicationContext, TransferService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationId = notificationId

            val notification = notification
            val notificationManagerCompat = NotificationManagerCompat.from(this)
            notificationManagerCompat.notify(notificationId, notification)

            intent.putExtra(TransferService.INTENT_KEY_NOTIFICATION, notification)
            intent.putExtra(TransferService.INTENT_KEY_NOTIFICATION_ID, notificationId)
            intent.putExtra(TransferService.INTENT_KEY_REMOVE_NOTIFICATION, true)
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channel = NotificationChannel("com.example.background", "name", NotificationManager.IMPORTANCE_HIGH)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        return channel.id
    }

    private fun upload() {

        val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val inputData = Data.Builder()
                .putInt("notificationId", notificationId)
                .build()

        val workRequest = OneTimeWorkRequest.Builder(UpdateSignatureWorker::class.java)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 11, TimeUnit.SECONDS)
                .addTag("mz-signature-upload")
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()

        //        runService();
        WorkManager.getInstance().enqueue(workRequest)

    }

    private fun normalUpload() {
        AWSMobileClient.getInstance().initialize(applicationContext, object : Callback<UserStateDetails> {
            override fun onResult(result: UserStateDetails) {
                Log.i(TAG, "AWSMobileClient initialized. User State is " + result.userState)
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "Initialization error.", e)
            }
        })

        val uploadObserver = transferObserver


        // Attach a listener to the observer to get state update and progress notifications
        uploadObserver.setTransferListener(object : TransferListener {

            override fun onStateChanged(id: Int, state: TransferState) {
                Log.d(UpdateSignatureWorker.TAG, "New status $id $state")

                if (state == TransferState.COMPLETED) {
                    Log.d(UpdateSignatureWorker.TAG, "File successfully uploaded")
                    Log.d(UpdateSignatureWorker.TAG, "any work pending ${WorkManagerUtils.isAnyWorkPending()}")

                }

                // check if we need to put state == TransferState.PAUSED || state == TransferState.WAITING_FOR_NETWORK
                if (state == TransferState.FAILED) {
                    Log.d(UpdateSignatureWorker.TAG, "Inside state == TransferState.FAILED")
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                // Nothing to implement
                Log.d(UpdateSignatureWorker.TAG, "onProgressChanged $id $bytesCurrent / $bytesTotal")
            }

            override fun onError(id: Int, ex: Exception) {
                Log.d(UpdateSignatureWorker.TAG, "onError(id: Int, ex: Exception)")
                Log.e(UpdateSignatureWorker.TAG, "onError TransferListener for id: $id ")

                ex.printStackTrace()
            }
        })

    }

    /**
     * Save the permission request count on a rotate
     */

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PERMISSIONS_REQUEST_COUNT, mPermissionRequestCount)
    }

    /**
     * Request permissions twice - if the user denies twice then show a toast about how to update
     * the permission for storage. Also disable the button if we don't have access to pictures on
     * the device.
     */
    private fun requestPermissionsIfNecessary() {
        if (!checkAllPermissions()) {
            if (mPermissionRequestCount < MAX_NUMBER_REQUEST_PERMISSIONS) {
                mPermissionRequestCount += 1
                ActivityCompat.requestPermissions(
                        this,
                        sPermissions.toTypedArray(),
                        REQUEST_CODE_PERMISSIONS)
            } else {
                Toast.makeText(this, R.string.set_permissions_in_settings,
                        Toast.LENGTH_LONG).show()
                findViewById<View>(R.id.selectImage).setEnabled(false)
            }
        }
    }

    private fun checkAllPermissions(): Boolean {
        var hasPermissions = true
        for (permission in sPermissions) {
            hasPermissions = hasPermissions and (ContextCompat.checkSelfPermission(
                    this, permission) == PackageManager.PERMISSION_GRANTED)
        }
        return hasPermissions
    }

    /**
     * Permission Checking
     */

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            requestPermissionsIfNecessary() // no-op if permissions are granted already.
        }
    }

    /**
     * Image Selection
     */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_IMAGE -> handleImageRequestResult(data!!)
                else -> Log.d(TAG, "Unknown request code.")
            }
        } else {
            Log.e(TAG, String.format("Unexpected Result code %s", resultCode))
        }
    }

    private fun handleImageRequestResult(data: Intent) {
        var imageUri: Uri? = null
        if (data.clipData != null) {
            imageUri = data.clipData!!.getItemAt(0).uri
        } else if (data.data != null) {
            imageUri = data.data
        }

        if (imageUri == null) {
            Log.e(TAG, "Invalid input image Uri.")
            return
        }

        val filterIntent = Intent(this, BlurActivity::class.java)
        filterIntent.putExtra(Constants.KEY_IMAGE_URI, imageUri.toString())
        startActivity(filterIntent)
    }

    companion object {

        private val TAG = "UpdateSignatureWorker"

        private val REQUEST_CODE_IMAGE = 100
        private val REQUEST_CODE_PERMISSIONS = 101

        private val KEY_PERMISSIONS_REQUEST_COUNT = "KEY_PERMISSIONS_REQUEST_COUNT"
        private val MAX_NUMBER_REQUEST_PERMISSIONS = 2

        private val sPermissions = Arrays.asList(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

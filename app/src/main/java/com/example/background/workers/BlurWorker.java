package com.example.background.workers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.example.background.R;

import java.io.FileNotFoundException;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BlurWorker extends Worker {
    public BlurWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParameters) {
        super(appContext, workerParameters);

    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        Bitmap picture = BitmapFactory.decodeResource(context.getResources(), R.drawable.test);

        Bitmap output = WorkerUtils.blurBitmap(picture, context);

        try {
            Uri outputUri = WorkerUtils.writeBitmapToFile(context, output);
            WorkerUtils.makeStatusNotification("Output is " + outputUri.toString(), context);

            return Result.retry();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}

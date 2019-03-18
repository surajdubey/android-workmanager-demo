package com.example.background.workers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.example.background.Constants;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.UUID;

import androidx.annotation.Nullable;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import static android.support.constraint.Constraints.TAG;

public class CallbackWorker extends ListenableWorker {
    /**
     * @param appContext   The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */

    private ListenableFuture<Result> mFuture;
    private TransferUtility mTransferUtility;
    public CallbackWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        mTransferUtility = new TransferUtility(AWSUtil.getS3Client(getApplicationContext()), getApplicationContext());
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {

        // call aws upload here

        String name = "/data/data/com.example.background/files/blur_filter_outputs/blur-filter-output-71ba0d37-01f3-4dd3-8ebc-4721d8c9ee6b.png";
        File outputDir = new File(getApplicationContext().getFilesDir(), Constants.OUTPUT_PATH);
        File file = new File(name);

        return CallbackToFutureAdapter.getFuture(new CallbackToFutureAdapter.Resolver<Result>() {
            @Nullable
            @Override
            public Object attachCompleter(@androidx.annotation.NonNull CallbackToFutureAdapter.Completer<Result> completer) throws Exception {
                    Log.d("WorkManager", "Retrying at " + System.currentTimeMillis());

                    TransferListener transferListener = getTransferListener(completer);
                    TransferObserver transferObserver = mTransferUtility.upload("mz-mobile", file.getName(), file);

                    transferObserver.setTransferListener(transferListener);

                    return transferListener;
            }
        });
    }

    private TransferListener getTransferListener(CallbackToFutureAdapter.Completer<Result> completer) {

        TransferListener transferListener = new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                Log.d(TAG, "onStateChanged TransferListener");
                if (state == TransferState.COMPLETED) {
                    completer.set(Result.success());
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                Log.d(TAG, "onProgressChanged TransferListener");
            }

            @Override
            public void onError(int id, Exception ex) {
                Log.d(TAG, "onError TransferListener");

                ex.printStackTrace();
                completer.set(Result.retry());
            }
        };

        return transferListener;
    }
}

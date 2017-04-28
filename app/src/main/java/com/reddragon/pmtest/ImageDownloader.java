package com.reddragon.pmtest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by frysingg on 4/28/2017.
 */

class ImageDownloader<T> extends HandlerThread {
    private static final String TAG = "ImageDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;

    private boolean mHasQuit = false;
    private Handler mRequestHandler;
    private JSONDownloader mJSONDownloder;
    private ConcurrentMap<T,String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ImageDownloadListener<T> mImageDownloadListener;

    interface ImageDownloadListener<T> {
        void onImageDownloaded(T target, Bitmap bitmap);
    }

    void setImageDownloadListener(ImageDownloadListener<T> listener) {
        mImageDownloadListener = listener;
    }

    ImageDownloader(Handler responseHandler, JSONDownloader mJSONDownloader) {
        super(TAG);
        mResponseHandler = responseHandler;
        this.mJSONDownloder = mJSONDownloader;
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.d(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                }
            }
        };
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    /**
     * Requests a fetch of the image
     */
    void queueImage(T target, String url) {

        if (url == null) {
            Log.d(TAG, "Got a null URL, will use default image");
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }

    }

    void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }

    private void handleRequest(final T target) {
        try {
            final String url = mRequestMap.get(target);

            if (url == null) {
                Log.d(TAG,"Found a null URL associated with target: " + target);
                mRequestMap.remove(target);
                return;
            }
            // Get the image bytes pointed to by the URL
            byte[] bitmapBytes = mJSONDownloder.getUrlBytes(url);
            // Construct the bitmap from the bytes
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.d(TAG, "Bitmap created");
            // Post to queue managed by main thread's looper
            mResponseHandler.post(new Runnable() {
                public void run() {
                    String urlInMap = mRequestMap.get(target);
                    mRequestMap.remove(target);
                    if ( mHasQuit || urlInMap == null || !(urlInMap.equals(url)) ) {
                        // There might be an issue if user is scrolling up/down and due to
                        // re-use of holders, the url assigned has changed.
                        Log.d(TAG, "URL in map: " +
                                url +
                                " changed its value to: " +
                                urlInMap +
                                " before I could call onImageDownloaded.");
                        return;
                    }

                    mImageDownloadListener.onImageDownloaded(target, bitmap);
                }
            });
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }
    }

}

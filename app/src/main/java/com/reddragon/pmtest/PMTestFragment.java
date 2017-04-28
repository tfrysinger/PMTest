package com.reddragon.pmtest;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by frysingg on 4/28/2017.
 */

public class PMTestFragment extends Fragment {
    private Drawable mPatronus;
    private ImageDownloader mImageDownloader;
    private JSONDownloader mJSONDownloader = new JSONDownloader();
    private int mGridColumns;
    private ProgressBar mProgressBar;
    private GridLayoutManager mGridLayoutManager;
    private RecyclerView mImageRecyclerView;
    private List<ContentItem> mItems = new ArrayList<>();
    private final int DEFAULT_COL_WIDTH = 400;
    private final String TAG = "PMTestFragment";
    // Return instance of class
    public static PMTestFragment newInstance() {
        return new PMTestFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        // Default image in case a content item has no URL
        //noinspection deprecation
        mPatronus = getResources().getDrawable(R.drawable.hp_patronus);

        // Because we are creating this Handler on the main thread, it will attach itself to the Looper
        // for the main thread - it's the only thread where this kind of binding happens automagically.
        Handler responseHandler = new Handler();
        // the image downloader is a separate thread used to fetch the various images
        // that we have URLs to based on the JSON data
        mImageDownloader = new ImageDownloader<>(responseHandler, mJSONDownloader);
        mImageDownloader.setImageDownloadListener(
                new ImageDownloader.ImageDownloadListener<ContentHolder>() {
                    @Override
                    // Called by downloader when she's downloaded the associated image
                    public void onImageDownloaded(ContentHolder target, Bitmap bitmap) {
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                        target.bindContent(drawable, null);
                    }
                }
        );
        mImageDownloader.start();
        // Force the prep to happen so we can safely initialize in ImageDownloader
        mImageDownloader.getLooper();
        Log.d(TAG, "Background thread started");
    }


    /**
     * Make sure as we leave we stop the downloader thread
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mImageDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }


    /**
     * Make sure if view is destroyed we don't leave hanging requests
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mImageDownloader.clearQueue();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_pmtest, container, false);
        mImageRecyclerView = (RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mImageRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    mImageRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    //noinspection deprecation
                    mImageRecyclerView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
                // Adjust the columns to fit based on width of RecyclerView
                int width = mImageRecyclerView.getWidth();
                mGridColumns = width / DEFAULT_COL_WIDTH;
                mGridLayoutManager = new GridLayoutManager(getActivity(),mGridColumns);
                mImageRecyclerView.setLayoutManager(mGridLayoutManager);
                mProgressBar = (ProgressBar) v.findViewById(R.id.parse_progress);
                initAdapter();
                new RetrieveItemsTask().execute();
            }
        });
        return v;
    }

    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressBar.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void initAdapter() {
        // Since we are populating items from a background thread, we can only update our
        // adapter if this fragment is attached.
        if ( isAdded() ) {
            mImageRecyclerView.setAdapter(new ContentAdapter(mItems));
        }
    }

    private class ContentAdapter extends RecyclerView.Adapter<ContentHolder> {

        private List<ContentItem> mContentItems;

        private ContentAdapter(List<ContentItem> contentItems) {
            mContentItems = contentItems;
        }

        @Override
        public ContentHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.list_item_contentdata, parent, false);
            return new ContentHolder(view);
        }

        @Override
        public void onBindViewHolder(ContentHolder holder, int position) {
            ContentItem item = mContentItems.get(position);
            //Drawable placeholder
            holder.bindContent(mPatronus, item);
            // Now place a request to download this particular image
            mImageDownloader.queueImage(holder, item.getImageUrl());
        }

        @Override
        public int getItemCount() {
            return mContentItems.size();
        }
    }

    private class ContentHolder extends RecyclerView.ViewHolder {
        private ImageView mImageView;
        private TextView mTitleView;
        private TextView mAuthorView;
        private ContentItem mItem;

        private ContentHolder(View itemView) {
            super(itemView);
            mImageView = (ImageView)itemView.findViewById(R.id.list_item_image_view);
            mTitleView = (TextView) itemView.findViewById(R.id.list_item_content_title_text_view);
            mAuthorView = (TextView) itemView.findViewById(R.id.list_item_content_author_text_view);
        }

        private void bindContent(Drawable drawable, ContentItem item){
            if ( item != null) {
                mItem = item;
            }
            mImageView.setImageDrawable(drawable);
            mTitleView.setText((mItem == null || mItem.getTitle() == null) ?
                                   getResources().getText(R.string.title_unknown) :
                                   mItem.getTitle());
            mAuthorView.setText((mItem == null || mItem.getAuthor() == null) ?
                                   getResources().getText(R.string.author_unknown) :
                                   mItem.getAuthor());
        }

    }

    /**
     * First argument is what needs to be passed to doInBackground (or Void if nothing
     * needed in my implementation), second is what needs to be passed to onProgressUpdate
     * when 'publishProgress()' is called from doInBackground,
     * third is what needs to be passed to onPostExecute.
     *
     * See also using a Loader instead of AsyncTask
     *
     */
    private class RetrieveItemsTask extends AsyncTask<Void,Void,List<ContentItem>> {
        @Override
        protected void onPreExecute() {
            showProgress(true);
        }
        @Override
        protected List<ContentItem> doInBackground(Void...params) {
            return mJSONDownloader.retrieveItems(getResources().getString(R.string.json_url));
        }

        @Override
        protected void onPostExecute(List<ContentItem> items) {
            mItems.addAll(items);
            initAdapter();
            showProgress(false);
        }
    }
}

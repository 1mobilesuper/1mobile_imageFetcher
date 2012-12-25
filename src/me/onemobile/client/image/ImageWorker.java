/*
 * Copyright (C) 2012 The Android Open Source Project
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

package me.onemobile.client.image;

import java.io.File;
import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.ImageView;

/**
 * This class wraps up completing some arbitrary long running work when loading a bitmap to an
 * ImageView. It handles things like using a memory and disk cache, running the work in a background
 * thread and setting a placeholder image.
 */
public abstract class ImageWorker {
    private static final int FADE_IN_TIME = 200;

    protected ImageCache mImageCache;
    private Bitmap mLoadingBitmap;
    private boolean mFadeInBitmap = true;
    private boolean mExitTasksEarly = false;

    protected Context mContext;

    protected ImageWorker(Context context) {
        mContext = context;
    }

    /**
     * Load an image specified by the data parameter into an ImageView (override
     * {@link ImageWorker#processBitmap(Object)} to define the processing logic). A memory and disk
     * cache will be used if an {@link ImageCache} has been set using
     * {@link ImageWorker#setImageCache(ImageCache)}. If the image is found in the memory cache, it
     * is set immediately, otherwise an {@link AsyncTask} will be created to asynchronously load the
     * bitmap.
     *
     * @param data The URL of the image to download.
     * @param imageView The ImageView to bind the downloaded image to.
     */
    public void loadImage(Object data, ImageView imageView, int reqWidth, int reqHeight) {
    	String uri = String.valueOf(data);
    	if (uri == null || uri.length() == 0) {
    		return;
    	}
        Bitmap bitmap = null;
        if (mImageCache != null) {
            bitmap = mImageCache.getBitmapFromMemCache(uri);
        }

        if (bitmap != null) {
            // Bitmap found in memory cache
        	if (callback != null) {
				if (!doCallbackOnly) {
					imageView.setImageBitmap(bitmap);
				}
				callback.onFinished(imageView, bitmap);
			} else {
				imageView.setImageBitmap(bitmap);
			}
		} else if (cancelPotentialWork(data, imageView)) {
			BitmapWorkerTask task = new BitmapWorkerTask(imageView, reqWidth, reqHeight);
			AsyncDrawable asyncDrawable = new AsyncDrawable(mContext.getResources(), mLoadingBitmap, task);
			imageView.setImageDrawable(asyncDrawable);
			try {
				task.execute(data);
			} catch (Exception e) {
				asyncDrawable = null;
				task.cancel(true);
				task = null;
			}
		}
    }

    /**
     * Set placeholder bitmap that shows when the the background thread is running.
     *
     * @param bitmap
     */
    public void setLoadingImage(Bitmap bitmap) {
        mLoadingBitmap = bitmap;
    }

    /**
     * Set placeholder bitmap that shows when the the background thread is running.
     *
     * @param resId
     */
    public void setLoadingImage(int resId) {
        mLoadingBitmap = BitmapFactory.decodeResource(mContext.getResources(), resId);
    }

    /**
     * Set the {@link ImageCache} object to use with this ImageWorker.
     *
     * @param cacheCallback
     */
    public void setImageCache(ImageCache cacheCallback) {
        mImageCache = cacheCallback;
    }

    public ImageCache getImageCache() {
        return mImageCache;
    }

    /**
     * If set to true, the image will fade-in once it has been loaded by the background thread.
     *
     * @param fadeIn
     */
    public void setImageFadeIn(boolean fadeIn) {
        mFadeInBitmap = fadeIn;
    }

    public void setExitTasksEarly(boolean exitTasksEarly) {
        mExitTasksEarly = exitTasksEarly;
    }

    /**
     * Subclasses should override this to define any processing or work that must happen to produce
     * the final bitmap. This will be executed in a background thread and be long running. For
     * example, you could resize a large bitmap here, or pull down an image from the network.
     *
     * @param data The data to identify which image to process, as provided by
     *            {@link ImageWorker#loadImage(Object, ImageView)}
     * @return The processed bitmap
     */
    protected abstract Bitmap processBitmap(Object data, int reqWidth, int reqHeight);

    public static void cancelWork(ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            bitmapWorkerTask.cancel(true);
        }
    }

    /**
     * Returns true if the current work has been canceled or if there was no work in
     * progress on this image view.
     * Returns false if the work in progress deals with the same data. The work is not
     * stopped in that case.
     */
    public static boolean cancelPotentialWork(Object data, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.data;
            if (bitmapData == null || !bitmapData.equals(data)) {
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress.
                return false;
            }
        }
        return true;
    }

    /**
     * @param imageView Any imageView
     * @return Retrieve the currently active work task (if any) associated with this imageView.
     * null if there is no such task.
     */
    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    /**
     * The actual AsyncTask that will asynchronously process the image.
     */
    private class BitmapWorkerTask extends CustomAsyncTask<Object, Void, Bitmap> {
        private Object data;
        private final WeakReference<ImageView> imageViewReference;
        int reqWidth;
        int reqHeight;
        
        public BitmapWorkerTask(ImageView imageView, int reqWidth, int reqHeight) {
            imageViewReference = new WeakReference<ImageView>(imageView);
            this.reqWidth = reqWidth;
            this.reqHeight = reqHeight;
        }
        
        @Override
        protected boolean isExeImmediately(Object... params) {
        	data = params[0];
			final String dataString = String.valueOf(data);

			Uri imgUri = Uri.parse(dataString);
			if (imgUri != null && imgUri.getScheme() != null && imgUri.getScheme().equalsIgnoreCase("pkg")) {
				return true;
			} else {
				if (mImageCache != null && !isCancelled() && getAttachedImageView() != null && !mExitTasksEarly) {
					String cacheFile = mImageCache.getCacheFile(dataString);
					if (cacheFile != null && cacheFile.length() > 0) {
						return new File(cacheFile).exists();
					}
				}
			}
			return false;
        }

        /**
         * Background processing.
         */
        @Override
		protected Bitmap doInBackground(Object... params) {
			try {
//				data = params[0];
				final String dataString = String.valueOf(data);
				Bitmap bitmap = null;

				Uri imgUri = Uri.parse(dataString);
				if (imgUri != null && imgUri.getScheme() != null && imgUri.getScheme().equalsIgnoreCase("pkg")) {
					bitmap = getApplicationIcon(mContext, imgUri.getHost());
				} else {

					// If the image cache is available and this task has not
					// been cancelled by another
					// thread and the ImageView that was originally bound to
					// this task is still bound back
					// to this task and our "exit early" flag is not set then
					// try and fetch the bitmap from
					// the cache
					if (mImageCache != null && !isCancelled() && getAttachedImageView() != null && !mExitTasksEarly) {
						String cacheFile = mImageCache.getCacheFile(dataString);
						if (cacheFile != null && cacheFile.length() > 0) {
							bitmap = processBitmapByFile(cacheFile, reqWidth, reqHeight);
							if (bitmap == null) {
								mImageCache.deleteCacheFile(dataString);
							}
						}
					}

					// If the bitmap was not found in the cache and this task
					// has not been cancelled by
					// another thread and the ImageView that was originally
					// bound to this task is still
					// bound back to this task and our "exit early" flag is not
					// set, then call the main
					// process method (as implemented by a subclass)
					if (bitmap == null && !isCancelled() && getAttachedImageView() != null && !mExitTasksEarly) {
						bitmap = processBitmap(params[0], reqWidth, reqHeight);
					}
				}

				// If the bitmap was processed and the image cache is available,
				// then add the processed
				// bitmap to the cache for future use. Note we don't check if
				// the task was cancelled
				// here, if it was, and the thread is still running, we may as
				// well add the processed
				// bitmap to our cache as it might be used again in the future
				if (bitmap != null && mImageCache != null) {
					mImageCache.addBitmapToCache(dataString, bitmap);
				}

				return bitmap;

			} catch (Exception e) {
				return null;
			}
		}

        /**
         * Once the image is processed, associates it to the imageView
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // if cancel was called on this task or the "exit early" flag is set then we're done
            if (isCancelled() || mExitTasksEarly) {
                bitmap = null;
            }

            final ImageView imageView = getAttachedImageView();
            if (bitmap != null && imageView != null) {
            	if (callback != null) {
					if (!doCallbackOnly) {
						setImageBitmap(imageView, bitmap);
					}
					callback.onFinished(imageView, bitmap);
				} else {
					setImageBitmap(imageView, bitmap);
				}
            }
        }

        /**
         * Returns the ImageView associated with this task as long as the ImageView's task still
         * points to this task as well. Returns null otherwise.
         */
        private ImageView getAttachedImageView() {
            final ImageView imageView = imageViewReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

            if (this == bitmapWorkerTask) {
                return imageView;
            }

            return null;
        }
        
    }

    /**
     * A custom Drawable that will be attached to the imageView while the work is in progress.
     * Contains a reference to the actual worker task, so that it can be stopped if a new binding is
     * required, and makes sure that only the last started worker process can bind its result,
     * independently of the finish order.
     */
    private static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);

            bitmapWorkerTaskReference =
                new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    /**
     * Called when the processing is complete and the final bitmap should be set on the ImageView.
     *
     * @param imageView
     * @param bitmap
     */
    private void setImageBitmap(ImageView imageView, Bitmap bitmap) {
        if (mFadeInBitmap) {
            // Transition drawable with a transparent drwabale and the final bitmap
            final TransitionDrawable td =
                    new TransitionDrawable(new Drawable[] {
                            new ColorDrawable(android.R.color.transparent),
                            new BitmapDrawable(mContext.getResources(), bitmap)
                    });
            // Set background to loading bitmap,It's stupid
           /* imageView.setBackgroundDrawable(
                    new BitmapDrawable(mContext.getResources(), mLoadingBitmap));*/

            imageView.setImageDrawable(td);
            td.startTransition(FADE_IN_TIME);
        } else {
            imageView.setImageBitmap(bitmap);
        }
    }

    protected abstract Bitmap processBitmapByFile(String file, int reqWidth, int reqHeight);
    
    
    /////////////////////////////////////////////////////////////////////////
    
    private Callback callback;
    private boolean doCallbackOnly = true;
    
	public interface Callback {
		public void onFinished(ImageView imageView, Bitmap bitmap);
	}
	
	/**
	 * If you want to get the result, implement <code>Callback</code>.
	 * 
	 * @param callback
	 * @param callbackOnly
	 *            true: just callback, false:show the image in the view and
	 *            callback.
	 */
	public void setCallback(Callback callback, boolean callbackOnly) {
		this.callback = callback;
		this.doCallbackOnly = callbackOnly;
	}

	
	
	public static Bitmap getApplicationIcon(Context ctx, String pkg) {
		try {
			Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
			return ((BitmapDrawable) d).getBitmap();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public void clearMemoryCache() {
		if (mImageCache != null) {
			mImageCache.cleanMemCache();
			mImageCache = null;
		}
		mLoadingBitmap = null;
	}
	
}

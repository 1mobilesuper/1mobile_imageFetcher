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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.FragmentActivity;

/**
 * A simple subclass of {@link ImageResizer} that fetches and resizes images fetched from a URL.
 */
public class ImageFetcher extends ImageResizer {
    private static final String TAG = "ImageFetcher";

    /**
     * Initialize providing a target image width and height for the processing images.
     *
     * @param context
     * @param imageWidth
     * @param imageHeight
     */
    public ImageFetcher(Context context, int imageWidth, int imageHeight) {
        super(context, imageWidth, imageHeight);
        init(context);
    }

    /**
     * Initialize providing a single target image size (used for both width and height);
     *
     * @param context
     * @param imageSize
     */
    public ImageFetcher(Context context, int imageSize) {
        super(context, imageSize);
        init(context);
    }
    
    /**
     * 
     * @param context
     */
    public ImageFetcher(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
    	if (context instanceof FragmentActivity) {
    		setImageCache(ImageCache.getInstance((FragmentActivity)context));
    	} else {
    		setImageCache(ImageCache.getInstance(context));
    	}
        checkConnection(context);
    }
   

    /**
     * Simple network connection check.
     *
     * @param context
     */
    private void checkConnection(Context context) {
        final ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
//            Toast.makeText(context, "No network connection found.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * The main process method, which will be called by the ImageWorker in the AsyncTask background
     * thread.
     *
     * @param data The data to load the bitmap, in this case, a regular http URL
     * @return The downloaded and resized bitmap
     */
    private Bitmap processBitmap(String data, int reqWidth, int reqHeight) {
        // Download a bitmap, write it to a file
        final File f = downloadBitmap(mContext, data);

        if (f != null) {
            // Return a sampled down version
            return decodeSampledBitmapFromFile(f.toString(), reqWidth, reqWidth, strictMode);
        }

        return null;
    }

    @Override
	protected Bitmap processBitmap(Object data, int reqWidth, int reqHeight) {
		return processBitmap(String.valueOf(data), reqWidth, reqHeight);
	}

    /**
     * Download a bitmap from a URL, write it to a disk and return the File pointer. This
     * implementation uses a simple disk cache.
     *
     * @param context The context to use
     * @param urlString The URL to fetch
     * @return A File pointing to the fetched bitmap
     */
	public static File downloadBitmap(Context context, String urlString) {
		final File cacheDir = DiskLruCache.getDiskCacheDir(context, ImageCache.CACHE_DIR_IMAGES);

		final DiskLruCache cache = DiskLruCache.openCache(context, cacheDir, ImageCache.DEFAULT_DISK_CACHE_SIZE);
		if (cache == null) {
			return null;
		}

		final File cacheFile = new File(cache.createFilePath(urlString));

		if (cache.containsKey(urlString)) {
			return cacheFile;
		}

		Utils.disableConnectionReuseIfNecessary();
		HttpURLConnection urlConnection = null;
		BufferedOutputStream out = null;

		try {
			final URL url = new URL(urlString);
			urlConnection = (HttpURLConnection) url.openConnection();
			final InputStream in = new BufferedInputStream(urlConnection.getInputStream(), Utils.IO_BUFFER_SIZE);
			out = new BufferedOutputStream(new FileOutputStream(cacheFile), Utils.IO_BUFFER_SIZE);

			int b;
			while ((b = in.read()) != -1) {
				out.write(b);
			}

			return cacheFile;

		} catch (final IOException e) {
			e.printStackTrace();
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
			if (out != null) {
				try {
					out.close();
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}
	
    /**
     * A sync method to get the image. </p>
     * 
     * @param url
     * @return
     */
    public Bitmap getImage(String url, int reqWidth, int reqHeight) {
        if (mImageCache == null) {
        	return null;
        }
        // from memory
        Bitmap bitmap = mImageCache.getBitmapFromMemCache(url);
        
        // from disk cache
        if (bitmap == null) {
        	String cacheFile = mImageCache.getCacheFile(url);
			if (cacheFile != null && cacheFile.length() > 0) {
				bitmap = processBitmapByFile(cacheFile, reqWidth, reqHeight);
			}
        }
        
        // download
    	if (bitmap == null) {
    		bitmap = processBitmap(url, reqWidth, reqHeight);
    	}
    	
    	if (bitmap != null) {
            mImageCache.addBitmapToCache(url, bitmap);
        }
    	return bitmap;
    }
    
    /**
     * Download image and return Bitmap
     * @param url
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public Bitmap downloadImageInCache(String url, int reqWidth, int reqHeight) {
    	return processBitmap(url, reqWidth, reqHeight);
    }
}

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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Environment;

/**
 * A simple disk LRU bitmap cache to illustrate how a disk cache would be used
 * for bitmap caching. A much more robust and efficient disk LRU cache solution
 * can be found in the ICS source code
 * (libcore/luni/src/main/java/libcore/io/DiskLruCache.java) and is preferable
 * to this simple implementation.
 */
public class DiskLruCache {
	private static final String TAG = "DiskLruCache";
	private static final String CACHE_FILENAME_PREFIX = "i_";
	private static final int MAX_REMOVALS = 6;
	private static final int INITIAL_CAPACITY = 32;
	private static final float LOAD_FACTOR = 0.75f;

	public static File mCacheDir;
	private int cacheSize = 0;
	private int cacheByteSize = 0;
	private final int maxCacheItemSize = 64; // 64 item default
	private long maxCacheByteSize = 1024 * 1024 * 5; // 5MB default
	private CompressFormat mCompressFormat = CompressFormat.PNG;
	private int mCompressQuality = 85;

	private final Map<String, String> mLinkedHashMap = Collections.synchronizedMap(new LinkedHashMap<String, String>(INITIAL_CAPACITY, LOAD_FACTOR, true));

	/**
	 * A filename filter to use to identify the cache filenames which have
	 * CACHE_FILENAME_PREFIX prepended.
	 */
	private static final FilenameFilter cacheFileFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String filename) {
			return filename.startsWith(CACHE_FILENAME_PREFIX);
		}
	};

	/**
	 * Used to fetch an instance of DiskLruCache.
	 * 
	 * @param context
	 * @param cacheDir
	 * @param maxByteSize
	 * @return
	 */
	public static DiskLruCache openCache(Context context, File cacheDir, int maxByteSize) {
		if (cacheDir == null) {
			return null;
		}
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}

		if (cacheDir.isDirectory() && cacheDir.canWrite() && Utils.getUsableSpace(cacheDir) > maxByteSize) {
			return new DiskLruCache(cacheDir, maxByteSize);
		}

		return null;
	}

	/**
	 * Constructor that should not be called directly, instead use
	 * {@link DiskLruCache#openCache(Context, File, long)} which runs some extra
	 * checks before creating a DiskLruCache instance.
	 * 
	 * @param cacheDir
	 * @param maxByteSize
	 */
	private DiskLruCache(File cacheDir, long maxByteSize) {
		if (mCacheDir == null) {
			mCacheDir = cacheDir;
		}
		maxCacheByteSize = maxByteSize;
	}

	/**
	 * Add a bitmap to the disk cache.
	 * 
	 * @param key
	 *            A unique identifier for the bitmap.
	 * @param data
	 *            The bitmap to store.
	 */
	public void put(String key, Bitmap data) {
		synchronized (mLinkedHashMap) {
			if (!mLinkedHashMap.containsKey(key) || mLinkedHashMap.get(key) == null) {
				try {
					final String file = createFilePath(mCacheDir, key);
					put(key, file);
					flushCache();
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void put(String key, String file) {
		mLinkedHashMap.put(key, file);
		cacheSize = mLinkedHashMap.size();
		cacheByteSize += new File(file).length();
	}

	/**
	 * Flush the cache, removing oldest entries if the total size is over the
	 * specified cache size. Note that this isn't keeping track of stale files
	 * in the cache directory that aren't in the HashMap. If the images and keys
	 * in the disk cache change often then they probably won't ever be removed.
	 */
	private void flushCache() {
		Entry<String, String> eldestEntry;
		File eldestFile;
		long eldestFileSize;
		int count = 0;

		while (count < MAX_REMOVALS && (cacheSize > maxCacheItemSize || cacheByteSize > maxCacheByteSize)) {
			eldestEntry = mLinkedHashMap.entrySet().iterator().next();
			eldestFile = new File(eldestEntry.getValue());
			eldestFileSize = eldestFile.length();
			mLinkedHashMap.remove(eldestEntry.getKey());
			eldestFile.delete();
			cacheSize = mLinkedHashMap.size();
			cacheByteSize -= eldestFileSize;
			count++;
		}
	}

	/**
	 * Get an image from the disk cache.
	 * 
	 * @param key
	 *            The unique identifier for the bitmap
	 * @return The bitmap or null if not found
	 */
	public Bitmap get(String key) {
		synchronized (mLinkedHashMap) {
			final String file = mLinkedHashMap.get(key);
			if (file != null) {
				return BitmapFactory.decodeFile(file);
			} else {
				final String existingFile = createFilePath(mCacheDir, key);
				if (new File(existingFile).exists()) {
					put(key, existingFile);
					return BitmapFactory.decodeFile(existingFile);
				}
			}
			return null;
		}
	}

	public String getCacheFile(String key) {
		final String file = mLinkedHashMap.get(key);
		if (file != null) {
			return file;
		} else {
			final String existingFile = createFilePath(mCacheDir, key);
			if (new File(existingFile).exists()) {
				put(key, existingFile);
				return existingFile;
			}
		}
		return "";
	}

	/**
	 * Checks if a specific key exist in the cache.
	 * 
	 * @param key
	 *            The unique identifier for the bitmap
	 * @return true if found, false otherwise
	 */
	public boolean containsKey(String key) {
		// See if the key is in our HashMap
		if (mLinkedHashMap.containsKey(key)) {
			return true;
		}

		// Now check if there's an actual file that exists based on the key
		final String existingFile = createFilePath(mCacheDir, key);
		if (new File(existingFile).exists()) {
			// File found, add it to the HashMap for future use
			put(key, existingFile);
			return true;
		}
		return false;
	}

	public void deleteCache(String key) {
		if (mLinkedHashMap.containsKey(key)) {
			mLinkedHashMap.remove(key);
		}
		final String existingFile = createFilePath(mCacheDir, key);
		File cacheFile = new File(existingFile);
		if (cacheFile.exists()) {
			long fileSize = cacheFile.length();
			if (cacheFile.delete()) {
				cacheByteSize -= fileSize;
			}
		}
	}

	/**
	 * Removes all disk cache entries from this instance cache dir
	 */
	public void clearCache() {
		DiskLruCache.clearCache(mCacheDir);
	}

	public void clearLinkedHashMap() {
		if (mLinkedHashMap != null) {
			mLinkedHashMap.clear();
		}
	}

	/**
	 * Removes all disk cache entries from the application cache directory in
	 * the uniqueName sub-directory.
	 * 
	 * @param context
	 *            The context to use
	 * @param uniqueName
	 *            A unique cache directory name to append to the app cache
	 *            directory
	 */
	public static void clearCache(Context context, String uniqueName) {
		File cacheDir = getDiskCacheDir(context, uniqueName);
		if (cacheDir != null && cacheDir.exists()) {
			clearCache(cacheDir);
		}
	}

	/**
	 * Removes all disk cache entries from the given directory. This should not
	 * be called directly, call {@link DiskLruCache#clearCache(Context, String)}
	 * or {@link DiskLruCache#clearCache()} instead.
	 * 
	 * @param cacheDir
	 *            The directory to remove the cache files from
	 */
	private static void clearCache(File cacheDir) {
		if (cacheDir == null) {
			return;
		}
		final File[] files = cacheDir.listFiles(cacheFileFilter);
		for (int i = 0; i < files.length; i++) {
			files[i].delete();
		}
	}

	/**
	 * Get a usable cache directory (external if available, internal otherwise).
	 * 
	 * @param context
	 *            The context to use
	 * @param uniqueName
	 *            A unique directory name to append to the cache dir
	 * @return The cache dir
	 */
	public static File getDiskCacheDir(Context context, String uniqueName) {
		// Check if media is mounted or storage is built-in, if so, try and use
		// external cache dir
		// otherwise use internal cache dir
		if (context == null || uniqueName == null) {
			return null;
		}
		File cachePath;
		try {
			String exterState = Environment.getExternalStorageState();
			cachePath = exterState.equalsIgnoreCase(Environment.MEDIA_MOUNTED)
					|| (!Utils.isExternalStorageRemovable() && !exterState.equalsIgnoreCase(Environment.MEDIA_SHARED)) ? Utils.getExternalCacheDir(context)
					: context.getCacheDir();
		} catch (Exception e) {
			return null;
		}

		return new File(cachePath, uniqueName);
	}

	/**
	 * Creates a constant cache file path given a target cache directory and an
	 * image key.
	 * 
	 * @param cacheDir
	 * @param key
	 * @return
	 */
	public static String createFilePath(File cacheDir, String key) {
		if (cacheDir == null) {
			return "";
		}
		try {
			return cacheDir.getAbsolutePath() + File.separator + CACHE_FILENAME_PREFIX + key.hashCode();
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Create a constant cache file path using the current cache directory and
	 * an image key.
	 * 
	 * @param key
	 * @return
	 */
	public String createFilePath(String key) {
		return createFilePath(mCacheDir, key);
	}

	/**
	 * Sets the target compression format and quality for images written to the
	 * disk cache.
	 * 
	 * @param compressFormat
	 * @param quality
	 */
	public void setCompressParams(CompressFormat compressFormat, int quality) {
		mCompressFormat = compressFormat;
		mCompressQuality = quality;
	}

	/**
	 * Writes a bitmap to a file. Call
	 * {@link DiskLruCache#setCompressParams(CompressFormat, int)} first to set
	 * the target bitmap compression and format.
	 * 
	 * @param bitmap
	 * @param file
	 * @return
	 */
	private boolean writeBitmapToFile(Bitmap bitmap, String file) throws IOException, FileNotFoundException {

		OutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(file), Utils.IO_BUFFER_SIZE);
			return bitmap.compress(mCompressFormat, mCompressQuality, out);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}
}

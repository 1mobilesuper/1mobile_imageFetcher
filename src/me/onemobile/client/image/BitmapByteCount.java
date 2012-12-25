package me.onemobile.client.image;

import android.annotation.TargetApi;
import android.graphics.Bitmap;

public class BitmapByteCount {

	@TargetApi(12)
	public int getByteCount(Bitmap bitmap) {
		return bitmap.getByteCount();
	}
}

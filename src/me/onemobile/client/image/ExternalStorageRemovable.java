package me.onemobile.client.image;

import android.annotation.TargetApi;
import android.os.Environment;

public class ExternalStorageRemovable {

	@TargetApi(9)
	public boolean get() {
		try {
			return Environment.isExternalStorageRemovable();
		} catch (NoSuchMethodError e) {
			return false;
		}
	}
}

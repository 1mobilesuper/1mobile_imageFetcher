package me.onemobile.client.image;

import java.io.File;

import android.annotation.TargetApi;
import android.content.Context;

public class ExternalCacheDir {

	@TargetApi(8)
	public File get(Context context) {
		return context.getExternalCacheDir();
	}
}

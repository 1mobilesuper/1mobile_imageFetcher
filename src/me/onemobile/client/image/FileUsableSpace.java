package me.onemobile.client.image;

import java.io.File;

import android.annotation.TargetApi;

public class FileUsableSpace {

	@TargetApi(9)
	public long getUsableSpace(File f) {
		try {
			return f.getUsableSpace();
		} catch (NoSuchMethodError e) { // Handle for 'odd' ROM
			e.printStackTrace();
			return 0;
		}
	}

}

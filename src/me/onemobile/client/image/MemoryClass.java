package me.onemobile.client.image;

import android.app.ActivityManager;
import android.content.Context;

public class MemoryClass {
	
	public int getMemoryClass(Context ctx) {
		return ((ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
	}

}

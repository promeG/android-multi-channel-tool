package com.github.promeg.multichannel.multi_channel_tool.lib;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by guyacong on 2015/3/26.
 */
public class ChannelUtils {
    private static final String CHANNEL_STRING = "com.github.promeg.multichannel.channel_info";

    /**
     * @return current channel
     */
    public static String getChannel(Context context) {

        String channel = PreferenceManager.getDefaultSharedPreferences(context).getString(CHANNEL_STRING, "");
        if (!TextUtils.isEmpty(channel)) {
            return channel;
        }

        ApplicationInfo appInfo = context.getApplicationInfo();
        String sourceDir = appInfo.sourceDir;
        String channelName = "";
        ZipFile zipfile = null;
        try {
            zipfile = new ZipFile(sourceDir);
            Enumeration<?> entries = zipfile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = ((ZipEntry) entries.nextElement());
                String entryName = entry.getName();
                if (entryName.equals("assets/channel_info")) {
                    InputStream inputStream = zipfile.getInputStream(entry);
                    Scanner sc = new Scanner(inputStream);
                    if (sc.hasNextLine()) {
                        channelName = sc.nextLine();
                    }
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zipfile != null) {
                try {
                    zipfile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!TextUtils.isEmpty(channelName)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(CHANNEL_STRING, channelName).commit();
            return channelName;
        } else {
            return "empty-channel";
        }
    }
}

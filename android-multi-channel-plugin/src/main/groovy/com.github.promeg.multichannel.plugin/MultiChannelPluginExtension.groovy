package com.github.promeg.multichannel.plugin

import com.android.builder.model.SigningConfig

/**
 * Created by guyacong on 2015/3/24.
 */
class MultiChannelPluginExtension {
    String prefix;
    String subfix;

    String jarsignerPath;
    String zipalignPath;

    SigningConfig defaultSigningConfig;

    MultiChannelPluginExtension() {
    }
}

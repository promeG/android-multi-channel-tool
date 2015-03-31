package com.github.promeg.multichannel.plugin

import com.android.builder.model.SigningConfig


/**
 * Created by guyacong on 2015/3/24.
 */
class ChannelExtension {
    String name;
    SigningConfig signingConfig;
    List<String> childFlavors;

    ChannelExtension(String name) {
        this.name = name
    }
}

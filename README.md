# android-multi-channel-tool
基于Gradle的易用、快速、稳定的多渠道打包工具。

## 优势
易用：Gradle插件实现打包，配置简洁；

快速：采用一次编译、多次派生子渠道包的方式，大幅提升打包速度；

稳定：派生子渠道包时，不修改任何已有文件，杜绝基于反编译的多渠道打包方法带来的稳定性问题。

## 用法

#### Step 1  添加到工程

```groovy
buildscript {
  repositories {
    jcenter()
  }

  dependencies {
    classpath 'com.github.promeg:android-multi-channel-plugin:0.1'
  }
}

apply plugin: 'android-multi-channel'

dependencies {
    compile 'com.github.promeg:android-multi-channel-plugin-lib:0.1'
}

```

#### Step 2  配置渠道包

该工具采用父包派生出子包的方式，父包由android.productFlavors指定，子包在父包基础上生成。

下面的例子中，inside和outside是两个android.productFlavors渠道，其中inside是子渠道c1和c2的父包，outside是cc1和cc2的父包。

注意子包和父包的代码、资源文件等完全一样，区别仅仅在于子包中多了/assets/channel_info文件，文件中包含了子包的渠道名称。

```groovy

multiFlavors{
    prefix = "A_"; // 前缀
    subfix = "_B"; // 后缀，生成的apk名称为  " 前缀+渠道名+后缀.apk "
    defaultSigningConfig = android.signingConfigs.release // 默的签名配置
    channelConfig {
        inside { // inside为父渠道名， 父渠道在android.productFlavors中指定
            // override default signing config
            signingConfig = android.signingConfigs.debug
            childFlavors = ["c1", "c2"] // 子渠道名
        }

        outside {
            // using default signing config
            childFlavors = ["cc1", "cc2"]
        }
    }
}

```

完整的配置请参见 [这里](https://github.com/promeG/android-multi-channel-tool/blob/master/example/build.gradle)

#### Step 3  生成多渠道包

``` groovy

gradlew assembleRelease

```

采用示例配置会生成 A_c1_B.apk、A_c2_B.apk、A_cc1_B.apk、A_cc2_B.apk四个渠道包。文件目录是build\outputs\apk。

#### Step 4  在代码中配置渠道包

根据所使用的渠道统计平台，在程序的主Activity的onCreate中添加相应代码：

注意：请不要在AndroidManifest.xml文件中指定渠道名，否则可能导致渠道数据错误。

```java
// 友盟
AnalyticsConfig.setChannel(String channel);

// 腾讯云分析
StatConfig.setInstallChannel(String channel);

// 百度移动统计
StatService.setAppChannel(android.content.Context context, java.lang.String appChannel, boolean saveChannelWithCode);

```

## 速度

待补充。

## 与其他方案对比

目前主要有三种方案：

1. 修改AndroidManifest.xml二进制文件，替换其中的渠道名称

   [方法链接](https://github.com/umeng/umeng-muti-channel-build-tool)
  
   由于该方法修改了编译生成的二进制文件，因此可能导致稳定性问题。 

2. 利用 Android Gradle 的 ProductFlavor 功能添加多个渠道

   [方法链接](https://github.com/umeng/umeng-muti-channel-build-tool/tree/master/Gradle)
   
   该方法每个渠道均会重新编译一次代码，因此速度很慢。对于大型工程来说，每个渠道包的编译可能消耗数分钟，几十个渠道会消耗数小时之久。 

3. 在META-INF目录内添加空文件

   [方法链接](http://tech.meituan.com/mt-apk-packaging.html)
   
   该方法巧妙利用了Android系统在5.0之前忽略空文件签名的问题，实现了不需重新签名即可插入渠道信息的功能。
   
   很遗憾，使用该方法生成的渠道包在Android 5.0及以后的手机上无法安装。
   

本项目综合了方法二和方法三的优点，采用向apk中添加/assets/channel_info文件，并重新签名的方法，既避免修改编译生成的二进制文件（稳定性），又避免为每个渠道单独编译代码（速度），同时以Gradle插件的形式无缝集成到Android编译过程中，简洁易用。




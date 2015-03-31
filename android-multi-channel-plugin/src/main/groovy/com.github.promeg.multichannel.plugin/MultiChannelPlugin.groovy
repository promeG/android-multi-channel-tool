package com.github.promeg.multichannel.plugin

import com.android.build.gradle.AppPlugin
import org.apache.commons.io.FilenameUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.reflect.Instantiator

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Created by guyacong on 2015/3/24.
 */
class MultiChannelPlugin implements Plugin<Project> {
    def CHANNEL_FILE = "/assets/channel_info"
    def CHANNEL_DIR = "/assets"
    def JARSIGNER_EXE = ".." + File.separator + "bin" + File.separator + "jarsigner"
    def ZIPALIGN_EXE = "zipalign"

    @Override
    void apply(Project project) {
        project.extensions.create("multiFlavors", MultiChannelPluginExtension)
        project.multiFlavors.extensions.channelConfig = project.container(ChannelExtension) { String name ->
            ChannelExtension channelExtension = project.gradle.services.get(Instantiator).newInstance(ChannelExtension, name)
            assert channelExtension instanceof ExtensionAware
            return channelExtension
        }

        project.afterEvaluate {
            def hasApp = project.plugins.withType(AppPlugin)
            if (!hasApp) {
                return
            }

            final def log = project.logger
            final def variants = project.android.applicationVariants

            def jarsignerExe
            def zipalignExe
            if (project.multiFlavors.jarsignerPath) {
                jarsignerExe = project.multiFlavors.jarsignerPath
            } else {
                jarsignerExe = System.properties.'java.home' + File.separator + JARSIGNER_EXE
            }

            if (project.multiFlavors.zipalignPath) {
                zipalignExe = project.multiFlavors.zipalignPath
            } else {
                zipalignExe = "${project.android.getSdkDirectory().getAbsolutePath()}" + File.separator + "build-tools" + File.separator + project.android.buildToolsVersion + File.separator + ZIPALIGN_EXE
            }

            log.debug("jarsignerExe: " + jarsignerExe)
            log.debug("zipalignExe: " + zipalignExe)

            variants.all { variant ->
                def flavorName = variant.properties.get('flavorName')

                variant.assemble.doLast {
                    def defaultSignConfig = project.multiFlavors.defaultSigningConfig

                    project.multiFlavors.channelConfig.each() { config ->
                        if (flavorName.equals(config.name)) {
                            log.debug("Generate channel based on " + config.name)

                            def signConfig = (config.signingConfig != null && config.signingConfig.isSigningReady()) ? config.signingConfig : defaultSignConfig

                            if (signConfig == null || !signConfig.isSigningReady()) {
                                throw new ProjectConfigurationException("Could not resolve signing config.", null)
                            }

                            config.childFlavors.each() { childFlavor ->
                                log.debug("\tNew channel: " + childFlavor)
                                Path path = Paths.get(variant.getOutputs().get(0).getOutputFile().getAbsolutePath())

                                genApkWithChannel(project, jarsignerExe, zipalignExe, path.getParent().toString() + File.separator,
                                        FilenameUtils.removeExtension(path.getFileName().toString()),
                                        childFlavor,
                                        project.multiFlavors.prefix,
                                        project.multiFlavors.subfix,
                                        signConfig.getStoreFile().getAbsolutePath(),
                                        signConfig.getKeyAlias(),
                                        signConfig.getStorePassword(),
                                        signConfig.getKeyPassword()
                                )

                            }
                        }
                    }
                    variant.getOutputs().get(0).getOutputFile().delete()

                }
            }


            project.task('displayChannelConfig') << {
                project.multiFlavors.channelConfig.each() { config ->
                    def defaultSignConfig = project.multiFlavors.defaultSigningConfig
                    def signConfig = (config.signingConfig != null && config.signingConfig.isSigningReady()) ? config.signingConfig : defaultSignConfig

                    println "\\-----" + config.name
                    println "\t\\-----signConfig: " + signConfig.getName()
                    config.childFlavors.each() { childFlavor ->
                        println "\t\\-----" + childFlavor
                    }
                }
            }
        }
    }


    void genApkWithChannel(Project project, String jarsignerExe, String zipalignExe, String apkPath, String apkName, String channel, String prefix, String subfix,
                           String keyStoreFilePath, String keystoreName, String storePass, String keyPass)
            throws IOException, InterruptedException {
        def log = project.logger

        log.debug("genApkWithChannel: " +
                "\n\t" + apkPath +
                "\n\t" + apkName +
                "\n\t" + channel +
                "\n\t" + prefix +
                "\n\t" + subfix +
                "\n\t" + keyStoreFilePath +
                "\n\t" + keystoreName +
                "\n\t" + storePass +
                "\n\t" + keyPass)

        long startTime = System.currentTimeMillis()

        // create temp file: xx.apk --> xx.zip
        File oldFile = new File(apkPath + apkName + ".apk")
        File tempFile = new File(apkPath + apkName + "_" + channel + "_temp.zip")

        File tempApkFile = new File(apkPath + apkName + "_" + channel + "_temp.apk")

        // outFile is the final output apk file
        File outFile = new File(apkPath + prefix + channel + subfix + ".apk")

        if (outFile.exists()) {
            outFile.delete();
        }

        // copy oldFile to tempFile
        Files.copy(oldFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        FileSystem zipFileSystem = createZipFileSystem(tempFile.getAbsolutePath(), false)

        // delete META-INF
        deleteEntry(zipFileSystem, "/META-INF/")

        createEntry(zipFileSystem, CHANNEL_FILE, channel)

        zipFileSystem.close()

        // rename file: xx.zip --> xx_temp.apk and waiting for re-sign
        Files.move(tempFile.toPath(), tempApkFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        // re-sign apk
        String signCmd = (jarsignerExe + " -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore "
                + keyStoreFilePath
                + " -storepass " + storePass + " -keypass " + keyPass + " "
                + tempApkFile.getAbsolutePath().replaceAll(" ", "\" \"")
                + " " + keystoreName)

        log.debug(signCmd)

        if (execCmdAndWait(signCmd, true) == 0) {
            log.debug("jarsigner process: " + tempApkFile.getAbsolutePath())
        } else {
            log.error("jarsigner Error: " + tempApkFile.getAbsolutePath())
            return
        }

        // zipalign
        String zipAlignCmd = (zipalignExe + " -v 4 "
                + tempApkFile.getAbsolutePath().replaceAll(" ", "\" \"")
                + " " + outFile.getAbsolutePath().replaceAll(" ", "\" \""))

        if (execCmdAndWait(zipAlignCmd, true) == 0) {
            log.debug("zipalign process: " + tempApkFile.getAbsolutePath())
        } else {
            log.error("zipalign Error: " + tempApkFile.getAbsolutePath())
            return
        }

        // delete temp apk file
        tempApkFile.delete()

        // sucess
        log.debug("Sucess: " + outFile.getAbsolutePath())
        log.debug("time:   " + (System.currentTimeMillis() - startTime))
    }

    void createEntry(FileSystem zipFileSystem, String entryName, String content) throws IOException {
        Path nf = zipFileSystem.getPath(entryName)

        Path dirPath = zipFileSystem.getPath(CHANNEL_DIR)

        if (!Files.exists(dirPath)) {
            Files.createDirectory(dirPath)
        }

        Writer writer = Files.newBufferedWriter(nf, StandardCharsets.UTF_8, StandardOpenOption.CREATE)
        writer.write(content)
        writer.flush()
        writer.close()
    }

    void deleteEntry(FileSystem zipFileSystem, String entryName) throws IOException {
        Path path = zipFileSystem.getPath(entryName)
        if (!Files.exists(path)) {
            return
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs) throws IOException {

                println("Deleting file: " + file)
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                                                      IOException exc) throws IOException {

                println("Deleting dir: " + dir)
                if (exc == null) {
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                } else {
                    throw exc
                }
            }

        })
    }

    FileSystem createZipFileSystem(String zipFilename,
                                   boolean create)
            throws IOException {
        // convert the filename to a URI
        final Path path = Paths.get(zipFilename)
        final URI uri = URI.create("jar:file:" + path.toUri().getPath())

        final Map<String, String> env = new HashMap<String, String>()
        if (create) {
            env.put("create", "true")
        }
        return FileSystems.newFileSystem(uri, env)
    }

    int execCmdAndWait(String cmd, boolean showOutput)
            throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(cmd)
        if (showOutput) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))
            String line
            while ((line = reader.readLine()) != null) {
                println("jarsigner output: " + line)
            }
        }
        return process.waitFor()
    }
}

package com.omes.dgw.deploy;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Process handoff: the replacement must not open ports or databases before its parent exits. */
public final class RestartProcess {
    static final String PARENT_PID = "omes.restart.parent-pid";

    private RestartProcess() { }

    public static void awaitPreviousProcess() {
        String pid = System.getProperty(PARENT_PID);
        if (pid == null) return;
        System.clearProperty(PARENT_PID);
        try {
            ProcessHandle previous = ProcessHandle.of(Long.parseLong(pid)).orElse(null);
            if (previous != null) previous.onExit().get(90, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("旧进程未能退出，取消启动替代进程", ex);
        }
    }

    static List<String> command(Path plugins, String[] args) throws Exception {
        String java = ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("无法确定 Java 启动程序"));
        return command(java, ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getProperty("java.class.path"), plugins, ProcessHandle.current().pid(), args);
    }

    static List<String> command(String java, List<String> vmArgs, String classpath,
                                Path plugins, long parentPid, String[] args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(java);
        for (String arg : vmArgs) {
            // IDE agents/debug ports belong to the original process.
            if (!arg.startsWith("-javaagent:") && !arg.startsWith("-agentlib:")
                    && !arg.startsWith("-agentpath:") && !arg.startsWith("-Xrunjdwp:")
                    && !arg.startsWith("-D" + PARENT_PID + "=")
                    && !arg.startsWith("-Dloader.path=")) command.add(arg);
        }
        command.add("-D" + PARENT_PID + "=" + parentPid);
        Path archive = classpath.contains(File.pathSeparator) ? null : Path.of(classpath);
        if (archive != null && Files.isRegularFile(archive) && classpath.endsWith(".jar")) {
            try (JarFile jar = new JarFile(archive.toFile())) {
                if (jar.getManifest() == null || !"org.springframework.boot.loader.launch.PropertiesLauncher"
                        .equals(jar.getManifest().getMainAttributes().getValue("Main-Class"))) {
                    throw new IllegalStateException("当前 JAR 不支持插件加载，请使用 ZIP 布局的 *-exec.jar");
                }
            }
            command.add("-jar");
            command.add(archive.toAbsolutePath().toString());
        } else {
            command.add("-cp");
            command.add(classpath);
            command.add("com.omes.dgw.App");
        }
        command.addAll(List.of(args));
        return command;
    }
}

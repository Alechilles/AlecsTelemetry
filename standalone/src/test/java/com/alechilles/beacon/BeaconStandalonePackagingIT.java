package com.alechilles.beacon;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconStandalonePackagingIT {

    @Test
    void producedJarContainsBeaconIdentityMetadataAndUsableUiAssets() throws Exception {
        Path jarPath = packagedJar();
        assertTrue(Files.isRegularFile(jarPath), "the produced Beacon JAR must exist");

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest jarManifest = jar.getManifest();
            assertNotNull(jarManifest, "the produced JAR must have a manifest");
            assertEquals("com.alechilles", jarManifest.getMainAttributes().getValue("Plugin-Group"));
            assertEquals("beacon", jarManifest.getMainAttributes().getValue("Plugin-Name"));
            assertEquals("2.0.0", jarManifest.getMainAttributes().getValue("Plugin-Version"));

            String manifest = entryText(jar, "manifest.json");
            assertEquals("Alechilles", jsonString(manifest, "Group"));
            assertEquals("Beacon", jsonString(manifest, "Name"));
            assertEquals("2.0.0", jsonString(manifest, "Version"));
            assertEquals("Beacon — The developer platform behind ModStats.io.", jsonString(manifest, "Description"));
            assertEquals("https://beacon.modstats.io", jsonString(manifest, "Website"));
            assertEquals("com.alechilles.beacon.Beacon", jsonString(manifest, "Main"));

            assertMavenProperties(jar, "META-INF/maven/com.alechilles/beacon/pom.properties",
                    "com.alechilles", "beacon", "2.0.0");
            assertMavenProperties(jar, "META-INF/maven/com.alechilles/beacon-runtime/pom.properties",
                    "com.alechilles", "beacon-runtime", "2.0.0");

            String credits = entryText(jar, "Server/Credits/Beacon.json");
            assertTrue(credits.contains("\"Plugin\": \"Alechilles:Beacon\""));
            assertTrue(credits.contains("\"RawText\": \"Beacon — The developer platform behind ModStats.io.\""));

            assertImage(jar, "Common/UI/Custom/BeaconLogo.png", 800, 278);
            assertImage(jar, "Common/UI/Custom/icon-256.png", 256, 256);
            assertImage(jar, "Common/UI/Custom/TelemetryConsentHeader.png", 1560, 256);
            assertImage(jar, "icon-256.png", 256, 256);

            for (String uiPath : new String[]{
                    "Common/UI/Custom/TelemetryConsentPage.ui",
                    "Common/UI/Custom/TelemetryReportPage.ui",
                    "Common/UI/Custom/TelemetryReportProjectSelectPage.ui"}) {
                String ui = entryText(jar, uiPath);
                assertTrue(ui.contains("Background: \"BeaconLogo.png\""),
                        uiPath + " must reference the packaged Beacon logo");
                assertTrue(ui.contains("Background: \"TelemetryConsentHeader.png\""),
                        uiPath + " must reference the packaged consent header");
            }
        }
    }

    private static Path packagedJar() throws URISyntaxException {
        Path testClasses = Path.of(BeaconStandalonePackagingIT.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        return testClasses.getParent().resolve("Beacon v2.0.0.jar");
    }

    private static String entryText(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path + " must be packaged in the standalone JAR");
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String jsonString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                .matcher(json);
        assertTrue(matcher.find(), "manifest must contain a string property named " + key);
        return matcher.group(1);
    }

    private static void assertMavenProperties(JarFile jar, String path, String groupId, String artifactId,
                                              String version) throws IOException {
        Properties properties = new Properties();
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path + " must be packaged in the standalone JAR");
        try (InputStream input = jar.getInputStream(entry)) {
            properties.load(input);
        }
        assertEquals(groupId, properties.getProperty("groupId"));
        assertEquals(artifactId, properties.getProperty("artifactId"));
        assertEquals(version, properties.getProperty("version"));
    }

    private static void assertImage(JarFile jar, String path, int width, int height) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path + " must be packaged in the standalone JAR");
        try (InputStream input = jar.getInputStream(entry)) {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path + " must be a readable image");
            assertEquals(width, image.getWidth(), path + " width");
            assertEquals(height, image.getHeight(), path + " height");
            assertTrue(image.getColorModel().hasAlpha(), path + " must retain alpha");
        }
    }
}

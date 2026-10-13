package com.sturgeon.xflipcamera.core;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** JVM-only independent decode of the original device evidence; not packaged in Android. */
public final class PhotoEvidenceChecks {
    public static void main(String[] args) throws Exception {
        Path directory = args.length == 0 ? Path.of("docs/evidence/2026-10-12-v1")
                : Path.of(args[0]);
        String[] hashes = {
                "454b6d10a45c3dee2e5851e2a5ddba4e024e04db11e03c541dd8c6a2430689bb",
                "3ba26c465c874f976aa4e52bf3800d525884127fb54c765fdef7c94d594bd591"
        };
        long decoded = 0;
        for (int index = 0; index < hashes.length; index++) {
            Path path = directory.resolve("photo-default-" + (index + 1) + ".jpg");
            byte[] bytes = Files.readAllBytes(path); // Missing evidence must fail the check.
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            require(bytes.length == 62_824 && sha.equals(hashes[index]), "Original JPEG identity");
            BufferedImage image = ImageIO.read(path.toFile());
            require(image != null, "JPEG must actually decode");
            require(image.getWidth() == 4096 && image.getHeight() == 2534, "Encoded dimensions");
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null,
                    0, image.getWidth());
            RgbFrameStats stats = RgbFrameStats.fromArgb(pixels);
            require(stats.allBlack() && stats.nonBlackPixels == 0, "All decoded RGB pixels are zero");
            require(stats.redMin == 0 && stats.redMax == 0 && stats.greenMin == 0
                    && stats.greenMax == 0 && stats.blueMin == 0 && stats.blueMax == 0,
                    "Independent full-raster channel ranges agree with the device report");
            decoded += stats.pixels;
            image.flush();
        }
        System.out.println("PASS original JPEG evidence: 2 files, " + decoded
                + " fully decoded pixels, RGB=0, original SHA-256 hashes match");
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}

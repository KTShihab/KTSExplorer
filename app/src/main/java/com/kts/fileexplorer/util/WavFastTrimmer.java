package com.kts.fileexplorer.util;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Super-fast, lossless WAV trimmer and segment deleter.
 * Performs direct PCM sample slicing without re-encoding.
 * - 100% lossless (preserves exact source bitrate, sampling rate, channels, bit depth).
 * - Completes in milliseconds.
 */
public class WavFastTrimmer {

    private static final String TAG = "WavFastTrimmer";

    public static boolean trimWav(File inputFile, File outputFile, long startMs, long endMs) {
        return sliceWav(inputFile, outputFile, startMs, endMs, false);
    }

    public static boolean deleteWavSegment(File inputFile, File outputFile, long startMs, long endMs) {
        return sliceWav(inputFile, outputFile, startMs, endMs, true);
    }

    private static boolean sliceWav(File inputFile, File outputFile, long startMs, long endMs, boolean isDeleteMode) {
        if (!inputFile.exists() || inputFile.length() < 44) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] header = new byte[44];
            int readH = fis.read(header);
            if (readH < 44) return false;

            // Check "RIFF" and "WAVE"
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F' ||
                header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E') {
                return false;
            }

            ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int channels = bb.getShort(22);
            int sampleRate = bb.getInt(24);
            int byteRate = bb.getInt(28);
            int blockAlign = bb.getShort(32);
            int bitsPerSample = bb.getShort(34);
            int dataSize = bb.getInt(40);

            if (byteRate <= 0 || blockAlign <= 0) {
                byteRate = sampleRate * channels * (bitsPerSample / 8);
                blockAlign = channels * (bitsPerSample / 8);
            }
            if (byteRate <= 0 || blockAlign <= 0) return false;

            long totalAudioBytes = inputFile.length() - 44;
            if (dataSize <= 0 || dataSize > totalAudioBytes) {
                dataSize = (int) totalAudioBytes;
            }

            long startByte = (startMs * byteRate) / 1000L;
            startByte = (startByte / blockAlign) * blockAlign;
            if (startByte < 0) startByte = 0;
            if (startByte > dataSize) startByte = dataSize;

            long endByte = (endMs * byteRate) / 1000L;
            endByte = (endByte / blockAlign) * blockAlign;
            if (endByte < startByte) endByte = startByte;
            if (endByte > dataSize) endByte = dataSize;

            long targetDataSize;
            if (!isDeleteMode) {
                targetDataSize = endByte - startByte;
            } else {
                targetDataSize = dataSize - (endByte - startByte);
            }

            // Write updated header
            long totalRiffSize = targetDataSize + 36;
            bb.putInt(4, (int) totalRiffSize);
            bb.putInt(40, (int) targetDataSize);
            fos.write(header, 0, 44);

            byte[] buffer = new byte[65536];

            if (!isDeleteMode) {
                // TRIM: Skip up to startByte, then copy (endByte - startByte)
                long skipped = 0;
                while (skipped < startByte) {
                    long s = fis.skip(startByte - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }

                long remainingToCopy = endByte - startByte;
                while (remainingToCopy > 0) {
                    int toRead = (int) Math.min(buffer.length, remainingToCopy);
                    int r = fis.read(buffer, 0, toRead);
                    if (r <= 0) break;
                    fos.write(buffer, 0, r);
                    remainingToCopy -= r;
                }
            } else {
                // DELETE: Copy 0..startByte, then skip (endByte - startByte), then copy endByte..dataSize
                long remainingPart1 = startByte;
                while (remainingPart1 > 0) {
                    int toRead = (int) Math.min(buffer.length, remainingPart1);
                    int r = fis.read(buffer, 0, toRead);
                    if (r <= 0) break;
                    fos.write(buffer, 0, r);
                    remainingPart1 -= r;
                }

                long toSkip = endByte - startByte;
                long skipped = 0;
                while (skipped < toSkip) {
                    long s = fis.skip(toSkip - skipped);
                    if (s <= 0) break;
                    skipped += s;
                }

                int r;
                while ((r = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, r);
                }
            }

            fos.flush();
            Log.d(TAG, "WAV slicing completed. Data size: " + targetDataSize + ", deleteMode: " + isDeleteMode);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error in WavFastTrimmer", e);
            return false;
        }
    }
}

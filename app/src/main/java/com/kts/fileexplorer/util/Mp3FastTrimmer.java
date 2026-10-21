package com.kts.fileexplorer.util;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Super-fast, lossless MP3 trimmer and segment deleter.
 * Performs direct MPEG audio frame slicing without PCM re-encoding.
 * - 100% lossless (preserves exact source bitrate, sampling rate, channels, and quality).
 * - Completes in milliseconds.
 * - Preserves ID3v2 metadata.
 */
public class Mp3FastTrimmer {

    private static final String TAG = "Mp3FastTrimmer";

    // Bitrate table for MPEG-1 Layer III (in kbps)
    private static final int[] BITRATES_MPEG1_L3 = {
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    };

    // Bitrate table for MPEG-2 / MPEG-2.5 Layer III (in kbps)
    private static final int[] BITRATES_MPEG2_L3 = {
            0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0
    };

    // Sampling rates: [version][index]
    // version 3 = MPEG-1, version 2 = MPEG-2, version 0 = MPEG-2.5
    private static final int[][] SAMPLE_RATES = {
            {11025, 12000, 8000},   // MPEG-2.5 (version 0)
            {0, 0, 0},              // Reserved (version 1)
            {22050, 24000, 16000},  // MPEG-2 (version 2)
            {44100, 48000, 32000}   // MPEG-1 (version 3)
    };

    public static class FrameHeader {
        public int frameSize;
        public int samplesPerFrame;
        public int sampleRate;
        public int bitrateKbps;
        public double durationMs;
    }

    /**
     * Parses a 4-byte MPEG frame header starting at the given buffer offset.
     */
    public static FrameHeader parseHeader(byte[] b, int offset) {
        int b0 = b[offset] & 0xFF;
        int b1 = b[offset + 1] & 0xFF;
        int b2 = b[offset + 2] & 0xFF;
        int b3 = b[offset + 3] & 0xFF;

        // Sync: 11 bits set (0xFFE...)
        if (b0 != 0xFF || (b1 & 0xE0) != 0xE0) {
            return null;
        }

        int version = (b1 >> 3) & 0x03;
        if (version == 1) return null; // Reserved version

        int layer = (b1 >> 1) & 0x03;
        if (layer == 0) return null; // Reserved layer

        int bitrateIdx = (b2 >> 4) & 0x0F;
        if (bitrateIdx == 0 || bitrateIdx == 15) return null;

        int sampleRateIdx = (b2 >> 2) & 0x03;
        if (sampleRateIdx == 3) return null;

        int padding = (b2 >> 1) & 0x01;

        int sampleRate = SAMPLE_RATES[version][sampleRateIdx];
        if (sampleRate <= 0) return null;

        int bitrate;
        int samplesPerFrame;

        if (version == 3) {
            // MPEG-1
            if (layer == 3) {
                // Layer I
                bitrate = bitrateIdx * 32;
                samplesPerFrame = 384;
            } else if (layer == 2) {
                // Layer II
                bitrate = BITRATES_MPEG1_L3[bitrateIdx];
                samplesPerFrame = 1152;
            } else {
                // Layer III
                bitrate = BITRATES_MPEG1_L3[bitrateIdx];
                samplesPerFrame = 1152;
            }
        } else {
            // MPEG-2 / MPEG-2.5
            if (layer == 3) {
                // Layer I
                bitrate = bitrateIdx * 32;
                samplesPerFrame = 384;
            } else {
                // Layer II or III
                bitrate = BITRATES_MPEG2_L3[bitrateIdx];
                samplesPerFrame = (layer == 2) ? 1152 : 576;
            }
        }

        if (bitrate <= 0) return null;

        int frameSize;
        if (layer == 3) {
            // Layer I
            frameSize = ((12 * bitrate * 1000) / sampleRate + padding) * 4;
        } else if (version == 3) {
            // MPEG-1 Layer II / III
            frameSize = (144 * bitrate * 1000) / sampleRate + padding;
        } else {
            // MPEG-2 / 2.5 Layer II / III
            frameSize = ((layer == 2 ? 144 : 72) * bitrate * 1000) / sampleRate + padding;
        }

        if (frameSize <= 4) return null;

        FrameHeader h = new FrameHeader();
        h.frameSize = frameSize;
        h.samplesPerFrame = samplesPerFrame;
        h.sampleRate = sampleRate;
        h.bitrateKbps = bitrate;
        h.durationMs = (samplesPerFrame * 1000.0) / sampleRate;
        return h;
    }

    /**
     * Losslessly trims an MP3 file, keeping the audio between startMs and endMs.
     */
    public static boolean trimMp3(File inputFile, File outputFile, long startMs, long endMs) {
        return sliceMp3(inputFile, outputFile, startMs, endMs, false);
    }

    /**
     * Losslessly deletes the segment between startMs and endMs from an MP3 file.
     */
    public static boolean deleteMp3Segment(File inputFile, File outputFile, long startMs, long endMs) {
        return sliceMp3(inputFile, outputFile, startMs, endMs, true);
    }

    private static boolean sliceMp3(
            File inputFile,
            File outputFile,
            long startMs,
            long endMs,
            boolean isDeleteMode
    ) {
        if (!inputFile.exists() || inputFile.length() == 0) {
            return false;
        }

        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(inputFile), 65536);
            bos = new BufferedOutputStream(new FileOutputStream(outputFile), 65536);

            // 1. Check for ID3v2 header
            bis.mark(10);
            byte[] id3Header = new byte[10];
            int id3Read = bis.read(id3Header);
            if (id3Read == 10 && id3Header[0] == 'I' && id3Header[1] == 'D' && id3Header[2] == '3') {
                int tagSize = ((id3Header[6] & 0x7F) << 21)
                        | ((id3Header[7] & 0x7F) << 14)
                        | ((id3Header[8] & 0x7F) << 7)
                        | (id3Header[9] & 0x7F);
                int totalId3Len = 10 + tagSize;
                if ((id3Header[5] & 0x10) != 0) {
                    totalId3Len += 10; // footer
                }

                // Copy entire ID3 tag to preserve artwork, title, artist
                bis.reset();
                byte[] id3Buffer = new byte[Math.min(totalId3Len, 512 * 1024)];
                int remainingId3 = totalId3Len;
                while (remainingId3 > 0) {
                    int toRead = Math.min(id3Buffer.length, remainingId3);
                    int r = bis.read(id3Buffer, 0, toRead);
                    if (r <= 0) break;
                    bos.write(id3Buffer, 0, r);
                    remainingId3 -= r;
                }
            } else {
                bis.reset();
            }

            // 2. Iterate MPEG audio frames
            double currentMs = 0.0;
            byte[] headerBuf = new byte[4];
            int framesWritten = 0;

            while (true) {
                // Find next sync byte 0xFF
                int b = bis.read();
                if (b == -1) break;

                if (b != 0xFF) {
                    continue; // Skip non-sync byte / padding
                }

                bis.mark(4);
                headerBuf[0] = (byte) 0xFF;
                int hRead = bis.read(headerBuf, 1, 3);
                if (hRead < 3) break;

                FrameHeader header = parseHeader(headerBuf, 0);
                if (header == null) {
                    // False sync, roll back 3 bytes and continue search
                    bis.reset();
                    continue;
                }

                // Valid frame! Read the remainder of the frame body
                int bodySize = header.frameSize - 4;
                byte[] frameBody = new byte[bodySize];
                int readBody = 0;
                while (readBody < bodySize) {
                    int r = bis.read(frameBody, readBody, bodySize - readBody);
                    if (r <= 0) break;
                    readBody += r;
                }
                if (readBody < bodySize) {
                    break; // Corrupted / truncated end of file
                }

                double frameStartMs = currentMs;
                double frameEndMs = currentMs + header.durationMs;
                currentMs = frameEndMs;

                boolean shouldWrite;
                if (!isDeleteMode) {
                    // TRIM MODE: keep [startMs, endMs]
                    shouldWrite = (frameEndMs >= startMs && frameStartMs <= endMs);
                    if (frameStartMs > endMs) {
                        // Reached end of cut, we can terminate early!
                        break;
                    }
                } else {
                    // DELETE MODE: keep 0..startMs and endMs..duration (exclude [startMs, endMs])
                    shouldWrite = (frameEndMs < startMs || frameStartMs > endMs);
                }

                if (shouldWrite) {
                    bos.write(headerBuf, 0, 4);
                    bos.write(frameBody, 0, bodySize);
                    framesWritten++;
                }
            }

            bos.flush();
            Log.d(TAG, "Mp3 slicing completed. Frames written: " + framesWritten + ", deleteMode: " + isDeleteMode);
            return framesWritten > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error in Mp3FastTrimmer", e);
            return false;
        } finally {
            if (bis != null) {
                try { bis.close(); } catch (Exception ignored) {}
            }
            if (bos != null) {
                try { bos.close(); } catch (Exception ignored) {}
            }
        }
    }
}

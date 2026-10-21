package com.kts.fileexplorer.util;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Media and file splitting utility supporting:
 * - Lossless media splitting by duration using MediaExtractor + MediaMuxer
 * - Generic file splitting by size
 * - Splitting media or generic files into N equal parts
 */
public class MediaSplitter {

    private static final String TAG = "MediaSplitter";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SplitListener {
        void onSuccess(List<File> outputFiles);
        void onError(String errorMessage);
    }

    /**
     * Splits media (audio or video) by duration in seconds.
     */
    public static void splitMediaByDuration(
            File inputFile,
            int durationSecondsPerPart,
            SplitListener listener
    ) {
        executor.execute(() -> {
            try {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(inputFile.getAbsolutePath());
                String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                mmr.release();

                if (durationStr == null) {
                    throw new IOException("Unable to determine media duration for " + inputFile.getName());
                }

                long totalDurationMs = Long.parseLong(durationStr);
                long partDurationMs = durationSecondsPerPart * 1000L;
                if (partDurationMs <= 0 || partDurationMs >= totalDurationMs) {
                    throw new IllegalArgumentException("Split duration must be shorter than file duration");
                }

                int numParts = (int) Math.ceil((double) totalDurationMs / partDurationMs);
                List<File> parts = new ArrayList<>();

                String fileName = inputFile.getName();
                String baseName = fileName;
                String extension = "";
                int dotIdx = fileName.lastIndexOf('.');
                if (dotIdx > 0) {
                    baseName = fileName.substring(0, dotIdx);
                    extension = fileName.substring(dotIdx);
                }

                File parentDir = inputFile.getParentFile();

                for (int i = 0; i < numParts; i++) {
                    long startUs = i * partDurationMs * 1000L;
                    long endUs = Math.min((i + 1) * partDurationMs * 1000L, totalDurationMs * 1000L);

                    File outFile = new File(parentDir, baseName + "_part" + (i + 1) + extension);
                    boolean extracted = extractMediaSegment(inputFile, outFile, startUs, endUs);
                    if (extracted && outFile.exists() && outFile.length() > 0) {
                        parts.add(outFile);
                    } else {
                        // Fallback to binary chunking if muxer fails for this format
                        throw new IOException("Failed to extract media segment part " + (i + 1));
                    }
                }

                mainHandler.post(() -> listener.onSuccess(parts));
            } catch (Exception e) {
                Log.e(TAG, "Error splitting media by duration", e);
                // Fallback to generic splitting by equal parts if media extractor failed
                splitFileByParts(inputFile, (int) Math.ceil((double) inputFile.length() / (durationSecondsPerPart * 128 * 1024L)), listener);
            }
        });
    }

    /**
     * Splits any file by chunk size in bytes.
     */
    public static void splitFileBySize(
            File inputFile,
            long bytesPerPart,
            SplitListener listener
    ) {
        executor.execute(() -> {
            try {
                if (bytesPerPart <= 0 || bytesPerPart >= inputFile.length()) {
                    throw new IllegalArgumentException("Part size must be smaller than file size");
                }

                List<File> parts = new ArrayList<>();
                String fileName = inputFile.getName();
                String baseName = fileName;
                String extension = "";
                int dotIdx = fileName.lastIndexOf('.');
                if (dotIdx > 0) {
                    baseName = fileName.substring(0, dotIdx);
                    extension = fileName.substring(dotIdx);
                }

                File parentDir = inputFile.getParentFile();

                try (FileInputStream fis = new FileInputStream(inputFile)) {
                    byte[] buffer = new byte[64 * 1024];
                    int partIndex = 1;
                    long remainingForThisPart = bytesPerPart;

                    File currentPart = new File(parentDir, baseName + "_part" + partIndex + extension);
                    FileOutputStream fos = new FileOutputStream(currentPart);
                    parts.add(currentPart);

                    int bytesRead;
                    while ((bytesRead = fis.read(buffer, 0, (int) Math.min(buffer.length, remainingForThisPart))) > 0) {
                        fos.write(buffer, 0, bytesRead);
                        remainingForThisPart -= bytesRead;

                        if (remainingForThisPart <= 0) {
                            fos.flush();
                            fos.close();

                            if (fis.available() > 0) {
                                partIndex++;
                                remainingForThisPart = bytesPerPart;
                                currentPart = new File(parentDir, baseName + "_part" + partIndex + extension);
                                fos = new FileOutputStream(currentPart);
                                parts.add(currentPart);
                            }
                        }
                    }

                    fos.flush();
                    fos.close();
                }

                mainHandler.post(() -> listener.onSuccess(parts));
            } catch (Exception e) {
                Log.e(TAG, "Error splitting file by size", e);
                mainHandler.post(() -> listener.onError(e.getMessage() != null ? e.getMessage() : "Splitting failed"));
            }
        });
    }

    /**
     * Splits a file into N equal parts.
     */
    public static void splitFileByParts(
            File inputFile,
            int numParts,
            SplitListener listener
    ) {
        if (numParts <= 1) {
            listener.onError("Number of parts must be at least 2");
            return;
        }
        long partSize = (long) Math.ceil((double) inputFile.length() / numParts);
        splitFileBySize(inputFile, partSize, listener);
    }

    /**
     * Extracts a lossless segment of media between startUs and endUs using MediaExtractor and MediaMuxer.
     */
    public static boolean extractMediaSegment(File inputFile, File outputFile, long startUs, long endUs) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;

        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            int selectedTrackIndex = -1;

            // Find first audio track (or fallback to track 0)
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    selectedTrackIndex = i;
                    break;
                }
            }
            if (selectedTrackIndex < 0 && trackCount > 0) {
                selectedTrackIndex = 0;
            }
            if (selectedTrackIndex < 0) {
                return false;
            }

            MediaFormat trackFormat = extractor.getTrackFormat(selectedTrackIndex);
            int muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            String lowerName = outputFile.getName().toLowerCase(Locale.ENGLISH);
            if (lowerName.endsWith(".webm")) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
                }
            } else if (lowerName.endsWith(".ogg") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG;
            }

            muxer = new MediaMuxer(outputFile.getAbsolutePath(), muxerFormat);
            int muxerTrackIndex = muxer.addTrack(trackFormat);
            extractor.selectTrack(selectedTrackIndex);
            muxer.start();

            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            }

            int maxBufferSize = 256 * 1024;
            if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                int size = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                if (size > maxBufferSize) maxBufferSize = size;
            }

            ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            long firstSampleTimeUs = -1;
            int samplesWritten = 0;

            while (true) {
                int trackIdx = extractor.getSampleTrackIndex();
                if (trackIdx != selectedTrackIndex) {
                    if (trackIdx < 0) break;
                    extractor.advance();
                    continue;
                }

                long sampleTime = extractor.getSampleTime();
                if (endUs > 0 && sampleTime > endUs) {
                    break;
                }

                if (sampleTime >= startUs) {
                    if (firstSampleTimeUs < 0) {
                        firstSampleTimeUs = sampleTime;
                    }

                    info.offset = 0;
                    info.size = extractor.readSampleData(buffer, 0);
                    if (info.size < 0) break;

                    info.presentationTimeUs = Math.max(0, sampleTime - firstSampleTimeUs);
                    info.flags = extractor.getSampleFlags();

                    muxer.writeSampleData(muxerTrackIndex, buffer, info);
                    samplesWritten++;
                }

                extractor.advance();
            }

            if (samplesWritten > 0) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    Log.e(TAG, "muxer.stop error", e);
                }
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "extractMediaSegment error", e);
            return false;
        } finally {
            extractor.release();
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Losslessly deletes a segment [deleteStartUs, deleteEndUs] from media without re-encoding.
     */
    public static boolean deleteMediaSegment(File inputFile, File outputFile, long deleteStartUs, long deleteEndUs) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;

        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            int selectedTrackIndex = -1;

            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    selectedTrackIndex = i;
                    break;
                }
            }
            if (selectedTrackIndex < 0 && trackCount > 0) {
                selectedTrackIndex = 0;
            }
            if (selectedTrackIndex < 0) {
                return false;
            }

            MediaFormat trackFormat = extractor.getTrackFormat(selectedTrackIndex);
            int muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            String lowerName = outputFile.getName().toLowerCase(Locale.ENGLISH);
            if (lowerName.endsWith(".webm") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
            } else if (lowerName.endsWith(".ogg") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG;
            }

            muxer = new MediaMuxer(outputFile.getAbsolutePath(), muxerFormat);
            int muxerTrackIndex = muxer.addTrack(trackFormat);
            extractor.selectTrack(selectedTrackIndex);
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            muxer.start();

            int maxBufferSize = 256 * 1024;
            if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                int size = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                if (size > maxBufferSize) maxBufferSize = size;
            }

            ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            long deletedDurationUs = deleteEndUs - deleteStartUs;
            long firstSampleTimeUs = -1;
            int samplesWritten = 0;

            while (true) {
                int trackIdx = extractor.getSampleTrackIndex();
                if (trackIdx != selectedTrackIndex) {
                    if (trackIdx < 0) break;
                    extractor.advance();
                    continue;
                }

                long sampleTime = extractor.getSampleTime();

                // Skip samples that fall within the deleted segment
                if (sampleTime >= deleteStartUs && sampleTime < deleteEndUs) {
                    extractor.advance();
                    continue;
                }

                if (firstSampleTimeUs < 0) {
                    firstSampleTimeUs = sampleTime;
                }

                info.offset = 0;
                info.size = extractor.readSampleData(buffer, 0);
                if (info.size < 0) break;

                long adjustedTimeUs;
                if (sampleTime < deleteStartUs) {
                    adjustedTimeUs = sampleTime - firstSampleTimeUs;
                } else {
                    adjustedTimeUs = (sampleTime - firstSampleTimeUs) - deletedDurationUs;
                }

                info.presentationTimeUs = Math.max(0, adjustedTimeUs);
                info.flags = extractor.getSampleFlags();

                muxer.writeSampleData(muxerTrackIndex, buffer, info);
                samplesWritten++;

                extractor.advance();
            }

            if (samplesWritten > 0) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    Log.e(TAG, "muxer.stop error", e);
                }
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "deleteMediaSegment error", e);
            return false;
        } finally {
            extractor.release();
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Trims media between startMs and endMs.
     */
    public static void trimMedia(
            File inputFile,
            File outputFile,
            long startMs,
            long endMs,
            SplitListener listener
    ) {
        executor.execute(() -> {
            try {
                if (startMs < 0 || endMs <= startMs) {
                    throw new IllegalArgumentException("Invalid start and end times");
                }

                long startUs = startMs * 1000L;
                long endUs = endMs * 1000L;

                boolean success = extractMediaSegment(inputFile, outputFile, startUs, endUs);
                if (success && outputFile.exists() && outputFile.length() > 0) {
                    List<File> result = new ArrayList<>();
                    result.add(outputFile);
                    mainHandler.post(() -> listener.onSuccess(result));
                } else {
                    throw new IOException("Failed to trim media segment");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error trimming media", e);
                mainHandler.post(() -> listener.onError(e.getMessage() != null ? e.getMessage() : "Trimming failed"));
            }
        });
    }
}

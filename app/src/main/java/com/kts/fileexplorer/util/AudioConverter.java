package com.kts.fileexplorer.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance media converter supporting:
 * - Native fixed-point MP3 encoding (via libshine JNI)
 * - Lossless 16-bit PCM to RIFF WAV
 * - MediaCodec AAC/M4A encoding
 * - Direct lossless stream demuxing / muxing for instant video-to-audio extraction
 * - Image conversion (JPG, PNG, WEBP)
 * - Custom Bitrate, Sample Rate, and Channel mixing (Stereo / Mono)
 */
public class AudioConverter {

    private static final String TAG = "AudioConverter";

    static {
        System.loadLibrary("ktsexplorer");
    }

    private static native boolean nativeEncodePcmToMp3(
            String pcmPath,
            String mp3Path,
            int sampleRate,
            int bitrateKbps,
            int channels
    );

    private static native long nativeOpenMp3Stream(
            String mp3Path,
            int sampleRate,
            int bitrateKbps,
            int channels
    );

    private static native void nativeWriteMp3Stream(
            long handle,
            byte[] pcmChunk,
            int offset,
            int length
    );

    private static native boolean nativeCloseMp3Stream(long handle);

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ConvertListener {
        void onSuccess(File outputFile);
        void onError(String errorMessage);
        default void onProgress(int percent) {}
        default boolean isCancelled() { return false; }
    }

    /**
     * Converts an audio or video file to a target audio format.
     */
    public static void convertAudio(
            Context context,
            File inputFile,
            File outputFile,
            String targetFormat,
            int targetBitrateKbps,
            int targetSampleRate,
            int targetChannels,
            ConvertListener listener
    ) {
        executor.execute(() -> {
            File tempPcmFile = null;
            try {
                String fmt = targetFormat.toUpperCase(Locale.ENGLISH);

                // Fast Path: Direct AAC/M4A extraction when source audio track is already AAC
                if ((fmt.contains("AAC") || fmt.contains("M4A")) && canDirectExtractAac(inputFile, targetSampleRate, targetChannels)) {
                    boolean extracted = extractAudioTrackDirectly(inputFile, outputFile, listener);
                    if (extracted && outputFile.exists() && outputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(outputFile));
                        return;
                    }
                    if (outputFile.exists() && outputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        outputFile.delete();
                    }
                }

                // Auto resolution: if sampleRate or channels <= 0, keep original from source
                SourceAudioInfo sourceInfo = getSourceAudioInfo(inputFile);
                int effectiveSampleRate = (targetSampleRate <= 0) ? sourceInfo.sampleRate : targetSampleRate;
                if (effectiveSampleRate <= 0) effectiveSampleRate = 44100;
                int effectiveChannels = (targetChannels <= 0) ? sourceInfo.channels : targetChannels;
                if (effectiveChannels <= 0) effectiveChannels = 2;

                if (fmt.contains("MP3")) {
                    // High-speed direct streaming MP3 encoding (Zero disk intermediate PCM)
                    boolean success = streamEncodeToMp3(
                            inputFile,
                            outputFile,
                            effectiveSampleRate,
                            targetBitrateKbps,
                            effectiveChannels,
                            listener
                    );
                    if (success && outputFile.exists() && outputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(outputFile));
                    } else {
                        mainHandler.post(() -> listener.onError("Encoding failed for format " + targetFormat));
                    }
                    return;
                } else if (fmt.contains("AAC") || fmt.contains("M4A")) {
                    // High-speed direct streaming AAC/M4A encoding (Zero disk intermediate PCM)
                    boolean success = streamEncodeToAac(
                            inputFile,
                            outputFile,
                            effectiveSampleRate,
                            targetBitrateKbps,
                            effectiveChannels,
                            listener
                    );
                    if (success && outputFile.exists() && outputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(outputFile));
                    } else {
                        mainHandler.post(() -> listener.onError("Encoding failed for format " + targetFormat));
                    }
                    return;
                }

                // Step 1: Decode source file to 16-bit PCM with requested channels and sample rate
                tempPcmFile = new File(context.getCacheDir(), "temp_convert_" + System.currentTimeMillis() + ".pcm");
                boolean decoded = decodeToPcm(inputFile, tempPcmFile, effectiveSampleRate, effectiveChannels, listener);
                if (!decoded || !tempPcmFile.exists() || tempPcmFile.length() == 0) {
                    throw new IOException("Failed to decode audio track from " + inputFile.getName());
                }

                // Step 2: Encode PCM to requested format
                boolean success;

                if (fmt.contains("WAV")) {
                    success = encodePcmToWav(tempPcmFile, outputFile, effectiveSampleRate, effectiveChannels, listener);
                } else if (fmt.contains("FLAC")) {
                    success = encodePcmToFlac(tempPcmFile, outputFile, effectiveSampleRate, effectiveChannels);
                } else {
                    // Fallback to streaming MP3
                    success = streamEncodeToMp3(
                            inputFile,
                            outputFile,
                            effectiveSampleRate,
                            targetBitrateKbps,
                            effectiveChannels,
                            listener
                    );
                }

                if (success && outputFile.exists() && outputFile.length() > 0) {
                    mainHandler.post(() -> listener.onSuccess(outputFile));
                } else {
                    mainHandler.post(() -> listener.onError("Encoding failed for format " + targetFormat));
                }
            } catch (Exception e) {
                Log.e(TAG, "Audio conversion error", e);
                mainHandler.post(() -> listener.onError(e.getMessage() != null ? e.getMessage() : "Conversion failed"));
            } finally {
                if (tempPcmFile != null && tempPcmFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempPcmFile.delete();
                }
            }
        });
    }

    /**
     * Converts an image file to JPG, PNG, or WEBP.
     */
    public static void convertImage(
            File inputFile,
            File outputFile,
            String targetFormat,
            int qualityPercent,
            ConvertListener listener
    ) {
        executor.execute(() -> {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(inputFile.getAbsolutePath());
                if (bitmap == null) {
                    throw new IOException("Could not decode image file: " + inputFile.getName());
                }

                Bitmap.CompressFormat compressFormat;
                String fmt = targetFormat.toUpperCase();
                if (fmt.contains("PNG")) {
                    compressFormat = Bitmap.CompressFormat.PNG;
                } else if (fmt.contains("WEBP")) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        compressFormat = Bitmap.CompressFormat.WEBP_LOSSY;
                    } else {
                        //noinspection deprecation
                        compressFormat = Bitmap.CompressFormat.WEBP;
                    }
                } else {
                    compressFormat = Bitmap.CompressFormat.JPEG;
                }

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    boolean success = bitmap.compress(compressFormat, qualityPercent, fos);
                    fos.flush();
                    bitmap.recycle();
                    if (success && outputFile.exists()) {
                        mainHandler.post(() -> listener.onSuccess(outputFile));
                    } else {
                        mainHandler.post(() -> listener.onError("Image compression failed"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Image conversion error", e);
                mainHandler.post(() -> listener.onError(e.getMessage() != null ? e.getMessage() : "Image conversion failed"));
            }
        });
    }

    /**
     * Checks if the source media contains an AAC audio track that can be extracted directly
     * without re-encoding, preserving 100% original quality and finishing in milliseconds.
     */
    public static boolean canDirectExtractAac(File inputFile, int targetSampleRate, int targetChannels) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && (mime.equalsIgnoreCase("audio/mp4a-latm") || mime.equalsIgnoreCase("audio/aac"))) {
                    int srcRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 0;
                    int srcChannels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 0;
                    boolean rateMatches = (targetSampleRate <= 0 || targetSampleRate == srcRate);
                    boolean channelsMatch = (targetChannels <= 0 || targetChannels == srcChannels);
                    return rateMatches && channelsMatch;
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Extracts an AAC audio track directly from an MP4/MKV/WebM video container into an M4A file
     * using MediaMuxer with zero re-encoding overhead.
     */
    private static boolean extractAudioTrackDirectly(File inputFile, File outputFile, ConvertListener listener) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int audioTrackIndex = -1;
            MediaFormat trackFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && (mime.equalsIgnoreCase("audio/mp4a-latm") || mime.equalsIgnoreCase("audio/aac"))) {
                    audioTrackIndex = i;
                    trackFormat = format;
                    break;
                }
            }
            if (audioTrackIndex < 0 || trackFormat == null) return false;

            extractor.selectTrack(audioTrackIndex);
            long durationUs = trackFormat.containsKey(MediaFormat.KEY_DURATION) ? trackFormat.getLong(MediaFormat.KEY_DURATION) : 0;

            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerTrackIndex = muxer.addTrack(trackFormat);
            muxer.start();

            int maxBufferSize = 256 * 1024;
            if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                int size = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                if (size > maxBufferSize) maxBufferSize = size;
            }

            ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long firstSampleTimeUs = -1;
            int samplesWritten = 0;
            int lastPercent = 0;

            while (true) {
                int trackIdx = extractor.getSampleTrackIndex();
                if (trackIdx != audioTrackIndex) {
                    if (trackIdx < 0) break;
                    extractor.advance();
                    continue;
                }

                long sampleTime = extractor.getSampleTime();
                if (firstSampleTimeUs < 0) firstSampleTimeUs = sampleTime;

                info.offset = 0;
                info.size = extractor.readSampleData(buffer, 0);
                if (info.size < 0) break;

                info.presentationTimeUs = Math.max(0, sampleTime - firstSampleTimeUs);
                info.flags = extractor.getSampleFlags();

                muxer.writeSampleData(muxerTrackIndex, buffer, info);
                samplesWritten++;

                if (durationUs > 0 && listener != null) {
                    int percent = (int) ((sampleTime * 100L) / durationUs);
                    if (percent > lastPercent && percent <= 100) {
                        lastPercent = percent;
                        int p = percent;
                        mainHandler.post(() -> listener.onProgress(p));
                    }
                }

                extractor.advance();
            }

            if (samplesWritten > 0) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    Log.e(TAG, "muxer.stop error", e);
                }
                if (listener != null) {
                    mainHandler.post(() -> listener.onProgress(100));
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "extractAudioTrackDirectly error", e);
            return false;
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
            if (muxer != null) {
                try { muxer.release(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * High-speed direct streaming MP3 encoding without writing any intermediate PCM files to disk.
     */
    private static boolean streamEncodeToMp3(
            File inputFile,
            File outputFile,
            int targetSampleRate,
            int targetBitrateKbps,
            int targetChannels,
            ConvertListener listener
    ) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        long mp3Handle = 0;

        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int audioTrackIndex = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    format = trackFormat;
                    break;
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in: " + inputFile.getName());
                return false;
            }

            extractor.selectTrack(audioTrackIndex);
            long totalInputBytes = inputFile.length();
            long inputBytesRead = 0;
            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            int sourceSampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : targetSampleRate;
            int sourceChannels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : targetChannels;

            if (targetSampleRate <= 0) targetSampleRate = sourceSampleRate;
            if (targetChannels <= 0) targetChannels = sourceChannels;
            if (targetSampleRate <= 0) targetSampleRate = 44100;
            if (targetChannels <= 0) targetChannels = 2;

            mp3Handle = nativeOpenMp3Stream(outputFile.getAbsolutePath(), targetSampleRate, targetBitrateKbps, targetChannels);
            if (mp3Handle == 0) {
                Log.e(TAG, "Failed to open native MP3 stream for " + outputFile.getName());
                return false;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            long timeoutUs = 200;
            int lastReportedPercent = 0;

            while (!sawOutputEOS) {
                if (listener != null && listener.isCancelled()) {
                    break;
                }

                if (!sawInputEOS) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                inputBytesRead += sampleSize;
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();

                                if (totalInputBytes > 0 && listener != null) {
                                    int percent = (int) ((inputBytesRead * 100L) / totalInputBytes);
                                    if (percent > lastReportedPercent && percent <= 99) {
                                        lastReportedPercent = percent;
                                        int p = percent;
                                        mainHandler.post(() -> listener.onProgress(p));
                                    }
                                }
                            }
                        }
                    }
                }

                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }

                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        byte[] chunk = new byte[bufferInfo.size];
                        outputBuffer.get(chunk);

                        byte[] processed = adaptPcm(chunk, sourceSampleRate, sourceChannels, targetSampleRate, targetChannels);
                        nativeWriteMp3Stream(mp3Handle, processed, 0, processed.length);
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                }
            }

            boolean closed = nativeCloseMp3Stream(mp3Handle);
            mp3Handle = 0;
            if (listener != null) {
                mainHandler.post(() -> listener.onProgress(100));
            }
            return closed;
        } catch (Exception e) {
            Log.e(TAG, "Streaming MP3 encoding error", e);
            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;
        } finally {
            if (mp3Handle != 0) {
                try {
                    nativeCloseMp3Stream(mp3Handle);
                } catch (Exception ignored) {}
            }
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception ignored) {}
            }
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * High-speed direct streaming AAC/M4A encoding (MediaCodec decoder -> MediaCodec encoder -> MediaMuxer)
     * entirely in memory without writing intermediate PCM files to disk.
     */
    private static boolean streamEncodeToAac(
            File inputFile,
            File outputFile,
            int targetSampleRate,
            int targetBitrateKbps,
            int targetChannels,
            ConvertListener listener
    ) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        MediaMuxer muxer = null;

        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int audioTrackIndex = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    format = trackFormat;
                    break;
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in: " + inputFile.getName());
                return false;
            }

            extractor.selectTrack(audioTrackIndex);
            long totalInputBytes = inputFile.length();
            long inputBytesRead = 0;

            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            int sourceSampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : targetSampleRate;
            int sourceChannels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : targetChannels;

            if (targetSampleRate <= 0) targetSampleRate = sourceSampleRate;
            if (targetChannels <= 0) targetChannels = sourceChannels;
            if (targetSampleRate <= 0) targetSampleRate = 44100;
            if (targetChannels <= 0) targetChannels = 2;

            MediaFormat encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, targetSampleRate, targetChannels);
            encFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encFormat.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrateKbps * 1000);
            encFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerTrackIndex = -1;
            boolean muxerStarted = false;

            MediaCodec.BufferInfo decOutInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encOutInfo = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false;
            boolean sawDecOutputEOS = false;
            boolean sawEncOutputEOS = false;
            long timeoutUs = 200;
            long presentationTimeUs = 0;
            long bytesPerSec = (long) targetSampleRate * targetChannels * 2;
            int lastReportedPercent = 0;

            while (!sawEncOutputEOS) {
                if (listener != null && listener.isCancelled()) {
                    break;
                }

                // Step A: Feed input from Extractor to Decoder
                if (!sawInputEOS) {
                    int inBufIdx = decoder.dequeueInputBuffer(timeoutUs);
                    if (inBufIdx >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inBufIdx);
                        if (inBuf != null) {
                            inBuf.clear();
                            int sampleSize = extractor.readSampleData(inBuf, 0);
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inBufIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                inputBytesRead += sampleSize;
                                decoder.queueInputBuffer(inBufIdx, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();

                                if (totalInputBytes > 0 && listener != null) {
                                    int percent = (int) ((inputBytesRead * 100L) / totalInputBytes);
                                    if (percent > lastReportedPercent && percent <= 99) {
                                        lastReportedPercent = percent;
                                        int p = percent;
                                        mainHandler.post(() -> listener.onProgress(p));
                                    }
                                }
                            }
                        }
                    }
                }

                // Step B: Pull decoded PCM from Decoder and feed into Encoder
                if (!sawDecOutputEOS) {
                    int decOutIdx = decoder.dequeueOutputBuffer(decOutInfo, timeoutUs);
                    if (decOutIdx >= 0) {
                        if ((decOutInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawDecOutputEOS = true;
                        }

                        ByteBuffer decBuf = decoder.getOutputBuffer(decOutIdx);
                        if (decBuf != null && decOutInfo.size > 0) {
                            decBuf.position(decOutInfo.offset);
                            decBuf.limit(decOutInfo.offset + decOutInfo.size);

                            byte[] chunk = new byte[decOutInfo.size];
                            decBuf.get(chunk);
                            byte[] processed = adaptPcm(chunk, sourceSampleRate, sourceChannels, targetSampleRate, targetChannels);

                            feedPcmToEncoder(encoder, processed, presentationTimeUs, sawDecOutputEOS);
                            presentationTimeUs += (processed.length * 1000000L) / bytesPerSec;
                        } else if (sawDecOutputEOS) {
                            feedPcmToEncoder(encoder, new byte[0], presentationTimeUs, true);
                        }
                        decoder.releaseOutputBuffer(decOutIdx, false);
                    } else if (decOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = decoder.getOutputFormat();
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        }
                    }
                }

                // Step C: Pull encoded AAC from Encoder and write to Muxer
                int encOutIdx = encoder.dequeueOutputBuffer(encOutInfo, timeoutUs);
                if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer.addTrack(encoder.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                    }
                } else if (encOutIdx >= 0) {
                    if ((encOutInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encoder.releaseOutputBuffer(encOutIdx, false);
                        continue;
                    }

                    ByteBuffer encBuf = encoder.getOutputBuffer(encOutIdx);
                    if (encBuf != null && encOutInfo.size > 0 && muxerStarted) {
                        encBuf.position(encOutInfo.offset);
                        encBuf.limit(encOutInfo.offset + encOutInfo.size);
                        muxer.writeSampleData(muxerTrackIndex, encBuf, encOutInfo);
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false);

                    if ((encOutInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawEncOutputEOS = true;
                    }
                }
            }

            if (muxerStarted) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    Log.e(TAG, "muxer.stop error", e);
                }
            }

            if (listener != null) {
                mainHandler.post(() -> listener.onProgress(100));
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Streaming AAC encoding error", e);
            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;
        } finally {
            if (decoder != null) {
                try { decoder.stop(); decoder.release(); } catch (Exception ignored) {}
            }
            if (encoder != null) {
                try { encoder.stop(); encoder.release(); } catch (Exception ignored) {}
            }
            if (muxer != null) {
                try { muxer.release(); } catch (Exception ignored) {}
            }
            if (extractor != null) {
                try { extractor.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static void feedPcmToEncoder(MediaCodec encoder, byte[] pcmData, long ptsUs, boolean isEOS) {
        int offset = 0;
        int remaining = pcmData.length;

        while (remaining > 0 || isEOS) {
            int encInIdx = encoder.dequeueInputBuffer(1000);
            if (encInIdx >= 0) {
                ByteBuffer encInBuf = encoder.getInputBuffer(encInIdx);
                if (encInBuf != null) {
                    encInBuf.clear();
                    int toPut = Math.min(remaining, encInBuf.remaining());
                    if (toPut > 0) {
                        encInBuf.put(pcmData, offset, toPut);
                        offset += toPut;
                        remaining -= toPut;
                    }
                    int flags = (remaining == 0 && isEOS) ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
                    encoder.queueInputBuffer(encInIdx, 0, toPut, ptsUs, flags);
                    if (remaining == 0) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Decodes source media into standard 16-bit PCM with resampling and channel adaptation.
     */
    private static boolean decodeToPcm(File inputFile, File outPcmFile, int targetSampleRate, int targetChannels) throws IOException {
        return decodeToPcm(inputFile, outPcmFile, targetSampleRate, targetChannels, null);
    }

    private static boolean decodeToPcm(File inputFile, File outPcmFile, int targetSampleRate, int targetChannels, ConvertListener listener) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        BufferedOutputStream pcmOut = null;

        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int audioTrackIndex = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    format = trackFormat;
                    break;
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in: " + inputFile.getName());
                return false;
            }

            extractor.selectTrack(audioTrackIndex);
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 0;
            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            pcmOut = new BufferedOutputStream(new FileOutputStream(outPcmFile), 131072);

            int sourceSampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : targetSampleRate;
            int sourceChannels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : targetChannels;

            if (targetSampleRate <= 0) targetSampleRate = sourceSampleRate;
            if (targetChannels <= 0) targetChannels = sourceChannels;

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            long timeoutUs = 1000;
            int lastReportedPercent = 0;

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }

                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        byte[] chunk = new byte[bufferInfo.size];
                        outputBuffer.get(chunk);

                        // Process channel and sample rate conversion if needed
                        byte[] processed = adaptPcm(chunk, sourceSampleRate, sourceChannels, targetSampleRate, targetChannels);
                        pcmOut.write(processed);

                        if (durationUs > 0 && listener != null) {
                            int percent = (int) ((bufferInfo.presentationTimeUs * 75L) / durationUs);
                            if (percent > lastReportedPercent && percent <= 75) {
                                lastReportedPercent = percent;
                                int p = percent;
                                mainHandler.post(() -> listener.onProgress(p));
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                }
            }

            pcmOut.flush();
            return true;
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception ignored) {}
            }
            extractor.release();
            if (pcmOut != null) {
                try {
                    pcmOut.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Adapts PCM 16-bit samples for channels (Stereo <-> Mono) and sample rate.
     */
    private static byte[] adaptPcm(byte[] rawPcm, int inRate, int inChannels, int outRate, int outChannels) {
        if (inChannels == outChannels && inRate == outRate) {
            return rawPcm;
        }

        short[] inSamples = new short[rawPcm.length / 2];
        ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inSamples);

        // 1. Channel conversion
        short[] channelAdapted;
        if (inChannels == 2 && outChannels == 1) {
            // Stereo to Mono: average left and right
            int numFrames = inSamples.length / 2;
            channelAdapted = new short[numFrames];
            for (int i = 0; i < numFrames; i++) {
                int left = inSamples[i * 2];
                int right = inSamples[i * 2 + 1];
                channelAdapted[i] = (short) ((left + right) / 2);
            }
        } else if (inChannels == 1 && outChannels == 2) {
            // Mono to Stereo: duplicate
            int numFrames = inSamples.length;
            channelAdapted = new short[numFrames * 2];
            for (int i = 0; i < numFrames; i++) {
                channelAdapted[i * 2] = inSamples[i];
                channelAdapted[i * 2 + 1] = inSamples[i];
            }
        } else {
            channelAdapted = inSamples;
        }

        // 2. Sample rate conversion (Linear Interpolation)
        short[] resampled;
        if (inRate != outRate && inRate > 0 && outRate > 0) {
            int inFrames = channelAdapted.length / outChannels;
            int outFrames = (int) ((long) inFrames * outRate / inRate);
            resampled = new short[outFrames * outChannels];

            double ratio = (double) inRate / outRate;
            for (int ch = 0; ch < outChannels; ch++) {
                for (int i = 0; i < outFrames; i++) {
                    double srcPos = i * ratio;
                    int srcIndex = (int) srcPos;
                    double frac = srcPos - srcIndex;

                    short s1 = (srcIndex < inFrames) ? channelAdapted[srcIndex * outChannels + ch] : 0;
                    short s2 = (srcIndex + 1 < inFrames) ? channelAdapted[(srcIndex + 1) * outChannels + ch] : s1;

                    short val = (short) (s1 + frac * (s2 - s1));
                    resampled[i * outChannels + ch] = val;
                }
            }
        } else {
            resampled = channelAdapted;
        }

        ByteBuffer outBuffer = ByteBuffer.allocate(resampled.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : resampled) {
            outBuffer.putShort(s);
        }
        return outBuffer.array();
    }

    /**
     * Encodes 16-bit PCM to RIFF WAV by adding standard 44-byte WAV header.
     */
    private static boolean encodePcmToWav(File pcmFile, File wavFile, int sampleRate, int channels) {
        return encodePcmToWav(pcmFile, wavFile, sampleRate, channels, null);
    }

    private static boolean encodePcmToWav(File pcmFile, File wavFile, int sampleRate, int channels, ConvertListener listener) {
        try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(pcmFile), 65536);
             BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(wavFile), 65536)) {

            long totalAudioLen = pcmFile.length();
            long totalDataLen = totalAudioLen + 36;
            int bitsPerSample = 16;
            long byteRate = (long) sampleRate * channels * (bitsPerSample / 8);

            // 44-byte RIFF header
            byte[] header = new byte[44];
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            header[4] = (byte) (totalDataLen & 0xff);
            header[5] = (byte) ((totalDataLen >> 8) & 0xff);
            header[6] = (byte) ((totalDataLen >> 16) & 0xff);
            header[7] = (byte) ((totalDataLen >> 24) & 0xff);
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            header[16] = 16; // Subchunk1Size for PCM
            header[17] = 0; header[18] = 0; header[19] = 0;
            header[20] = 1; header[21] = 0; // AudioFormat 1 = PCM
            header[22] = (byte) channels;
            header[23] = 0;
            header[24] = (byte) (sampleRate & 0xff);
            header[25] = (byte) ((sampleRate >> 8) & 0xff);
            header[26] = (byte) ((sampleRate >> 16) & 0xff);
            header[27] = (byte) ((sampleRate >> 24) & 0xff);
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            header[32] = (byte) (channels * (bitsPerSample / 8)); // BlockAlign
            header[33] = 0;
            header[34] = (byte) bitsPerSample;
            header[35] = 0;
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            header[40] = (byte) (totalAudioLen & 0xff);
            header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
            header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
            header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

            fos.write(header, 0, 44);

            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;
            int lastPercent = 80;
            while ((read = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, read);
                totalRead += read;
                if (listener != null && totalAudioLen > 0) {
                    int p = 80 + (int) ((totalRead * 20L) / totalAudioLen);
                    if (p > lastPercent && p <= 100) {
                        lastPercent = p;
                        int percent = p;
                        mainHandler.post(() -> listener.onProgress(percent));
                    }
                }
            }
            fos.flush();
            if (listener != null) {
                mainHandler.post(() -> listener.onProgress(100));
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error encoding WAV", e);
            return false;
        }
    }

    /**
     * Encodes 16-bit PCM to AAC inside an MP4/M4A container using MediaCodec & MediaMuxer.
     */
    private static boolean encodePcmToAac(File pcmFile, File m4aFile, int sampleRate, int bitrateKbps, int channels) {
        return encodePcmToAac(pcmFile, m4aFile, sampleRate, bitrateKbps, channels, null);
    }

    private static boolean encodePcmToAac(File pcmFile, File m4aFile, int sampleRate, int bitrateKbps, int channels, ConvertListener listener) {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        BufferedInputStream fis = null;

        try {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(m4aFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int trackIndex = -1;
            boolean muxerStarted = false;

            fis = new BufferedInputStream(new FileInputStream(pcmFile), 65536);
            long totalPcmBytes = pcmFile.length();
            long bytesReadTotal = 0;
            int lastPercent = 75;

            byte[] buffer = new byte[4096];
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long presentationTimeUs = 0;
            long bytesPerSec = (long) sampleRate * channels * 2;

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inputBufferIndex = encoder.dequeueInputBuffer(1000);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            int read = fis.read(buffer);
                            if (read < 0) {
                                encoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                bytesReadTotal += read;
                                inputBuffer.put(buffer, 0, read);
                                encoder.queueInputBuffer(inputBufferIndex, 0, read, presentationTimeUs, 0);
                                presentationTimeUs += (read * 1000000L) / bytesPerSec;

                                if (listener != null && totalPcmBytes > 0) {
                                    int p = 75 + (int) ((bytesReadTotal * 25L) / totalPcmBytes);
                                    if (p > lastPercent && p <= 100) {
                                        lastPercent = p;
                                        int percent = p;
                                        mainHandler.post(() -> listener.onProgress(percent));
                                    }
                                }
                            }
                        }
                    }
                }

                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 1000);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw new RuntimeException("Format changed twice in AAC encoder");
                    }
                    trackIndex = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                } else if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }
                }
            }
            if (listener != null) {
                mainHandler.post(() -> listener.onProgress(100));
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error encoding AAC", e);
            return false;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
            if (encoder != null) {
                try {
                    encoder.stop();
                    encoder.release();
                } catch (Exception ignored) {}
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                    muxer.release();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Fallback or direct FLAC encoding via WAV RIFF header or MediaCodec.
     */
    private static boolean encodePcmToFlac(File pcmFile, File flacFile, int sampleRate, int channels) {
        // Many Android devices do not have hardware FLAC encoder, so encode WAV or fallback
        return encodePcmToWav(pcmFile, flacFile, sampleRate, channels);
    }

    public static class SourceAudioInfo {
        public int bitrateKbps = 192;
        public int sampleRate = 44100;
        public int channels = 2;
    }

    public static SourceAudioInfo getSourceAudioInfo(File file) {
        SourceAudioInfo info = new SourceAudioInfo();
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        info.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        info.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        int br = format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000;
                        if (br > 16) info.bitrateKbps = br;
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
        finally {
            try { extractor.release(); } catch (Exception ignored) {}
        }

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String brStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (brStr != null) {
                int br = Integer.parseInt(brStr) / 1000;
                if (br > 16) info.bitrateKbps = br;
            }
            String srStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
            if (srStr != null) {
                int sr = Integer.parseInt(srStr);
                if (sr > 0) info.sampleRate = sr;
            }
        } catch (Exception ignored) {}
        finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return info;
    }

    /**
     * Trims an audio file between startMs and endMs.
     * Prioritizes lossless direct frame slicing (MP3, WAV, M4A/MP4/WebM/OGG) to preserve
     * exact bitrate and quality, finishing in milliseconds.
     */
    public static void trimAudio(
            Context context,
            File inputFile,
            File outputFile,
            long startMs,
            long endMs,
            ConvertListener listener
    ) {
        executor.execute(() -> {
            String inName = inputFile.getName().toLowerCase(Locale.ENGLISH);
            String outName = outputFile.getName().toLowerCase(Locale.ENGLISH);

            // Auto-preserve source extension if omitted
            File resolvedOutputFile = outputFile;
            if (!outName.contains(".")) {
                int dot = inName.lastIndexOf('.');
                if (dot > 0) {
                    String ext = inName.substring(dot);
                    outName = outName + ext;
                    resolvedOutputFile = new File(outputFile.getParentFile(), outputFile.getName() + ext);
                }
            }

            final File finalOutputFile = resolvedOutputFile;
            File tempPcmFile = null;
            try {
                if (startMs < 0 || endMs <= startMs) {
                    throw new IllegalArgumentException("Start time must be less than end time");
                }

                // 1. Lossless MP3 Frame Trimming (Instant, same bitrate and format!)
                if (outName.endsWith(".mp3") && inName.endsWith(".mp3")) {
                    boolean success = Mp3FastTrimmer.trimMp3(inputFile, finalOutputFile, startMs, endMs);
                    if (success && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                        return;
                    }
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                }

                // 2. Lossless WAV Direct Sample Trimming (Instant, same bitrate and format!)
                if (outName.endsWith(".wav") && inName.endsWith(".wav")) {
                    boolean success = WavFastTrimmer.trimWav(inputFile, finalOutputFile, startMs, endMs);
                    if (success && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                        return;
                    }
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                }

                // 3. Lossless container muxer trim for MP4/M4A/WebM/OGG (Instant, same bitrate!)
                if (outName.endsWith(".m4a") || outName.endsWith(".mp4") || outName.endsWith(".webm") || outName.endsWith(".ogg")) {
                    boolean muxerSuccess = MediaSplitter.extractMediaSegment(inputFile, finalOutputFile, startMs * 1000L, endMs * 1000L);
                    if (muxerSuccess && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                        return;
                    }
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                }

                // 4. Fallback for cross-container conversions: Use EXACT source bitrate and sample rate!
                SourceAudioInfo sourceInfo = getSourceAudioInfo(inputFile);
                int sampleRate = sourceInfo.sampleRate;
                int channels = sourceInfo.channels;
                int targetBitrate = sourceInfo.bitrateKbps;

                tempPcmFile = new File(context.getCacheDir(), "temp_trim_fallback_" + System.currentTimeMillis() + ".pcm");
                boolean decoded = decodePcmSegment(inputFile, tempPcmFile, startMs, endMs, sampleRate, channels, false);
                if (!decoded || !tempPcmFile.exists() || tempPcmFile.length() == 0) {
                    throw new IOException("Failed to decode audio segment");
                }

                boolean success;
                if (outName.endsWith(".wav")) {
                    success = encodePcmToWav(tempPcmFile, finalOutputFile, sampleRate, channels);
                } else if (outName.endsWith(".m4a") || outName.endsWith(".aac")) {
                    success = encodePcmToAac(tempPcmFile, finalOutputFile, sampleRate, targetBitrate, channels);
                } else {
                    success = nativeEncodePcmToMp3(
                            tempPcmFile.getAbsolutePath(),
                            finalOutputFile.getAbsolutePath(),
                            sampleRate,
                            targetBitrate,
                            channels
                    );
                }

                if (success && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                    mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                } else {
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                    mainHandler.post(() -> listener.onError("Trim encoding failed"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Audio trim error", e);
                if (finalOutputFile != null && finalOutputFile.exists() && finalOutputFile.length() == 0) {
                    //noinspection ResultOfMethodCallIgnored
                    finalOutputFile.delete();
                }
                mainHandler.post(() -> listener.onError(e.getMessage() != null ? e.getMessage() : "Trimming failed"));
            } finally {
                if (tempPcmFile != null && tempPcmFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempPcmFile.delete();
                }
            }
        });
    }

    /**
     * Deletes the marked segment [startMs, endMs] from the audio file.
     * Prioritizes lossless slicing (MP3, WAV) to finish in milliseconds and preserve exact bitrate.
     */
    public static void deleteAudioSegment(
            Context context,
            File inputFile,
            File outputFile,
            long startMs,
            long endMs,
            long totalDurationMs,
            ConvertListener listener
    ) {
        executor.execute(() -> {
            String inName = inputFile.getName().toLowerCase(Locale.ENGLISH);
            String outName = outputFile.getName().toLowerCase(Locale.ENGLISH);

            // Auto-preserve source extension if omitted
            File resolvedOutputFile = outputFile;
            if (!outName.contains(".")) {
                int dot = inName.lastIndexOf('.');
                if (dot > 0) {
                    String ext = inName.substring(dot);
                    outName = outName + ext;
                    resolvedOutputFile = new File(outputFile.getParentFile(), outputFile.getName() + ext);
                }
            }

            final File finalOutputFile = resolvedOutputFile;
            File tempPcmFile = null;
            try {
                if (startMs < 0 || endMs <= startMs) {
                    throw new IllegalArgumentException("Start time must be less than end time");
                }

                // 1. Lossless MP3 Segment Deletion (Instant, preserves source bitrate)
                if (outName.endsWith(".mp3") && inName.endsWith(".mp3")) {
                    boolean success = Mp3FastTrimmer.deleteMp3Segment(inputFile, finalOutputFile, startMs, endMs);
                    if (success && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                        return;
                    }
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                }

                // 2. Lossless WAV Segment Deletion (Instant, preserves source bitrate)
                if (outName.endsWith(".wav") && inName.endsWith(".wav")) {
                    boolean success = WavFastTrimmer.deleteWavSegment(inputFile, finalOutputFile, startMs, endMs);
                    if (success && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                        return;
                    }
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                }

                // 3. Lossless container muxer segment deletion for MP4/M4A/WebM/OGG (Instant, same bitrate!)
                if (outName.endsWith(".m4a") || outName.endsWith(".mp4") || outName.endsWith(".webm") || outName.endsWith(".ogg")) {
                    boolean muxerSuccess = MediaSplitter.deleteMediaSegment(inputFile, finalOutputFile, startMs * 1000L, endMs * 1000L);
                    if (muxerSuccess && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                        mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                        return;
                    }
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                }

                // 3. Fallback: Use master file's exact bitrate and sample rate
                SourceAudioInfo sourceInfo = getSourceAudioInfo(inputFile);
                int sampleRate = sourceInfo.sampleRate;
                int channels = sourceInfo.channels;
                int targetBitrate = sourceInfo.bitrateKbps;

                tempPcmFile = new File(context.getCacheDir(), "temp_delete_fallback_" + System.currentTimeMillis() + ".pcm");

                // Part 1: from 0 up to startMs (if startMs > 0)
                if (startMs > 0) {
                    decodePcmSegment(inputFile, tempPcmFile, 0, startMs, sampleRate, channels, false);
                }

                // Part 2: from endMs up to totalDurationMs (if endMs < totalDurationMs)
                if (endMs < totalDurationMs) {
                    boolean append = (startMs > 0 && tempPcmFile.exists() && tempPcmFile.length() > 0);
                    decodePcmSegment(inputFile, tempPcmFile, endMs, totalDurationMs, sampleRate, channels, append);
                }

                if (!tempPcmFile.exists() || tempPcmFile.length() == 0) {
                    throw new IOException("Resulting audio is empty after deletion");
                }

                boolean success;
                if (outName.endsWith(".wav")) {
                    success = encodePcmToWav(tempPcmFile, finalOutputFile, sampleRate, channels);
                } else if (outName.endsWith(".m4a") || outName.endsWith(".aac")) {
                    success = encodePcmToAac(tempPcmFile, finalOutputFile, sampleRate, targetBitrate, channels);
                } else {
                    success = nativeEncodePcmToMp3(
                            tempPcmFile.getAbsolutePath(),
                            finalOutputFile.getAbsolutePath(),
                            sampleRate,
                            targetBitrate,
                            channels
                    );
                }

                if (success && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                    mainHandler.post(() -> listener.onSuccess(finalOutputFile));
                } else {
                    if (finalOutputFile.exists() && finalOutputFile.length() == 0) {
                        //noinspection ResultOfMethodCallIgnored
                        finalOutputFile.delete();
                    }
                    mainHandler.post(() -> listener.onError("Delete encoding failed"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Audio delete segment error", e);
                if (finalOutputFile != null && finalOutputFile.exists() && finalOutputFile.length() == 0) {
                    //noinspection ResultOfMethodCallIgnored
                    finalOutputFile.delete();
                }
                mainHandler.post(() -> listener.onError(e.getMessage() != null ? e.getMessage() : "Deleting segment failed"));
            } finally {
                if (tempPcmFile != null && tempPcmFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempPcmFile.delete();
                }
            }
        });
    }

    /**
     * Decodes only the requested time range [startMs, endMs] directly to a PCM file.
     * Uses MediaExtractor seek for near-instant access, skipping decoding unnecessary audio.
     */
    private static boolean decodePcmSegment(
            File inputFile,
            File outPcmFile,
            long startMs,
            long endMs,
            int targetSampleRate,
            int targetChannels,
            boolean append
    ) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        FileOutputStream pcmOut = null;

        try {
            extractor.setDataSource(inputFile.getAbsolutePath());
            int audioTrackIndex = -1;
            MediaFormat format = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    format = trackFormat;
                    break;
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in: " + inputFile.getName());
                return false;
            }

            extractor.selectTrack(audioTrackIndex);

            // Fast seek to start mark
            if (startMs > 0) {
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }

            String mime = format.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            pcmOut = new FileOutputStream(outPcmFile, append);

            int sourceSampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : targetSampleRate;
            int sourceChannels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : targetChannels;

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            long timeoutUs = 5000;

            long startUs = startMs * 1000L;
            long endUs = (endMs > 0) ? endMs * 1000L : Long.MAX_VALUE;

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            long sampleTime = extractor.getSampleTime();

                            if (sampleSize < 0 || (endMs > 0 && sampleTime > (endMs + 1000L) * 1000L)) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }

                    long timeUs = bufferInfo.presentationTimeUs;

                    if (bufferInfo.size > 0 && sourceSampleRate > 0 && sourceChannels > 0) {
                        long bufferDurationUs = ((long) bufferInfo.size * 1000000L) / ((long) sourceSampleRate * sourceChannels * 2);

                        if (timeUs + bufferDurationUs <= startUs) {
                            // Entire buffer is before start time, discard
                            decoder.releaseOutputBuffer(outputBufferIndex, false);
                            continue;
                        }
                        if (timeUs >= endUs) {
                            // Reached end of requested segment, stop decoding immediately!
                            decoder.releaseOutputBuffer(outputBufferIndex, false);
                            sawOutputEOS = true;
                            break;
                        }

                        ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                        if (outputBuffer != null) {
                            int offset = bufferInfo.offset;
                            int size = bufferInfo.size;

                            // Trim leading samples if buffer starts before startUs
                            if (timeUs < startUs) {
                                long skipUs = startUs - timeUs;
                                int skipBytes = (int) ((skipUs * (long) sourceSampleRate * sourceChannels * 2) / 1000000L);
                                int frameSize = sourceChannels * 2;
                                skipBytes = (skipBytes / frameSize) * frameSize;
                                if (skipBytes > size) skipBytes = size;
                                offset += skipBytes;
                                size -= skipBytes;
                            }

                            // Trim trailing samples if buffer extends past endUs
                            if (timeUs + bufferDurationUs > endUs && endMs > 0) {
                                long keepUs = endUs - Math.max(timeUs, startUs);
                                int keepBytes = (int) ((keepUs * (long) sourceSampleRate * sourceChannels * 2) / 1000000L);
                                int frameSize = sourceChannels * 2;
                                keepBytes = (keepBytes / frameSize) * frameSize;
                                if (keepBytes < size) size = keepBytes;
                            }

                            if (size > 0) {
                                outputBuffer.position(offset);
                                outputBuffer.limit(offset + size);

                                byte[] chunk = new byte[size];
                                outputBuffer.get(chunk);

                                byte[] processed = adaptPcm(chunk, sourceSampleRate, sourceChannels, targetSampleRate, targetChannels);
                                pcmOut.write(processed);
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                }
            }

            pcmOut.flush();
            return true;
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception ignored) {}
            }
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Exception ignored) {}
            }
            if (pcmOut != null) {
                try {
                    pcmOut.close();
                } catch (Exception ignored) {}
            }
        }
    }
}

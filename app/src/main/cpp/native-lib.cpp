#include <jni.h>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
extern "C" {
#include "layer3.h"
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kts_fileexplorer_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from KTS File Explorer C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kts_fileexplorer_util_AudioConverter_nativeEncodePcmToMp3(
        JNIEnv* env,
        jclass /* clazz */,
        jstring pcmPathStr,
        jstring mp3PathStr,
        jint sampleRate,
        jint bitrateKbps,
        jint channels) {
    const char *pcmPath = env->GetStringUTFChars(pcmPathStr, nullptr);
    const char *mp3Path = env->GetStringUTFChars(mp3PathStr, nullptr);

    FILE *inPcm = fopen(pcmPath, "rb");
    if (!inPcm) {
        env->ReleaseStringUTFChars(pcmPathStr, pcmPath);
        env->ReleaseStringUTFChars(mp3PathStr, mp3Path);
        return JNI_FALSE;
    }

    FILE *outMp3 = fopen(mp3Path, "wb");
    if (!outMp3) {
        fclose(inPcm);
        env->ReleaseStringUTFChars(pcmPathStr, pcmPath);
        env->ReleaseStringUTFChars(mp3PathStr, mp3Path);
        return JNI_FALSE;
    }

    shine_config_t config;
    shine_set_config_mpeg_defaults(&config.mpeg);
    config.wave.samplerate = sampleRate;
    config.wave.channels = (channels == 1) ? PCM_MONO : PCM_STEREO;
    config.mpeg.bitr = bitrateKbps;
    config.mpeg.mode = (channels == 1) ? MONO : STEREO;

    if (shine_check_config(config.wave.samplerate, config.mpeg.bitr) < 0) {
        if (config.wave.samplerate != 44100 && config.wave.samplerate != 48000 && config.wave.samplerate != 32000) {
            config.wave.samplerate = 44100;
        }
        if (config.mpeg.bitr < 32 || config.mpeg.bitr > 320) {
            config.mpeg.bitr = 128;
        }
    }

    shine_t s = shine_initialise(&config);
    if (!s) {
        fclose(inPcm);
        fclose(outMp3);
        env->ReleaseStringUTFChars(pcmPathStr, pcmPath);
        env->ReleaseStringUTFChars(mp3PathStr, mp3Path);
        return JNI_FALSE;
    }

    int samples_per_pass = shine_samples_per_pass(s);
    int total_shorts = samples_per_pass * channels;
    int16_t *buffer = (int16_t *)malloc(total_shorts * sizeof(int16_t));

    int written = 0;
    size_t read_count = 0;
    while ((read_count = fread(buffer, sizeof(int16_t), total_shorts, inPcm)) > 0) {
        if (read_count < (size_t)total_shorts) {
            for (size_t i = read_count; i < (size_t)total_shorts; i++) {
                buffer[i] = 0;
            }
        }
        unsigned char *data = shine_encode_buffer_interleaved(s, buffer, &written);
        if (written > 0 && data) {
            fwrite(data, 1, written, outMp3);
        }
    }

    unsigned char *data = shine_flush(s, &written);
    if (written > 0 && data) {
        fwrite(data, 1, written, outMp3);
    }

    free(buffer);
    shine_close(s);
    fclose(inPcm);
    fclose(outMp3);

    env->ReleaseStringUTFChars(pcmPathStr, pcmPath);
    env->ReleaseStringUTFChars(mp3PathStr, mp3Path);
    return JNI_TRUE;
}

struct Mp3StreamEncoder {
    shine_t s;
    FILE *outMp3;
    int channels;
    int samples_per_pass;
    int total_shorts;
    int16_t *buffer;
    int buffer_fill;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_kts_fileexplorer_util_AudioConverter_nativeOpenMp3Stream(
        JNIEnv* env,
        jclass /* clazz */,
        jstring mp3PathStr,
        jint sampleRate,
        jint bitrateKbps,
        jint channels) {
    const char *mp3Path = env->GetStringUTFChars(mp3PathStr, nullptr);
    FILE *outMp3 = fopen(mp3Path, "wb");
    env->ReleaseStringUTFChars(mp3PathStr, mp3Path);
    if (!outMp3) {
        return 0;
    }

    shine_config_t config;
    shine_set_config_mpeg_defaults(&config.mpeg);
    config.wave.samplerate = sampleRate;
    config.wave.channels = (channels == 1) ? PCM_MONO : PCM_STEREO;
    config.mpeg.bitr = bitrateKbps;
    config.mpeg.mode = (channels == 1) ? MONO : STEREO;

    if (shine_check_config(config.wave.samplerate, config.mpeg.bitr) < 0) {
        if (config.wave.samplerate != 44100 && config.wave.samplerate != 48000 && config.wave.samplerate != 32000) {
            config.wave.samplerate = 44100;
        }
        if (config.mpeg.bitr < 32 || config.mpeg.bitr > 320) {
            config.mpeg.bitr = 128;
        }
    }

    shine_t s = shine_initialise(&config);
    if (!s) {
        fclose(outMp3);
        return 0;
    }

    auto *enc = new Mp3StreamEncoder();
    enc->s = s;
    enc->outMp3 = outMp3;
    enc->channels = channels;
    enc->samples_per_pass = shine_samples_per_pass(s);
    enc->total_shorts = enc->samples_per_pass * channels;
    enc->buffer = (int16_t *)malloc(enc->total_shorts * sizeof(int16_t));
    enc->buffer_fill = 0;

    return reinterpret_cast<jlong>(enc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_kts_fileexplorer_util_AudioConverter_nativeWriteMp3Stream(
        JNIEnv* env,
        jclass /* clazz */,
        jlong handle,
        jbyteArray pcmChunk,
        jint offset,
        jint length) {
    if (!handle || length <= 0) return;
    auto *enc = reinterpret_cast<Mp3StreamEncoder *>(handle);

    jbyte *bytes = env->GetByteArrayElements(pcmChunk, nullptr);
    if (!bytes) return;

    const int16_t *samples = reinterpret_cast<const int16_t *>(bytes + offset);
    int num_samples = length / sizeof(int16_t);

    int pos = 0;
    while (pos < num_samples) {
        int needed = enc->total_shorts - enc->buffer_fill;
        int available = num_samples - pos;
        int to_copy = (available < needed) ? available : needed;

        memcpy(enc->buffer + enc->buffer_fill, samples + pos, to_copy * sizeof(int16_t));
        enc->buffer_fill += to_copy;
        pos += to_copy;

        if (enc->buffer_fill == enc->total_shorts) {
            int written = 0;
            unsigned char *data = shine_encode_buffer_interleaved(enc->s, enc->buffer, &written);
            if (written > 0 && data) {
                fwrite(data, 1, written, enc->outMp3);
            }
            enc->buffer_fill = 0;
        }
    }

    env->ReleaseByteArrayElements(pcmChunk, bytes, JNI_ABORT);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kts_fileexplorer_util_AudioConverter_nativeCloseMp3Stream(
        JNIEnv* env,
        jclass /* clazz */,
        jlong handle) {
    if (!handle) return JNI_FALSE;
    auto *enc = reinterpret_cast<Mp3StreamEncoder *>(handle);

    if (enc->buffer_fill > 0) {
        for (int i = enc->buffer_fill; i < enc->total_shorts; i++) {
            enc->buffer[i] = 0;
        }
        int written = 0;
        unsigned char *data = shine_encode_buffer_interleaved(enc->s, enc->buffer, &written);
        if (written > 0 && data) {
            fwrite(data, 1, written, enc->outMp3);
        }
    }

    int written = 0;
    unsigned char *data = shine_flush(enc->s, &written);
    if (written > 0 && data) {
        fwrite(data, 1, written, enc->outMp3);
    }

    free(enc->buffer);
    shine_close(enc->s);
    fclose(enc->outMp3);
    delete enc;

    return JNI_TRUE;
}
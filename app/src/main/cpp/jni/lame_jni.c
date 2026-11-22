/*
 * JNI front to libmp3lame for Mp3Encoder.kt.
 *
 * One session is one lame_global_flags pointer passed to Kotlin as a jlong.
 * PCM goes in as interleaved native-endian 16-bit samples (what MediaCodec's
 * audio decoders emit), encoded frames come back as byte arrays. Nothing is
 * buffered here beyond what LAME itself holds back between encode calls.
 */
#include <jni.h>
#include <stdlib.h>
#include "lame.h"

/* LAME's own rule of thumb for the worst-case frame size. */
static size_t mp3_buffer_for(int pcm_samples) {
    return (size_t)(1.25 * pcm_samples) + 7200;
}

JNIEXPORT jlong JNICALL Java_com_music_bitchord_download_Mp3Encoder_nativeInit(
    JNIEnv *env, jclass clazz, jint sample_rate, jint channels, jint bitrate_kbps) {
    (void)env; (void)clazz;
    lame_t gfp = lame_init();
    if (gfp == NULL) return 0;
    lame_set_in_samplerate(gfp, sample_rate);
    lame_set_num_channels(gfp, channels);
    lame_set_mode(gfp, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_brate(gfp, bitrate_kbps);
    /* 2 is LAME's recommended quality/speed balance for CBR music. */
    lame_set_quality(gfp, 2);
    /* The app writes its own ID3v2 tag (Mp3Tagger.kt), so LAME must add no
       tag of its own: no Xing/Info frame (reserved at the start and, the way
       this stream is encoded, never rewritten - a blank frame) and no ID3
       block appended at flush. */
    lame_set_bWriteVbrTag(gfp, 0);
    lame_set_write_id3tag_automatic(gfp, 0);
    if (lame_init_params(gfp) < 0) {
        lame_close(gfp);
        return 0;
    }
    return (jlong)(intptr_t)gfp;
}

JNIEXPORT jbyteArray JNICALL Java_com_music_bitchord_download_Mp3Encoder_nativeEncode(
    JNIEnv *env, jclass clazz, jlong handle, jshortArray pcm, jint short_count) {
    (void)clazz;
    lame_t gfp = (lame_t)(intptr_t)handle;
    if (gfp == NULL || pcm == NULL || short_count <= 0) return NULL;

    jshort *samples = (*env)->GetShortArrayElements(env, pcm, NULL);
    if (samples == NULL) return NULL;

    int channels = lame_get_num_channels(gfp);
    int frames = channels == 2 ? short_count / 2 : short_count;
    size_t out_size = mp3_buffer_for(frames);
    unsigned char *out = (unsigned char *)malloc(out_size);
    if (out == NULL) {
        (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
        return NULL;
    }

    int written;
    if (channels == 2) {
        written = lame_encode_buffer_interleaved(gfp, samples, frames, out, (int)out_size);
    } else {
        written = lame_encode_buffer(gfp, samples, NULL, frames, out, (int)out_size);
    }
    (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
    if (written < 0) {
        free(out);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, written);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, written, (jbyte *)out);
    }
    free(out);
    return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_music_bitchord_download_Mp3Encoder_nativeFlush(
    JNIEnv *env, jclass clazz, jlong handle) {
    (void)clazz;
    lame_t gfp = (lame_t)(intptr_t)handle;
    if (gfp == NULL) return NULL;

    size_t out_size = mp3_buffer_for(8192);
    unsigned char *out = (unsigned char *)malloc(out_size);
    if (out == NULL) return NULL;

    int written = lame_encode_flush(gfp, out, (int)out_size);
    if (written < 0) {
        free(out);
        return NULL;
    }

    jbyteArray result = (*env)->NewByteArray(env, written);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, written, (jbyte *)out);
    }
    free(out);
    return result;
}

JNIEXPORT void JNICALL Java_com_music_bitchord_download_Mp3Encoder_nativeClose(
    JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    lame_t gfp = (lame_t)(intptr_t)handle;
    if (gfp != NULL) lame_close(gfp);
}

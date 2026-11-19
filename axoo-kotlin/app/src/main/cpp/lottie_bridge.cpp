/*
 * lottie_bridge.cpp
 *
 * Wrapper JNI per integrare il motore C++ rlottie con Android.
 * Gestisce il ciclo di vita dell'animazione nativa e il rendering 
 * diretto sulla memoria (pixel) di una Bitmap Java, garantendo thread-safety.
 */
#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <cstdlib>
#include <cstdint>
#include <memory>
#include <mutex>
#include "rlottie.h"

// Codici di ritorno per comunicare l'esito delle operazioni a Kotlin
enum LottieRenderResult {
    AXOO_RENDER_SUCCESS = 0,
    AXOO_RENDER_INVALID_POINTER = -1,
    AXOO_RENDER_INVALID_BITMAP = -2,
    AXOO_RENDER_INVALID_FRAME = -3,
    AXOO_RENDER_BUSY = -5,
    AXOO_RENDER_NATIVE_ERROR = -6
};

// Struttura che incapsula lo stato di una singola animazione in memoria.
struct LottieInfo {
    // Puntatore smart: distrugge in automatico l'oggetto Animation quando LottieInfo viene eliminato.
    std::unique_ptr<rlottie::Animation> animation;
    // Mutex per assicurare che un solo thread alla volta possa renderizzare frame di questa animazione.
    std::mutex renderMutex;
    
    // Metadati dell'animazione estratti al caricamento
    int width = 0;
    int height = 0;
    int frameCount = 0;
    float fps = 0.0f;
    bool destroyed = false;
};

// ==============================================================================
// 1. UTILITY INTERNE
// ==============================================================================

// Converte il riferimento generico jlong arrivato da JNI nel reale puntatore C++ (LottieInfo)
static LottieInfo *asInfo(jlong ptr) {
    return reinterpret_cast<LottieInfo *>(ptr);
}

// Interroga la libreria ufficiale rlottie per leggere i dati JSON e creare l'oggetto Animation.
static std::unique_ptr<rlottie::Animation> loadAnimationFromData(
        const std::string &json,
        const std::string &key) {
    return rlottie::Animation::loadFromData(json, key, nullptr, rlottie::FitzModifier::None);
}

// Esegue materialmente il rendering del singolo frame chiamando il metodo sincrono della libreria.
static int renderAnimationFrame(
        const std::unique_ptr<rlottie::Animation> &animation,
        int frameNum,
        rlottie::Surface &surface) {
    bool result = false;
    animation->renderSync((size_t) frameNum, surface, true, &result);
    // Traduce l'esito interno della libreria nel nostro codice di ritorno personalizzato
    return result ? AXOO_RENDER_SUCCESS : AXOO_RENDER_BUSY;
}

// ==============================================================================
// 2. FUNZIONI JNI (Esportate verso Kotlin)
// ==============================================================================

extern "C" {

// Funzione interna per caricare l'animazione e preparare l'oggetto LottieInfo.
static jlong createFromDataInternal(
        JNIEnv *env,
        jstring jsonStr,
        jstring keyStr) {
    if (jsonStr == nullptr || keyStr == nullptr) return 0;

    // Converte le stringhe Java in array di caratteri (C-style) leggibili dal C++
    const char *json = env->GetStringUTFChars(jsonStr, nullptr);
    if (json == nullptr) return 0;
    
    const char *key = env->GetStringUTFChars(keyStr, nullptr);
    if (key == nullptr) {
        env->ReleaseStringUTFChars(jsonStr, json);
        return 0;
    }

    // Costruisce l'animazione rlottie
    auto anim = loadAnimationFromData(std::string(json), std::string(key));

    // Libera la memoria temporanea allocata per le stringhe
    env->ReleaseStringUTFChars(jsonStr, json);
    env->ReleaseStringUTFChars(keyStr, key);

    if (!anim) return 0;

    // Estrae i metadati essenziali (dimensioni) passandoli per reference
    size_t w = 0, h = 0;
    anim->size(w, h);

    // Crea un'istanza persistente di LottieInfo sulla heap (sopravvive alla fine di questa funzione)
    auto *info = new LottieInfo();
    info->width = static_cast<int>(w);
    info->height = static_cast<int>(h);
    info->frameCount = static_cast<int>(anim->totalFrame());
    info->fps = static_cast<float>(anim->frameRate());
    info->animation = std::move(anim); // info->animation acquisisce l'ownership dell'oggetto anim

    // Maschera il puntatore come un numero lungo (jlong) per passarlo a Kotlin
    return reinterpret_cast<jlong>(info);
}

// Chiamata da Kotlin per creare una nuova istanza dell'animazione
JNIEXPORT jlong JNICALL
Java_com_axoo_engine_lottie_RLottieLib_createFromData(
        JNIEnv *env, jclass, jstring jsonStr, jstring keyStr) {
    return createFromDataInternal(env, jsonStr, keyStr);
}

// Chiamata da Kotlin quando l'animazione non serve più. Si assicura di deallocare la memoria C++.
JNIEXPORT void JNICALL
Java_com_axoo_engine_lottie_RLottieLib_destroy(
        JNIEnv *, jclass, jlong ptr) {
    auto *info = asInfo(ptr);
    if (!info) return;

    {
        // Ottiene un lock temporaneo per evitare di distruggere l'oggetto mentre è in corso un rendering.
        std::lock_guard<std::mutex> lock(info->renderMutex);
        info->destroyed = true;
        // reset() svuota l'unique_ptr, forzando la distruzione immediata dell'oggetto rlottie::Animation
        info->animation.reset();
    } // Qui il lock_guard esce di scope e rilascia il mutex
    
    // Dealloca la struttura contenitore liberando definitivamente la memoria
    delete info;
}

// Fornisce a Kotlin le informazioni base per preparare UI e Timer: [frameCount, fps, width, height]
JNIEXPORT jfloatArray JNICALL
Java_com_axoo_engine_lottie_RLottieLib_getInfo(
        JNIEnv *env, jclass, jlong ptr) {
    auto *infoPtr = asInfo(ptr);
    if (!infoPtr) return nullptr;

    // Array di appoggio locale (in C++)
    float info[4] = {
        (float) infoPtr->frameCount,
        infoPtr->fps,
        (float) infoPtr->width,
        (float) infoPtr->height
    };

    // Alloca un array analogo nello spazio di memoria Java e vi copia i dati
    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, 4, info);
    
    return result;
}

// Calcola un singolo frame e lo dipinge direttamente sulla Bitmap fornita.
JNIEXPORT jint JNICALL
Java_com_axoo_engine_lottie_RLottieLib_nativeRenderFrame(
        JNIEnv *env, jclass, jlong ptr, jint frameNum, jobject bitmap) {

    auto *info = asInfo(ptr);
    
    // Controlli di integrità iniziali: pointer valido, animazione attiva, bitmap presente e frame coerente
    if (!info || info->destroyed || !info->animation) return AXOO_RENDER_INVALID_POINTER;
    if (!bitmap) return AXOO_RENDER_INVALID_BITMAP;
    if (frameNum < 0 || frameNum >= info->frameCount) return AXOO_RENDER_INVALID_FRAME;

    // Se un altro thread è già nel blocco di rendering per questa animazione,
    // usciamo immediatamente restituendo BUSY anziché aspettare (per non causare lag nell'UI).
    if (!info->renderMutex.try_lock()) {
        return AXOO_RENDER_BUSY;
    }
    
    // Deleghiamo a unique_lock la responsabilità di rilasciare (unlock) automaticamente
    // il mutex alla fine della funzione. adopt_lock segnala che l'abbiamo già acquisito sopra.
    std::unique_lock<std::mutex> renderLock(info->renderMutex, std::adopt_lock);
    
    // Ricontrollo nel caso l'oggetto sia stato marcato per la distruzione mentre aspettavamo
    if (info->destroyed || !info->animation) {
        return AXOO_RENDER_INVALID_POINTER;
    }

    // Richiede ad Android la struttura dati (AndroidBitmapInfo) della nostra Bitmap Java
    AndroidBitmapInfo bitmapInfo;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) < 0) return AXOO_RENDER_INVALID_BITMAP;
    
    // Accetta solo immagini nel formato colori RGBA_8888 e dimensioni valide
    if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || bitmapInfo.width <= 0 || bitmapInfo.height <= 0) {
        return AXOO_RENDER_INVALID_BITMAP;
    }

    // lockPixels ci fornisce il puntatore ("pixels") diretto alla memoria in cui "dipingere"
    // e allo stesso tempo impedisce ad Android di spostare la Bitmap in memoria
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return AXOO_RENDER_INVALID_BITMAP;

    // Creiamo un wrapper Surface di rlottie, dicendogli di usare la memoria 
    // della nostra Bitmap come se fosse una tela
    rlottie::Surface surface(
            (uint32_t *) pixels,
            bitmapInfo.width,
            bitmapInfo.height,
            bitmapInfo.stride
    );

    try {
        // Lanciamo il motore rlottie
        const int renderResult = renderAnimationFrame(info->animation, frameNum, surface);
        if (renderResult != AXOO_RENDER_SUCCESS) {
            // Se fallisce, dobbiamo comunque sbloccare la Bitmap prima di uscire
            AndroidBitmap_unlockPixels(env, bitmap);
            return renderResult;
        }
    } catch (...) {
        // Gestione di eventuali eccezioni native impreviste
        AndroidBitmap_unlockPixels(env, bitmap);
        return AXOO_RENDER_NATIVE_ERROR;
    }

    // Rendering completato. Rilasciamo la Bitmap così che Java possa utilizzarla (es. a schermo).
    AndroidBitmap_unlockPixels(env, bitmap);
    return AXOO_RENDER_SUCCESS;
}

} // extern "C"

#ifndef AXOO_LOTTIE_H
#define AXOO_LOTTIE_H
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

typedef struct AXAnimation AXAnimation;
typedef struct AXAnimationInfo {
    uint32_t width, height, frame_count;
    double frames_per_second;
} AXAnimationInfo;

enum AXAnimationResult {
    AXAnimationOK = 0,
    AXAnimationInvalidInput = 1,
    AXAnimationInvalidFrame = 2,
    AXAnimationRenderFailed = 3
};

// Accepts bundled gzip TGS or JSON. Input <= 2 MiB, decompressed JSON <= 4 MiB.
// An owned handle is returned, or NULL. No C++ exception crosses this API.
AXAnimation *AXAnimationCreate(const uint8_t *data, size_t count);
AXAnimationInfo AXAnimationGetInfo(AXAnimation *animation);
// Writes premultiplied RGBA bytes (0xAABBGGRR words on little endian).
// This Telegram fork differs from the format described by upstream rlottie.h.
// Calls on the same handle are serialized. Caller retains handle throughout call.
int AXAnimationRender(AXAnimation *animation, uint32_t frame, uint32_t width,
                      uint32_t height, uint8_t *pixels, size_t count);
// Owner must wait for all calls to finish before destroying; never use afterwards.
void AXAnimationDestroy(AXAnimation *animation);

#ifdef __cplusplus
}
#endif
#endif

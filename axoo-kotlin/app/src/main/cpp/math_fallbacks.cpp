/*
 * math_fallbacks.cpp
 *
 * Fornisce implementazioni C++ di fallback per le funzioni matematiche di compositing di pixman.
 * 
 * Necessario perché i compilatori NDK moderni (Clang) rifiutano di compilare il vecchio 
 * codice Assembly ARM NEON di pixman. Senza questi stub C++, il processo di linking per 
 * l'architettura __ARM_NEON__ fallirebbe impedendo la compilazione dell'app.
 */

#if defined(__ARM_NEON__)

#include <cstdint>
#include <cstring>
#include <arm_neon.h>

extern "C" {

// Fallback per la composizione "src": sovrascrive direttamente il pixel di destinazione (colore solido)
void pixman_composite_src_n_8888_asm_neon(int32_t w, int32_t h,
                                           uint32_t *dst, int32_t dst_stride,
                                           uint32_t src) {
    for (int32_t y = 0; y < h; y++) {
        uint32_t *row = dst + y * dst_stride;
        for (int32_t x = 0; x < w; x++) {
            row[x] = src;
        }
    }
}

// Fallback per la composizione "over": applica l'alpha blending per gestire le trasparenze
void pixman_composite_over_n_8888_asm_neon(int32_t w, int32_t h,
                                            uint32_t *dst, int32_t dst_stride,
                                            uint32_t src) {
    uint32_t sa = (src >> 24); // Estrae il canale Alpha sorgente (trasparenza)
    
    if (sa == 0) return; // Sorgente completamente trasparente, ignora il calcolo
    
    if (sa == 255) {
        // Sorgente completamente opaca, equivale a una sovrascrittura netta ("src")
        pixman_composite_src_n_8888_asm_neon(w, h, dst, dst_stride, src);
        return;
    }
    
    // Estrae i canali RGB della sorgente
    uint32_t sr = (src >> 16) & 0xFF;
    uint32_t sg = (src >> 8) & 0xFF;
    uint32_t sb = src & 0xFF;
    
    // Calcola l'inverso dell'Alpha sorgente per sfumare i pixel di background
    uint32_t inv_sa = 255 - sa;

    for (int32_t y = 0; y < h; y++) {
        uint32_t *row = dst + y * dst_stride;
        for (int32_t x = 0; x < w; x++) {
            uint32_t d = row[x];
            
            // Estrae i canali ARGB del pixel di destinazione corrente
            uint32_t da = (d >> 24);
            uint32_t dr = (d >> 16) & 0xFF;
            uint32_t dg = (d >> 8) & 0xFF;
            uint32_t db = d & 0xFF;

            // Applica la formula standard di Alpha Blending a ciascun canale
            uint32_t ra = sa + ((da * inv_sa + 127) / 255);
            uint32_t rr = sr + ((dr * inv_sa + 127) / 255);
            uint32_t rg = sg + ((dg * inv_sa + 127) / 255);
            uint32_t rb = sb + ((db * inv_sa + 127) / 255);

            // Ricompone il pixel fuso in un intero 32-bit e lo salva
            row[x] = (ra << 24) | (rr << 16) | (rg << 8) | rb;
        }
    }
}

} // extern "C"

#endif // __ARM_NEON__

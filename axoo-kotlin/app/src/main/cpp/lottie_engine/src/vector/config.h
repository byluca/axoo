/*
 * config.h
 *
 * Feature toggles base. Pragmatismo assoluto: abilita log, macro,
 * e spegni ciò che non serve al tempo di compilazione. 
 * Niente controlli if-else a runtime per feature flag.
 * - antirez
 */
#ifndef CONFIG_H
#define CONFIG_H

//enable logging
//#define LOTTIE_LOGGING_SUPPORT

//enable static building of image loader
//#define LOTTIE_STATIC_IMAGE_LOADER

//enable lottie model caching
//#define LOTTIE_CACHE_SUPPORT

#endif  // CONFIG_H

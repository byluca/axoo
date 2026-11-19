#pragma once

// Telegram's rlottie subtree includes <tgnet/FileLog.h>, but the Axoo port
// intentionally imports only TMessagesProj/jni/rlottie. The observed includes
// are unused in normal builds unless Telegram-specific parser debug macros are
// enabled, so a no-op compatibility shim is enough for this isolated backend.

namespace FileLog {

template <typename... Args>
inline void d(Args&&...) {}

template <typename... Args>
inline void e(Args&&...) {}

} // namespace FileLog


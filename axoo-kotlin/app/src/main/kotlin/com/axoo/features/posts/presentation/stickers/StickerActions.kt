package com.axoo.features.posts.presentation.stickers

import android.view.View
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.axoo.features.posts.data.PersonalStickerRepository
import com.axoo.features.posts.domain.ImageMeta
import com.axoo.ui.sheets.OptionsSheet
import com.axoo.ui.sheets.OptionsSheetAction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One shared action for custom stickers in posts and comments. */
internal object StickerActions {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies { fun personalStickers(): PersonalStickerRepository }
    private var saving: Job? = null

    fun show(anchor: View, sticker: ImageMeta) {
        if (!anchor.isAttachedToWindow || sticker.provider != "axoo" || sticker.type != "sticker") return
        OptionsSheet.present(anchor, actions = listOf(
            OptionsSheetAction("Salva nei miei sticker", iconAsset = "smiley-sticker.svg") {
                val owner = anchor.findViewTreeLifecycleOwner() ?: return@OptionsSheetAction
                if (saving?.isActive == true) return@OptionsSheetAction
                val context = anchor.context.applicationContext
                val repository = EntryPointAccessors.fromApplication(context, Dependencies::class.java).personalStickers()
                saving = owner.lifecycleScope.launch {
                    try {
                        repository.save(sticker)
                        Toast.makeText(context, "Sticker salvato", Toast.LENGTH_SHORT).show()
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        val message = if ((error as? retrofit2.HttpException)?.code() == 409) "La tua raccolta contiene già 100 sticker"
                            else "Sticker non salvato, riprova"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        ))
    }
}

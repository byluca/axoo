package com.axoo.engine.emoji.catalog

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

private const val AXOOJI_ROOT = "axooji"
private val AXOOJI_CATEGORY_ID = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
private val AXOOJI_EMOJI_KEY = Regex(
    "^[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*$",
)

internal fun animatedEmojiAssetPath(name: String): String =
    "$AXOOJI_ROOT/animations/$name.tgs"

@JsonClass(generateAdapter = true)
data class Emoji(
    val id: Int,
    val name: String,
    val fallback: String,
    val pickerHidden: Boolean = false,
) {
    val assetPath: String get() = animatedEmojiAssetPath(name)
}

data class EmojiCategorySection(
    val id: String,
    val label: String,
    val tabEmoji: String,
    val items: List<Emoji>,
    val icon: Emoji,
)

/**
 * SINGLETON GLOBALE (Archivio in Memoria).
 * Viene caricato 1 sola volta all'avvio dell'app leggendo il file JSON.
 * Mantiene in RAM tutte le emoji già tradotte in oggetti per l'accesso istantaneo.
 */
object EmojiData {
    private const val CATALOG_ASSET = "$AXOOJI_ROOT/catalog.json"

    @Volatile
    private var catalog: LoadedCatalog? = null

    // Queste sono le agende (Map) salvate in RAM. Esempio: gli dai l'ID ---> ti restituisce l'Oggetto Emoji completo
    val emojis: List<Emoji> get() = requireCatalog().emojis
    val byId: Map<Int, Emoji> get() = requireCatalog().byId
    val byName: Map<String, Emoji> get() = requireCatalog().byName
    val categories: List<EmojiCategorySection> get() = requireCatalog().categories

    fun configure(context: Context) {
        // Evita di ricaricare il JSON se lo abbiamo già fatto
        if (catalog != null) return
        val json = context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
        configureFromJson(json)
    }

    internal fun configureFromJson(json: String) {
        if (catalog != null) return
        synchronized(this) {
            if (catalog != null) return
            val adapter = Moshi.Builder().build().adapter(AxoojiCatalogAsset::class.java)
            val source = requireNotNull(adapter.fromJson(json)) { "Invalid $CATALOG_ASSET" }
            require(source.name == "Axooji") { "Unexpected emoji catalog '${source.name}'" }
            require(source.schemaVersion == 1) {
                "Unsupported Axooji schema ${source.schemaVersion}"
            }
            require(source.emojis.all { AXOOJI_EMOJI_KEY.matches(it.name) }) {
                "Invalid Axooji emoji key"
            }
            require(source.categories.all { AXOOJI_CATEGORY_ID.matches(it.id) }) {
                "Invalid Axooji category id"
            }
            require(source.emojis.distinctBy(Emoji::id).size == source.emojis.size) {
                "Duplicate Axooji emoji id"
            }
            require(source.emojis.distinctBy(Emoji::name).size == source.emojis.size) {
                "Duplicate Axooji emoji key"
            }
            val uniqueCategoryCount = source.categories.distinctBy(EmojiCategoryAsset::id).size
            require(uniqueCategoryCount == source.categories.size) {
                "Duplicate Axooji category id"
            }
            require(source.categories.all { it.icon in it.items }) {
                "Axooji category icon must reference one of its emoji"
            }
            val byName = source.emojis.associateBy(Emoji::name)
            val categoryNames = source.categories.flatMap(EmojiCategoryAsset::items)
            require(categoryNames.size == categoryNames.distinct().size) {
                "An Axooji emoji belongs to multiple categories"
            }
            require(categoryNames.toSet() == byName.keys) {
                "Axooji categories and emoji records do not match"
            }
            val sections = source.categories.map { category ->
                EmojiCategorySection(
                    id = category.id,
                    label = category.label,
                    tabEmoji = category.tabEmoji,
                    icon = requireNotNull(byName[category.icon]),
                    items = category.items.map { name ->
                        requireNotNull(byName[name]) { "Unknown emoji '$name' in ${category.id}" }
                    }.filterNot { it.pickerHidden },
                )
            }
            catalog = LoadedCatalog(
                emojis = source.emojis,
                byId = source.emojis.associateBy(Emoji::id),
                byName = byName,
                categories = sections.filter { it.items.isNotEmpty() },
            )
        }
    }

    private fun requireCatalog(): LoadedCatalog = checkNotNull(catalog) {
        "EmojiData is not configured. Call EmojiData.configure(context) from Application.onCreate()."
    }
}

@JsonClass(generateAdapter = true)
internal data class AxoojiCatalogAsset(
    val name: String,
    val schemaVersion: Int,
    val emojis: List<Emoji>,
    val categories: List<EmojiCategoryAsset>,
)

@JsonClass(generateAdapter = true)
internal data class EmojiCategoryAsset(
    val id: String,
    val label: String,
    val tabEmoji: String,
    val icon: String,
    val items: List<String>,
)

private data class LoadedCatalog(
    val emojis: List<Emoji>,
    val byId: Map<Int, Emoji>,
    val byName: Map<String, Emoji>,
    val categories: List<EmojiCategorySection>,
)

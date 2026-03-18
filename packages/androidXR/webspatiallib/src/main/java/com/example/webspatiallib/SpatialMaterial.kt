package com.example.webspatiallib

import android.graphics.Color
import android.util.Log

/**
 * Base class for all spatial materials.
 * Mirrors visionOS material system.
 */
sealed class SpatialMaterial : SpatialObject() {
    companion object {
        const val TAG = "SpatialMaterial"

        /**
         * Parse a color string (hex or named) to Android Color int.
         */
        fun parseColor(colorString: String?): Int {
            if (colorString.isNullOrEmpty()) return Color.WHITE

            return try {
                when {
                    colorString.startsWith("#") -> Color.parseColor(colorString)
                    colorString.startsWith("rgb") -> parseRgbColor(colorString)
                    else -> getNamedColor(colorString)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse color: $colorString, using white")
                Color.WHITE
            }
        }

        private fun parseRgbColor(rgb: String): Int {
            // Parse rgb(r, g, b) or rgba(r, g, b, a)
            val values = rgb.replace(Regex("[^0-9,.]"), "").split(",")
            val r = values.getOrNull(0)?.toFloatOrNull()?.toInt() ?: 255
            val g = values.getOrNull(1)?.toFloatOrNull()?.toInt() ?: 255
            val b = values.getOrNull(2)?.toFloatOrNull()?.toInt() ?: 255
            val a = values.getOrNull(3)?.toFloatOrNull()?.let { (it * 255).toInt() } ?: 255
            return Color.argb(a, r, g, b)
        }

        private fun getNamedColor(name: String): Int {
            return when (name.lowercase()) {
                "white" -> Color.WHITE
                "black" -> Color.BLACK
                "red" -> Color.RED
                "green" -> Color.GREEN
                "blue" -> Color.BLUE
                "yellow" -> Color.YELLOW
                "cyan" -> Color.CYAN
                "magenta" -> Color.MAGENTA
                "gray", "grey" -> Color.GRAY
                "transparent" -> Color.TRANSPARENT
                else -> Color.WHITE
            }
        }
    }

    abstract val materialType: String
}

/**
 * Unlit material - no lighting calculations, just color/texture.
 */
class UnlitMaterial(
    val color: Int = Color.WHITE,
    val textureId: String? = null,
    val transparent: Boolean = false,
    val opacity: Float = 1f
) : SpatialMaterial() {
    override val materialType = "unlit"

    init {
        Log.d(TAG, "Created UnlitMaterial: color=${Integer.toHexString(color)}, opacity=$opacity")
    }

    companion object {
        fun create(
            colorString: String? = null,
            textureId: String? = null,
            transparent: Boolean = false,
            opacity: Float = 1f
        ): UnlitMaterial {
            return UnlitMaterial(
                color = parseColor(colorString),
                textureId = textureId,
                transparent = transparent,
                opacity = opacity
            )
        }
    }
}

/**
 * Physical-based rendering material with PBR properties.
 * Mirrors visionOS PhysicallyBasedMaterial.
 */
class PhysicalMaterial(
    val baseColor: Int = Color.WHITE,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val baseColorTextureId: String? = null,
    val normalTextureId: String? = null,
    val metallicRoughnessTextureId: String? = null
) : SpatialMaterial() {
    override val materialType = "physical"

    init {
        Log.d(TAG, "Created PhysicalMaterial: color=${Integer.toHexString(baseColor)}, metallic=$metallic, roughness=$roughness")
    }

    companion object {
        fun create(
            colorString: String? = null,
            metallic: Float = 0f,
            roughness: Float = 0.5f,
            baseColorTextureId: String? = null,
            normalTextureId: String? = null,
            metallicRoughnessTextureId: String? = null
        ): PhysicalMaterial {
            return PhysicalMaterial(
                baseColor = parseColor(colorString),
                metallic = metallic,
                roughness = roughness,
                baseColorTextureId = baseColorTextureId,
                normalTextureId = normalTextureId,
                metallicRoughnessTextureId = metallicRoughnessTextureId
            )
        }
    }
}

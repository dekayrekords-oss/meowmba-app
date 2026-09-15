package com.example.meowmba.ui.companion

/** A settled native pose for setup thumbnails; no animation clock or morph warm-up. */
internal fun restingAvatarFrame(style: AvatarStyle, animal: AvatarAnimal): LumenFrame {
    val character = if (style == AvatarStyle.LUMEN_CAT) AvatarAnimal.CAT else animal
    return LumenFrame(
        cat = if (character == AvatarAnimal.CAT) 1f else 0f,
        dog = if (character == AvatarAnimal.DOG) 1f else 0f,
        form = AvatarFormProfiles.traits(character),
    )
}

/** Fits the actual resting anatomy, leaving room for motion and the bottom controls.
 * Dimensions may be dp or px, provided they use the same unit. No sensors or frame loop. */
internal fun autoAvatarScale(width: Float, height: Float, style: AvatarStyle, animal: AvatarAnimal): Float {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return 1f
    if (style == AvatarStyle.FULL_FLY) return 1.5f // Stage renderer already fits the complete fly.
    if (style.isLumen) {
        val frame = restingAvatarFrame(style, animal)
        val unit = lumenUnit(width, height, frame)
        val envelope = lumenEnvelope(frame)
        return minOf(width * .40f / (unit * envelope.x),
            height * .34f / (unit * envelope.top),
            height * .27f / (unit * envelope.bottom)).coerceIn(.5f, 2.5f)
    }
    // These renderers already fit a full face/orb within the short edge.
    return 1f
}

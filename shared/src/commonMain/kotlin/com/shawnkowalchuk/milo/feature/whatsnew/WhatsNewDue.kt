package com.shawnkowalchuk.milo.feature.whatsnew

import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.MiloSettings

/**
 * Whether [settings] say that the list of [installedVersion] is still to be shown: the first
 * start is over and this phone has not been shown that version's list. Shared since 2026-11-16,
 * when the iPhone got its What's new: both phones open it by the same rule.
 */
fun isDue(settings: MiloSettings, installedVersion: String): Boolean =
    settings.firstRunStage == FirstRunStage.DONE &&
        settings.whatsNewSeenVersion != installedVersion

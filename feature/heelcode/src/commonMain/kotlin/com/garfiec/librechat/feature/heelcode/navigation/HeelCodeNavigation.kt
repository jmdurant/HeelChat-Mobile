package com.garfiec.librechat.feature.heelcode.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.heelcode.screen.HeelCodeScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface HeelCodeRoute : NavKey

/** Single HeelCode Remote destination; list↔detail is internal screen state, not a nav route. */
@Serializable data object HeelCode : HeelCodeRoute

fun EntryProviderScope<NavKey>.heelCodeEntries(
    onBack: () -> Unit,
) {
    entry<HeelCode> {
        HeelCodeScreen(onBack = onBack)
    }
}

val heelCodeSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(HeelCode::class, HeelCode.serializer())
    }
}

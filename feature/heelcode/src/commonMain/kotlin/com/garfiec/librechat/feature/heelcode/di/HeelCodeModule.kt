package com.garfiec.librechat.feature.heelcode.di

import com.garfiec.librechat.feature.heelcode.viewmodel.HeelCodeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val heelCodeModule = module {
    viewModelOf(::HeelCodeViewModel)
}

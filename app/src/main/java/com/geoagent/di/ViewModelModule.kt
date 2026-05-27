package com.geoagent.di

import com.geoagent.ui.auth.AuthViewModel
import com.geoagent.ui.chat.ChatListViewModel
import com.geoagent.ui.chat.ChatViewModel
import com.geoagent.ui.documents.DocumentViewModel
import com.geoagent.ui.settings.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { AuthViewModel(get()) }
    viewModel { ChatViewModel(get()) }
    viewModel { ChatListViewModel(get(), get()) }
    viewModel { DocumentViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
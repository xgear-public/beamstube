package com.awebo.ytext.di

import com.awebo.ytext.data.*
import com.awebo.ytext.ui.vm.HistoryViewModel
import com.awebo.ytext.ui.vm.ReorderViewModel
import com.awebo.ytext.ui.vm.SettingsViewModel
import com.awebo.ytext.ui.vm.YTViewModel
import com.awebo.ytext.ytapi.YTDataSource
import com.awebo.ytext.ytapi.YouTubeTranscriptSummarizer
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {

    single<AppDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single<VideoDao> { get<AppDatabase>().getDao() }
    single<MiscDataStore> { MiscDataStoreFactory().createMiscDataStore() }
    single<YouTubeTranscriptSummarizer> { YouTubeTranscriptSummarizer(miscDataStore = get()) }
    single<Map<VideoPlatform, VideoDataSource>> { mapOf(VideoPlatform.YOUTUBE to YTDataSource()) }

    single {
        VideosRepository(
            miscDataStore = get(),
            videoDao = get(),
            dataSources = get(),
        )
    }
    viewModelOf(::YTViewModel)
    viewModelOf(::ReorderViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::HistoryViewModel)

}
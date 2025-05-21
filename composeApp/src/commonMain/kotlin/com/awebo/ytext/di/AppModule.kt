package com.awebo.ytext.di

import com.awebo.ytext.data.AppDatabase
import com.awebo.ytext.data.MiscDataStore
import com.awebo.ytext.data.MiscDataStoreFactory
import com.awebo.ytext.data.VideoDao
import com.awebo.ytext.data.VideoDataSource
import com.awebo.ytext.data.getDatabaseBuilder
import com.awebo.ytext.data.getRoomDatabase
import com.awebo.ytext.ytapi.ReorderViewModel
import com.awebo.ytext.ytapi.VideosRepository
import com.awebo.ytext.ytapi.YTDataSource
import com.awebo.ytext.ytapi.YTViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {

    single<AppDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single<VideoDao> { get<AppDatabase>().getDao() }
    single<MiscDataStore> { MiscDataStoreFactory().createMiscDataStore() }
    single<Map<String, VideoDataSource>> { mapOf("youtube" to YTDataSource()) }

    singleOf(::VideosRepository)
    viewModelOf(::YTViewModel)
    viewModelOf(::ReorderViewModel)

}
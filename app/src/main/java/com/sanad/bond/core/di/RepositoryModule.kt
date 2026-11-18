package com.sanad.bond.core.di

import com.sanad.bond.data.repository.DocumentRepository
import com.sanad.bond.data.repository.DocumentRepositoryImpl
import com.sanad.bond.data.repository.FilesRepository
import com.sanad.bond.data.repository.FilesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFilesRepository(
        impl: FilesRepositoryImpl
    ): FilesRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository
}
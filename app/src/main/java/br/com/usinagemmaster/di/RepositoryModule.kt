package br.com.usinagemmaster.di

import br.com.usinagemmaster.data.repository.GameRepositoryImpl
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.data.social.FirebaseSocialRepository
import br.com.usinagemmaster.domain.social.SocialRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository
    @Binds @Singleton abstract fun bindSocialRepository(impl: FirebaseSocialRepository): SocialRepository
}

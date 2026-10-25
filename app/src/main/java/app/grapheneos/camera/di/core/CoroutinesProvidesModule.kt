package app.grapheneos.camera.di.core

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal class CoroutinesProvidesModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    @Provides
    @MainImmediateDispatcher
    fun provideMainImmediateDispatcher(): CoroutineDispatcher {
        return Dispatchers.Main.immediate
    }

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher {
        return Dispatchers.Default
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    // Each frame retriever starts its own hardware decoder, which competes with the playing video's.
    @Provides
    @Singleton
    @VideoFrameDispatcher
    fun provideVideoFrameDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(
            parallelism = 1,
            name = "Video frames",
        )
    }
}

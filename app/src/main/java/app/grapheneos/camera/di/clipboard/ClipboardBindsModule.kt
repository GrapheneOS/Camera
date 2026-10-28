package app.grapheneos.camera.di.clipboard

import app.grapheneos.camera.domain.clipboard.usecase.CopyText
import app.grapheneos.camera.domain.clipboard.usecase.CopyTextImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ClipboardBindsModule {

    @Binds
    @Reusable
    abstract fun bindCopyText(
        impl: CopyTextImpl,
    ): CopyText
}

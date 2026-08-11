package app.grapheneos.camera.di.media

import app.grapheneos.camera.data.media.store.CapturedItemStore
import app.grapheneos.camera.data.media.store.CapturedItemStoreImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
internal abstract class MediaBindsModule {

    @Binds
    @ActivityScoped
    abstract fun bindCapturedItemStore(impl: CapturedItemStoreImpl): CapturedItemStore
}

package app.grapheneos.camera.di.qr

import app.grapheneos.camera.domain.qr.BarcodeFormats
import app.grapheneos.camera.domain.qr.BarcodeFormatsImpl
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
internal abstract class QrBindsModule {

    @Binds
    @Reusable
    abstract fun bindBarcodeFormats(
        impl: BarcodeFormatsImpl,
    ): BarcodeFormats
}

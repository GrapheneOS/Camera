package app.grapheneos.camera.ui.composable.screen.routes

import kotlinx.serialization.Serializable

@Serializable
object ExtendedGalleryRoute

//import app.grapheneos.camera.CapturedItem
//import app.grapheneos.camera.ui.composable.screen.navtype.CapturedItemListNavType
//import kotlin.reflect.typeOf
//import kotlinx.serialization.Serializable
//
//@Serializable
//data class ExtendedGalleryRoute(
//    val isSecureMode : Boolean = false,
//    val showVideosOnly : Boolean = false,
//    val mediaItems : List<CapturedItem>? = null,
//) {
//    companion object {
//        val typeMap = mapOf(
//            typeOf<List<CapturedItem>?>() to CapturedItemListNavType,
//        )
//    }
//}
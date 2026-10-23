package app.grapheneos.camera.ui.activities

/**
 * CameraX composition coordinates are NDC (-1 to +1), with Y pointing up.
 * This model is unit-testable without an Android Camera provider.
 */
internal data class DualVideoCompositionLayout(
    val x:Float=0.68f, val y:Float=0.12f, val fraction:Float=0.29f
) {
    data class Viewport(val left:Float,val top:Float,val width:Float,val height:Float)
    companion object {
        /** Must match CameraX Preview's 16:9 HD stream rotated into portrait.
         * FIT_CENTER presents these exact bounds without invisible cropping. */
        fun fitViewport(screenWidth:Float,screenHeight:Float):Viewport {
            val w=screenWidth.coerceAtLeast(1f)
            val h=screenHeight.coerceAtLeast(1f)
            val ratio=if(w>h)16f/9f else 9f/16f
            val actualWidth=kotlin.math.min(w,h*ratio)
            val actualHeight=kotlin.math.min(h,w/ratio)
            return Viewport((w-actualWidth)/2,(h-actualHeight)/2,
                actualWidth,actualHeight)
        }
    }
    fun bounded():DualVideoCompositionLayout {
        val f=fraction.coerceIn(0.20f,0.55f)
        return copy(x=x.coerceIn(0f,1f-f),y=y.coerceIn(0f,1f-f),fraction=f)
    }
    fun moved(dx:Float,dy:Float)=copy(x=x+dx,y=y+dy).bounded()
    fun resized(factor:Float):DualVideoCompositionLayout {
        val f=(fraction*factor).coerceIn(0.20f,0.55f)
        return copy(x=x+(fraction-f)/2,y=y+(fraction-f)/2,fraction=f).bounded()
    }
    data class Rect(val cx:Float,val cy:Float,val sx:Float,val sy:Float)
    fun pip():Rect {val p=bounded();return Rect(2*p.x+p.fraction-1f,
        1f-2*p.y-p.fraction,p.fraction,p.fraction)}
    fun primary(split:Boolean)=if(split)Rect(-0.5f,0f,0.5f,1f) else Rect(0f,0f,1f,1f)
    fun secondary(split:Boolean)=if(split)Rect(0.5f,0f,0.5f,1f) else pip()
}

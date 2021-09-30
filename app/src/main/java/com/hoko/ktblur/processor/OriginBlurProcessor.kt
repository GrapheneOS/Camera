package com.hoko.ktblur.processor

import android.graphics.Bitmap
import com.hoko.ktblur.filter.OriginBlurFilter
import com.hoko.ktblur.params.Direction
import com.hoko.ktblur.params.Scheme
import com.hoko.ktblur.task.BlurSubTask
import com.hoko.ktblur.task.BlurTaskManager

class OriginBlurProcessor(builder: HokoBlurBuild) : AbstractBlurProcessor(builder) {

    override fun realBlur(bitmap: Bitmap, parallel: Boolean): Bitmap {
        if (parallel) {
            val cores = BlurTaskManager.WORKER_THREADS_COUNT
            val hTasks = mutableListOf<BlurSubTask>()
            val vTasks = mutableListOf<BlurSubTask>()
            for (i in 0 until cores) {
                hTasks.add(BlurSubTask(Scheme.KOTLIN, mode, bitmap, radius, i, cores, Direction.HORIZONTAL))
                vTasks.add(BlurSubTask(Scheme.KOTLIN, mode, bitmap, radius, i, cores, Direction.VERTICAL))
            }
            BlurTaskManager.invokeAll(hTasks)
            BlurTaskManager.invokeAll(vTasks)
        } else {
            OriginBlurFilter.doFullBlur(mode, bitmap, radius)
        }
        return bitmap
    }
}
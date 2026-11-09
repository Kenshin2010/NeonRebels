package com.manroid.neonrebels.view.animation

import java.util.Properties

/** Pixel positions use the original untrimmed canvas, with y measured from its bottom. */
data class Socket(val x: Float, val y: Float)
enum class ClipEnd { LOOP, STATE, HOLD }

class AnimationClip(
    val id: String,
    val frames: List<String>,
    val durations: List<Float>,
    val end: ClipEnd,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val height: Float,
    val facesRight: Boolean,
    val anchor: Socket,
    val sockets: Map<String, Socket>,
    /** Exclusive ends of startup and active; recovery ends at frames.size. */
    val phaseEnds: List<Int>,
    val frameAnchors: Map<String, Socket> = emptyMap(),
    val frameSockets: Map<String, Map<String, Socket>> = emptyMap(),
) {
    val duration = durations.sum()

    init {
        require(frames.size >= 2 && frames.distinct().size == frames.size) { "$id needs independent named frames" }
        require(frames.all { it.startsWith(id.replace('.', '_') + "_") }) { "$id frame prefix" }
        val numbers = frames.map { it.substringAfterLast('_').toInt() }
        require(numbers == numbers.sorted() && numbers.distinct().size == numbers.size) { "$id frame order" }
        require(durations.size == frames.size && durations.all { it.isFinite() && it > 0f } && duration.isFinite())
        require(canvasWidth > 0 && canvasHeight > 0 && height.isFinite() && height > 0f)
        require((listOf(anchor) + frameAnchors.values).all {
            it.x in 0f..canvasWidth.toFloat() && it.y in 0f..canvasHeight.toFloat()
        }) { "$id anchor outside canvas" }
        require(phaseEnds.isEmpty() || (phaseEnds.size == 2 && phaseEnds[0] > 0 &&
            phaseEnds[1] > phaseEnds[0] && phaseEnds[1] < frames.size))
    }

    fun frameAt(time: Float): Int {
        var t = time.coerceAtLeast(0f)
        if (end == ClipEnd.LOOP) t %= duration
        for (i in durations.indices) {
            if (t < durations[i]) return i
            t -= durations[i]
        }
        return frames.lastIndex
    }

    /** Keep each gameplay phase within its own authored frames, regardless of animation FPS. */
    fun phaseFrame(phase: Int, progress: Float): Int {
        if (phaseEnds.isEmpty()) return frameAt(progress * duration)
        val first = if (phase == 0) 0 else phaseEnds[phase - 1]
        val last = if (phase == 2) frames.size else phaseEnds[phase]
        val length = (first until last).sumOf { durations[it].toDouble() }.toFloat()
        var time = progress.coerceIn(0f, 1f) * length
        for (i in first until last) {
            if (time < durations[i]) return i
            time -= durations[i]
        }
        return last - 1
    }

    fun anchorAt(frame: Int): Socket = frameAnchors[frames[frame]] ?: anchor
    fun socketAt(frame: Int, name: String): Socket? = frameSockets[frames[frame]]?.get(name) ?: sockets[name]
}

/** No GL dependency: malformed configuration is reported once by the caller, never from draw(). */
object AnimationManifest {
    val actions = listOf("idle", "run", "jump", "fall", "land", "dodge", "attack1", "attack2", "attack3",
        "shoot", "skill", "hurt", "death")
    val ids = listOf("an", "vy").flatMap { hero ->
        actions.filter { if (hero == "an") it != "shoot" else !it.startsWith("attack") }.map { "$hero.$it" }
    }

    fun parse(p: Properties, report: (String) -> Unit = {}): Map<String, AnimationClip> {
        val result = LinkedHashMap<String, AnimationClip>()
        for (id in ids) {
            try {
                fun value(key: String): String = p.getProperty("$id.$key") ?: error("missing $key")
                fun pair(text: String): Socket {
                    val n = text.split(',').map { it.trim().toFloat() }
                    require(n.size == 2 && n.all { it.isFinite() })
                    return Socket(n[0], n[1])
                }
                val frames = value("frames").split(',').map { it.trim() }
                val fps = (p.getProperty("$id.fps") ?: "12").toFloat()
                require(fps.isFinite() && fps > 0f)
                val durations = p.getProperty("$id.durations")?.split(',')?.map { it.trim().toFloat() }
                    ?: List(frames.size) { 1f / fps }
                val canvas = pair(value("canvas"))
                require(canvas.x % 1f == 0f && canvas.y % 1f == 0f) { "canvas must use integer pixels" }
                val sockets = listOf("muzzle", "hand", "tip").mapNotNull { name ->
                    p.getProperty("$id.$name")?.let { name to pair(it) }
                }.toMap()
                val anchors = frames.mapNotNull { name -> p.getProperty("$name.anchor")?.let { name to pair(it) } }.toMap()
                val frameSockets = frames.associateWith { frame ->
                    listOf("muzzle", "hand", "tip").mapNotNull { name ->
                        p.getProperty("$frame.$name")?.let { name to pair(it) }
                    }.toMap()
                }
                val end = ClipEnd.valueOf(value("end"))
                require(if (id.endsWith(".death")) end == ClipEnd.HOLD
                    else if (id.endsWith(".idle") || id.endsWith(".run")) end == ClipEnd.LOOP
                    else end == ClipEnd.STATE) { "invalid end rule for $id" }
                val phases = p.getProperty("$id.phaseEnds")?.split(',')?.map { it.trim().toInt() } ?: emptyList()
                if (id.contains(".attack")) require(phases.size == 2) { "attack needs phaseEnds" }
                result[id] = AnimationClip(id, frames, durations, end,
                    canvas.x.toInt(), canvas.y.toInt(), value("height").toFloat(),
                    value("facesRight").toBooleanStrict(), pair(value("anchor")), sockets, phases, anchors, frameSockets)
            } catch (e: RuntimeException) {
                report("$id: ${e.message}")
            }
        }
        return result
    }
}

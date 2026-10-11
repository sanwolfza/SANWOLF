package com.sanwolf.daw.audio

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Where a bundled multisample instrument shows up in the instrument picker. The UI maps these
 * onto its own headings (Keys, Percussion, Strings).
 */
enum class MultisamplePickerGroup { KEYS, MALLETS, PLUCKED, WORLD }

/** Fixed description of a bundled multisample instrument; its zones come from the manifest. */
class MultisampleSpec(
    /** Manifest id, e.g. "steinway_grand". */
    val id: String,
    /** Exact display name; also the track name and synthPresetName of tracks that use it. */
    val name: String,
    val group: MultisamplePickerGroup,
    /** true: sound is held for the note length, then released (piano). false: one-shot ring-out. */
    val sustainWhileHeld: Boolean,
    val releaseMs: Float,
    val colorHex: Long
) {
    /** Picker / preset id, e.g. "vcsl.steinway_grand". */
    val presetId: String get() = MultisampleLibrary.PRESET_ID_PREFIX + id
}

/** One sample of an instrument: decoded PCM plus the pitch it sounds at. */
class MultisampleZone(
    val rootMidi: Int,
    val tuneCents: Float,
    val data: ShortArray,
    val sampleRate: Int
)

/**
 * A bundled multisample instrument (VCSL). Zones are decoded off the audio thread
 * ([MultisampleLibrary] owns the loader thread); the audio thread only calls [createVoice].
 */
class MultisampleInstrument(val spec: MultisampleSpec) {
    /** Sample entries from the manifest: asset path (under the VCSL dir), root note, tune. */
    internal class SampleRef(val file: String, val rootMidi: Int, val tuneCents: Float)

    internal var sampleRefs: List<SampleRef> = emptyList()

    /** Decoded zones plus the zone index for each MIDI note 0..127 (nearest root). */
    private class Loaded(val zones: Array<MultisampleZone>, val noteToZone: IntArray)

    /** null while not decoded / evicted. Replaced whole (one volatile write), never mutated. */
    @Volatile
    private var loaded: Loaded? = null

    internal val loading = AtomicBoolean(false)
    /** Set when nothing could be decoded (missing asset / no codec): no endless retries. */
    @Volatile
    internal var loadFailed = false
    private val notReadyLogged = AtomicBoolean(false)

    @Volatile
    internal var lastUsedMs: Long = 0L

    val isReady: Boolean get() = loaded != null

    fun decodedBytes(): Long {
        val z = loaded?.zones ?: return 0L
        var total = 0L
        for (zone in z) total += zone.data.size.toLong() * 2L
        return total
    }

    internal fun publish(decoded: Array<MultisampleZone>) {
        val map = IntArray(128)
        for (note in 0 until 128) {
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (i in decoded.indices) {
                val d = abs(decoded[i].rootMidi - note)
                // Ties go to the lower root (the sample is pitched up slightly rather than down).
                if (d < bestDist || (d == bestDist && decoded[i].rootMidi < decoded[best].rootMidi)) {
                    best = i
                    bestDist = d
                }
            }
            map[note] = best
        }
        loaded = Loaded(decoded, map)
        notReadyLogged.set(false)
    }

    internal fun evict() {
        loaded = null
    }

    /**
     * A voice for [pitch], or null when the zones aren't decoded yet (the caller skips the note;
     * a decode is requested and the miss is logged once). Allocates only the voice object.
     */
    fun createVoice(pitch: Int, durationSec: Float, velocity: Float, pan: Float): ActiveVoice? {
        val l = loaded
        if (l == null || l.zones.isEmpty()) {
            if (notReadyLogged.compareAndSet(false, true)) {
                Log.w(MultisampleLibrary.TAG, "${spec.name} not decoded yet: skipping notes until it is")
            }
            MultisampleLibrary.requestLoad(this)
            return null
        }
        val note = pitch.coerceIn(0, 127)
        val zone = l.zones[l.noteToZone[note]]
        val semis = (note - zone.rootMidi) + zone.tuneCents / 100.0
        val step = 2.0.pow(semis / 12.0) * zone.sampleRate / SanwolfAudioEngine.SAMPLE_RATE
        return MultisampleVoice(
            data = zone.data,
            step = step,
            velocity = velocity,
            pan = pan,
            holdSamples = if (spec.sustainWhileHeld) (durationSec.coerceAtLeast(0.02f) * SanwolfAudioEngine.SAMPLE_RATE).toInt() else Int.MAX_VALUE,
            releaseSamples = (spec.releaseMs / 1000f * SanwolfAudioEngine.SAMPLE_RATE).toInt().coerceAtLeast(64)
        )
    }
}

/**
 * Plays one zone, pitch-shifted by linear-interpolation resampling. Velocity sets the gain;
 * after [holdSamples] (note-off) an exponential release runs over [releaseSamples]. One-shot
 * instruments pass Int.MAX_VALUE and simply ring out to the end of the sample. A short fade at
 * the end of the data avoids a click on truncated samples. Allocation-free in [nextSample].
 */
class MultisampleVoice(
    private val data: ShortArray,
    private val step: Double,
    velocity: Float,
    override val pan: Float,
    private val holdSamples: Int,
    releaseSamples: Int
) : ActiveVoice {
    private val gain: Float
    private val releaseK: Float
    private val lastIndex = data.size - 1
    private var pos = 0.0
    private var n = 0
    private var env = 1f
    private var done = data.size < 2

    init {
        val v = velocity.coerceIn(0f, 2f)
        // Gentle curve: soft notes a little softer than linear, full velocity ≈ 0.85.
        gain = 0.85f * v * sqrt(v) * INV_SHORT
        // env(n) = 0.001 after releaseSamples (−60 dB)
        releaseK = exp(ln(0.001) / releaseSamples).toFloat()
    }

    override val isFinished: Boolean get() = done

    override fun nextSample(): Float {
        if (done) return 0f
        val i = pos.toInt()
        if (i >= lastIndex) {
            done = true
            return 0f
        }
        val frac = (pos - i).toFloat()
        val a = data[i].toFloat()
        val b = data[i + 1].toFloat()
        var s = (a + (b - a) * frac) * gain
        pos += step

        if (n >= holdSamples) {
            env *= releaseK
            if (env < 0.001f) done = true
        }
        n++

        // ~5 ms fade before the data runs out (in output samples at the current pitch).
        val remaining = (lastIndex - i) / step
        if (remaining < END_FADE) s *= (remaining / END_FADE).toFloat()

        return s * env
    }

    private companion object {
        const val INV_SHORT = 1f / 32768f
        const val END_FADE = 220.0
    }
}

/**
 * The bundled VCSL instruments (assets/instruments/vcsl): manifest, decoding on a background
 * thread, a memory budget shared with [PcmSampleCache], and name lookup for the engines.
 */
object MultisampleLibrary {
    internal const val TAG = "MultisampleLibrary"
    const val ASSET_DIR = "instruments/vcsl"
    const val PRESET_ID_PREFIX = "vcsl."
    /** Longest part of a sample we keep (all bundled samples are shorter). */
    private const val MAX_ZONE_SECONDS = 8f

    /** Licence details shown in Settings › About › Sound licences (overridden by the manifest). */
    const val DEFAULT_SOURCE = "VCSL - Versilian Community Sample Library"
    const val DEFAULT_LICENCE = "CC0 1.0"
    const val DEFAULT_AUTHOR = "Versilian Studios / Samuel Gossner"
    const val DEFAULT_URL = "https://github.com/sgossner/VCSL"

    val specs: List<MultisampleSpec> = listOf(
        MultisampleSpec("steinway_grand", "Steinway Grand Piano", MultisamplePickerGroup.KEYS, true, 260f, 0xFFFFC107),
        MultisampleSpec("fm_epiano", "FM Electric Piano", MultisamplePickerGroup.KEYS, true, 200f, 0xFFFFD54F),
        MultisampleSpec("marimba", "Marimba", MultisamplePickerGroup.MALLETS, false, 200f, 0xFFFF9100),
        MultisampleSpec("vibraphone", "Vibraphone", MultisamplePickerGroup.MALLETS, false, 250f, 0xFF00B0FF),
        MultisampleSpec("kalimba", "Kalimba", MultisamplePickerGroup.PLUCKED, false, 200f, 0xFF76FF03),
        MultisampleSpec("balafon", "Balafon", MultisamplePickerGroup.WORLD, false, 150f, 0xFFFF6D00),
        MultisampleSpec("harp", "Concert Harp", MultisamplePickerGroup.PLUCKED, false, 300f, 0xFFB2FF59)
    )

    private val instruments: Array<MultisampleInstrument> = specs.map { MultisampleInstrument(it) }.toTypedArray()

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var manifestLoaded = false
    private val manifestLock = Any()

    /** Licence fields from the manifest (or the defaults). */
    @Volatile var source: String = DEFAULT_SOURCE
        private set
    @Volatile var licence: String = DEFAULT_LICENCE
        private set
    @Volatile var author: String = DEFAULT_AUTHOR
        private set
    @Volatile var url: String = DEFAULT_URL
        private set

    /** Ids (manifest ids) the open project uses; never evicted. */
    @Volatile
    private var pinned: Set<String> = emptySet()

    private val loader = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SanwolfMultisample").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    /** Call once with any context (the application context is kept). Cheap; parses nothing yet. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext ?: context
    }

    /**
     * The instrument a track name / preset name / preset id refers to, or null. Exact
     * (case-insensitive) match only, so it never steals a synth preset with a similar name.
     * Allocation-free: safe on the audio thread.
     */
    fun find(query: String?): MultisampleInstrument? {
        if (query.isNullOrEmpty()) return null
        for (inst in instruments) {
            val s = inst.spec
            if (query.equals(s.name, ignoreCase = true) || query.equals(s.id, ignoreCase = true)) return inst
            if (query.length == PRESET_ID_PREFIX.length + s.id.length &&
                query.startsWith(PRESET_ID_PREFIX, ignoreCase = true) &&
                query.regionMatches(PRESET_ID_PREFIX.length, s.id, 0, s.id.length, ignoreCase = true)
            ) return inst
        }
        return null
    }

    fun isMultisample(query: String?): Boolean = find(query) != null

    /** Total bundled samples (for the licences screen); 0 if the manifest can't be read. */
    fun sampleCount(context: Context): Int {
        init(context)
        ensureManifest()
        return instruments.sumOf { it.sampleRefs.size }
    }

    fun decodedBytes(): Long {
        var total = 0L
        for (inst in instruments) total += inst.decodedBytes()
        return total
    }

    /** Marks the instruments the open project uses (by any name [find] accepts). */
    fun setPinned(queries: Collection<String>) {
        pinned = queries.mapNotNull { find(it)?.spec?.id }.toSet()
    }

    /** Decodes [query]'s instrument in the background (no-op if ready). [onReady] runs on the loader thread. */
    fun prewarm(query: String?, onReady: (() -> Unit)? = null) {
        val inst = find(query) ?: return
        inst.lastUsedMs = System.currentTimeMillis()
        if (inst.isReady) {
            onReady?.invoke()
            return
        }
        if (inst.loadFailed) return
        runCatching {
            loader.execute {
                // A throwable escaping an executor task kills the process: contain it here
                try {
                    loadBlocking(inst)
                    if (inst.isReady) onReady?.invoke()
                } catch (t: Throwable) {
                    Log.e(TAG, "Prewarm of ${inst.spec.name} failed", t)
                    inst.loadFailed = true
                }
            }
        }.onFailure { Log.w(TAG, "Prewarm not scheduled for ${inst.spec.name}", it) }
    }

    /** Called from the audio thread on a miss: schedules one background decode. */
    internal fun requestLoad(inst: MultisampleInstrument) {
        if (inst.loadFailed || !inst.loading.compareAndSet(false, true)) return
        runCatching {
            loader.execute {
                try {
                    loadBlocking(inst)
                } catch (t: Throwable) {
                    Log.e(TAG, "Load of ${inst.spec.name} failed", t)
                    inst.loadFailed = true
                } finally {
                    inst.loading.set(false)
                }
            }
        }.onFailure {
            inst.loading.set(false)
            Log.w(TAG, "Load not scheduled for ${inst.spec.name}", it)
        }
    }

    /**
     * Decodes [query]'s instrument now (offline export). Returns the instrument when ready,
     * null if it isn't a multisample instrument or couldn't be decoded. Blocking.
     */
    fun loadNow(query: String?): MultisampleInstrument? {
        val inst = find(query) ?: return null
        inst.lastUsedMs = System.currentTimeMillis()
        if (!inst.isReady && !inst.loadFailed) loadBlocking(inst)
        return if (inst.isReady) inst else null
    }

    /** Drops decoded instruments the open project doesn't use (low memory / project switch). */
    fun trim() {
        val keep = pinned
        for (inst in instruments) {
            if (inst.spec.id !in keep && inst.isReady) {
                Log.i(TAG, "Evicting ${inst.spec.name}")
                inst.evict()
            }
        }
    }

    @Synchronized
    private fun loadBlocking(inst: MultisampleInstrument) {
        if (inst.isReady) return
        val ctx = appContext
        if (ctx == null) {
            Log.w(TAG, "Not initialised: can't decode ${inst.spec.name}")
            return
        }
        ensureManifest()
        val refs = inst.sampleRefs
        if (refs.isEmpty()) {
            Log.w(TAG, "No samples listed for ${inst.spec.name}")
            inst.loadFailed = true
            return
        }
        val t0 = System.currentTimeMillis()
        val decoded = ArrayList<MultisampleZone>(refs.size)
        for (ref in refs) {
            val pcm = AssetPcmDecoder.decodeMono(ctx, "$ASSET_DIR/${ref.file}", MAX_ZONE_SECONDS)
            if (pcm == null || pcm.data.size < 2) {
                Log.w(TAG, "Skipping undecodable sample ${ref.file}")
                continue
            }
            decoded.add(MultisampleZone(ref.rootMidi, ref.tuneCents, pcm.data, pcm.sampleRate))
        }
        if (decoded.isEmpty()) {
            Log.e(TAG, "Couldn't decode any sample of ${inst.spec.name}")
            inst.loadFailed = true
            return
        }
        val newBytes = decoded.sumOf { it.data.size.toLong() * 2L }
        makeRoom(newBytes, inst)
        inst.publish(decoded.toTypedArray())
        Log.i(TAG, "${inst.spec.name}: ${decoded.size} zones, ${newBytes / 1024} KB in ${System.currentTimeMillis() - t0} ms")
    }

    /**
     * Keeps decoded audio inside [PcmSampleCache.BUDGET_BYTES] (shared with drum samples) by
     * evicting the least recently used unpinned instruments. Pinned instruments always load.
     */
    private fun makeRoom(incoming: Long, except: MultisampleInstrument) {
        val budget = PcmSampleCache.BUDGET_BYTES
        var used = PcmSampleCache.decodedBytes() + decodedBytes()
        if (used + incoming <= budget) return
        val keep = pinned
        val candidates = instruments
            .filter { it !== except && it.isReady && it.spec.id !in keep }
            .sortedBy { it.lastUsedMs }
        for (inst in candidates) {
            if (used + incoming <= budget) break
            used -= inst.decodedBytes()
            Log.i(TAG, "Over budget: evicting ${inst.spec.name}")
            inst.evict()
        }
        if (used + incoming > budget) {
            Log.w(TAG, "Decoded audio over budget (${(used + incoming) / 1024} KB > ${budget / 1024} KB); the open project needs it")
        }
    }

    private fun ensureManifest() {
        if (manifestLoaded) return
        synchronized(manifestLock) {
            if (manifestLoaded) return
            val ctx = appContext ?: return
            try {
                val text = ctx.assets.open("$ASSET_DIR/manifest.json").bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                source = root.optString("source", DEFAULT_SOURCE).ifBlank { DEFAULT_SOURCE }
                licence = root.optString("licence", root.optString("license", DEFAULT_LICENCE)).ifBlank { DEFAULT_LICENCE }
                author = root.optString("author", DEFAULT_AUTHOR).ifBlank { DEFAULT_AUTHOR }
                url = root.optString("url", DEFAULT_URL).ifBlank { DEFAULT_URL }
                val arr = root.optJSONArray("instruments")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id")
                        val inst = instruments.firstOrNull { it.spec.id == id } ?: continue
                        val samples = o.optJSONArray("samples") ?: continue
                        val refs = ArrayList<MultisampleInstrument.SampleRef>(samples.length())
                        for (j in 0 until samples.length()) {
                            val s = samples.optJSONObject(j) ?: continue
                            val file = s.optString("file")
                            if (file.isBlank() || !s.has("root_midi")) continue
                            refs.add(
                                MultisampleInstrument.SampleRef(
                                    file = file,
                                    rootMidi = s.optInt("root_midi", 60),
                                    tuneCents = s.optDouble("tune_cents", 0.0).toFloat()
                                )
                            )
                        }
                        inst.sampleRefs = refs
                    }
                }
                manifestLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "VCSL manifest failed to load", e)
                manifestLoaded = true // don't retry on every note
            }
        }
    }
}

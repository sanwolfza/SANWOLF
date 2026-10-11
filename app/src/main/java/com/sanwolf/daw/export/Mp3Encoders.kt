package com.sanwolf.daw.export

import android.util.Log
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.MPGLib
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import de.sciss.jump3r.mpg.Interface as MpgInterface

/** An MP3 encoder backend (CBR). */
interface Mp3Encoder {
    /** Encodes interleaved stereo float PCM (-1..1) to [out] at [kbps] CBR, with an ID3v2 tag. */
    fun encode(pcm: FloatArray, sampleRate: Int, kbps: Int, tags: ExportTags, out: File, onProgress: (Float) -> Unit)
}

/**
 * MP3 export. Android has no MP3 encoder, so this uses jump3r (de.sciss:jump3r, a pure-Java port
 * of LAME, LGPL-2.1+), used unmodified as a separate library jar.
 *
 * Only the low-level LAME classes are used: jump3r's convenience `LameEncoder` imports
 * javax.sound.sampled, which Android doesn't have, so it is never touched.
 */
object Mp3Encoders {
    private const val TAG = "Mp3Encoders"

    private val backend: Mp3Encoder? by lazy {
        runCatching {
            // Touch a class so a missing/incompatible jar shows up here, not mid-export
            Class.forName("de.sciss.jump3r.mp3.Lame")
            Jump3rMp3Encoder() as Mp3Encoder
        }.onFailure { Log.e(TAG, "MP3 encoder unavailable", it) }.getOrNull()
    }

    fun isAvailable(): Boolean = backend != null

    fun encode(pcm: FloatArray, sampleRate: Int, kbps: Int, tags: ExportTags, out: File, onProgress: (Float) -> Unit) {
        val b = backend ?: throw IllegalStateException("MP3 encoding isn't available on this build")
        b.encode(pcm, sampleRate, kbps, tags, out, onProgress)
    }
}

/** LAME (jump3r) CBR encoder, joint stereo, quality 2 (LAME "-h"). */
internal class Jump3rMp3Encoder : Mp3Encoder {

    override fun encode(pcm: FloatArray, sampleRate: Int, kbps: Int, tags: ExportTags, out: File, onProgress: (Float) -> Unit) {
        // Module wiring, as in jump3r's own LameEncoder
        val lame = Lame()
        val gaud = GetAudio()
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val mpg = MPGLib()
        val intf = MpgInterface()
        val common = Common()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, p)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        val gfp = lame.lame_init()
        gfp.num_channels = 2
        gfp.in_samplerate = sampleRate
        gfp.out_samplerate = sampleRate
        gfp.mode = MPEGMode.JOINT_STEREO
        gfp.VBR = VbrMode.vbr_off
        gfp.brate = kbps
        gfp.quality = 2
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false // we write our own ID3v2 tag below
        gfp.bWriteVbrTag = false            // no Xing/Info frame (it would need a rewrite at the end)
        gfp.findReplayGain = false
        val rc = lame.lame_init_params(gfp)
        if (rc < 0) throw IllegalStateException("MP3 encoder rejected the settings ($rc)")

        val frames = pcm.size / 2
        val chunk = 1152 * 8
        val left = IntArray(chunk)
        val right = IntArray(chunk)
        val mp3buf = ByteArray((1.25 * chunk + 7200).toInt())
        try {
            BufferedOutputStream(FileOutputStream(out), 1 shl 16).use { os ->
                os.write(Id3v2.build(tags))
                var f = 0
                while (f < frames) {
                    val n = minOf(chunk, frames - f)
                    for (k in 0 until n) {
                        left[k] = toInt32(pcm[(f + k) * 2])
                        right[k] = toInt32(pcm[(f + k) * 2 + 1])
                    }
                    val bytes = lame.lame_encode_buffer_int(gfp, left, right, n, mp3buf, 0, mp3buf.size)
                    if (bytes < 0) throw IllegalStateException("MP3 encoding failed ($bytes)")
                    if (bytes > 0) os.write(mp3buf, 0, bytes)
                    f += n
                    onProgress(f.toFloat() / frames * 0.98f)
                }
                val tail = lame.lame_encode_flush(gfp, mp3buf, 0, mp3buf.size)
                if (tail > 0) os.write(mp3buf, 0, tail)
            }
        } finally {
            runCatching { lame.lame_close(gfp) }
        }
    }

    /** Float -1..1 -> full-scale 32-bit int (LAME scales it down by 2^16 internally). */
    private fun toInt32(v: Float): Int {
        val d = v.toDouble().coerceIn(-1.0, 1.0) * 2147483647.0
        return d.toInt()
    }
}

/** Minimal ID3v2.3 tag: TIT2 (title), TPE1 (artist), TSSE (encoder). UTF-16 with BOM. */
internal object Id3v2 {
    fun build(tags: ExportTags): ByteArray {
        val frames = ByteArrayOutputStream()
        frames.write(textFrame("TIT2", tags.title.ifBlank { "Untitled" }))
        frames.write(textFrame("TPE1", tags.artist.ifBlank { "SANWOLF" }))
        frames.write(textFrame("TSSE", "SANWOLF DAW"))
        val body = frames.toByteArray()
        val size = body.size
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, // version 2.3.0
            0,    // flags
            ((size shr 21) and 0x7F).toByte(),
            ((size shr 14) and 0x7F).toByte(),
            ((size shr 7) and 0x7F).toByte(),
            (size and 0x7F).toByte()
        )
        return header + body
    }

    private fun textFrame(id: String, text: String): ByteArray {
        // Encoding 1 = UTF-16 with BOM; Java's "UTF-16" encoder writes a big-endian BOM.
        val content = byteArrayOf(1) + text.toByteArray(Charsets.UTF_16) + byteArrayOf(0, 0)
        val len = content.size
        return id.toByteArray(Charsets.US_ASCII) + byteArrayOf(
            ((len shr 24) and 0xFF).toByte(),
            ((len shr 16) and 0xFF).toByte(),
            ((len shr 8) and 0xFF).toByte(),
            (len and 0xFF).toByte(),
            0, 0 // frame flags
        ) + content
    }
}

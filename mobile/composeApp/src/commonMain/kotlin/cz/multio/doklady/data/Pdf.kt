package cz.multio.doklady.data

/**
 * PDF z fotek JPEG bez přepočtu obrazu: každá fotka se vloží jako stránka (filtr DCTDecode = původní JPEG),
 * takže PDF je jen o pár stovek bajtů větší než fotky. Šířka stránky 595 bodů (A4), výška podle poměru fotky
 * (dlouhá účtenka = dlouhá stránka). Funguje stejně na Androidu i iPhonu.
 */
object Pdf {
    class JpegInfo(val width: Int, val height: Int, val components: Int)

    /** Rozměry JPEGu ze záhlaví SOF; null = není to JPEG. */
    fun jpegInfo(data: ByteArray): JpegInfo? {
        if (data.size < 4 || (data[0].toInt() and 0xFF) != 0xFF || (data[1].toInt() and 0xFF) != 0xD8) return null
        var i = 2
        while (i + 9 < data.size) {
            if ((data[i].toInt() and 0xFF) != 0xFF) { i++; continue }
            val marker = data[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { i += 2; continue }
            val len = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
            val sof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
            if (sof) {
                val h = ((data[i + 5].toInt() and 0xFF) shl 8) or (data[i + 6].toInt() and 0xFF)
                val w = ((data[i + 7].toInt() and 0xFF) shl 8) or (data[i + 8].toInt() and 0xFF)
                val comps = data[i + 9].toInt() and 0xFF
                return if (w > 0 && h > 0) JpegInfo(w, h, comps) else null
            }
            if (marker == 0xDA || len < 2) return null
            i += 2 + len
        }
        return null
    }

    /** Stránky JPEG → PDF. Vrací null, když některá stránka není JPEG. */
    fun fromJpegs(pages: List<ByteArray>): ByteArray? {
        if (pages.isEmpty()) return null
        val infos = pages.map { jpegInfo(it) ?: return null }
        val out = Buffer()
        val offsets = mutableListOf<Int>()
        fun obj(text: String) { offsets += out.size; out.text(text) }
        out.text("%PDF-1.4\n%âãÏÓ\n")
        val n = pages.size
        // 1 = katalog, 2 = stránky, pak pro každou stranu: stránka, obrázek, obsah
        obj("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        val kids = (0 until n).joinToString(" ") { "${3 + it * 3} 0 R" }
        obj("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $n >>\nendobj\n")
        for (p in 0 until n) {
            val info = infos[p]
            val pageW = 595.0
            val pageH = (pageW * info.height / info.width).coerceIn(200.0, 14400.0)
            val w = fmt(pageW); val h = fmt(pageH)
            val page = 3 + p * 3; val img = page + 1; val content = page + 2
            obj("$page 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] /Resources << /XObject << /Im$p $img 0 R >> >> /Contents $content 0 R >>\nendobj\n")
            val cs = when (info.components) { 1 -> "/DeviceGray"; 4 -> "/DeviceCMYK"; else -> "/DeviceRGB" }
            offsets += out.size
            out.text("$img 0 obj\n<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} /ColorSpace $cs /BitsPerComponent 8 /Filter /DCTDecode" +
                (if (info.components == 4) " /Decode [1 0 1 0 1 0 1 0]" else "") + " /Length ${pages[p].size} >>\nstream\n")
            out.bytes(pages[p])
            out.text("\nendstream\nendobj\n")
            val draw = "q $w 0 0 $h 0 0 cm /Im$p Do Q\n"
            obj("$content 0 obj\n<< /Length ${draw.length} >>\nstream\n${draw}endstream\nendobj\n")
        }
        val xref = out.size
        val sb = StringBuilder("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { sb.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        sb.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.text(sb.toString())
        return out.toByteArray()
    }

    private fun fmt(v: Double): String {
        val r = kotlin.math.round(v * 100) / 100
        return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
    }

    private class Buffer {
        private val chunks = mutableListOf<ByteArray>()
        var size = 0
            private set
        /** Text PDF je ASCII (znaky nad 127 jen v hlavičce – zapíšou se jako jeden bajt). */
        fun text(s: String) = bytes(ByteArray(s.length) { s[it].code.toByte() })
        fun bytes(b: ByteArray) { chunks += b; size += b.size }
        fun toByteArray(): ByteArray {
            val out = ByteArray(size)
            var pos = 0
            for (c in chunks) { c.copyInto(out, pos); pos += c.size }
            return out
        }
    }
}

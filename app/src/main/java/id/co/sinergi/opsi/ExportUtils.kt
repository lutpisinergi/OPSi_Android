package id.co.sinergi.opsi

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

fun exportCsv(context: Context, filename: String, headers: List<String>, rows: List<List<String>>) {
    val file = fileFromCache(context, filename)
    file.writeText(buildString {
        append("\uFEFF")
        append(headers.joinToString(",") { csvEscape(it) }).append('\n')
        rows.forEach { row -> append(row.joinToString(",") { csvEscape(it) }).append('\n') }
    }, Charsets.UTF_8)
    shareExport(context, file, "text/csv")
}

private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""

fun exportReportsPdf(context: Context, title: String, rows: List<JSONObject>, visitReport: Boolean) {
    val document = PdfDocument()
    val pageWidth = 842
    val pageHeight = 595
    val margin = 28f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = android.graphics.Color.rgb(51, 65, 85) }
    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = android.graphics.Color.rgb(100, 116, 139) }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; color = android.graphics.Color.rgb(15, 23, 42); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = android.graphics.Color.rgb(15, 23, 42); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(3, 183, 202) }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(203, 213, 225); strokeWidth = 1f }

    val rowHeight = if (visitReport) 142f else 164f
    val usableHeight = pageHeight - 115f
    val rowsPerPage = (usableHeight / rowHeight).toInt().coerceAtLeast(1)
    val totalPages = maxOf(1, (rows.size + rowsPerPage - 1) / rowsPerPage)

    for (pageNumber in 0 until totalPages) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create())
        val canvas = page.canvas
        canvas.drawText("OPSi  |  PT. SINERGI Integra Services", margin, 28f, labelPaint)
        canvas.drawText(title, margin, 56f, titlePaint)
        canvas.drawText("Tanggal cetak: ${todayIso()}    •    Total data: ${rows.size}", margin, 75f, smallPaint)
        canvas.drawRect(margin, 88f, pageWidth - margin, 91f, headerPaint)

        val start = pageNumber * rowsPerPage
        val end = minOf(rows.size, start + rowsPerPage)
        var y = 103f
        for (index in start until end) {
            val item = rows[index]
            canvas.drawRoundRect(RectF(margin, y, pageWidth - margin, y + rowHeight - 8f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(248, 250, 252) })
            val left = margin + 12f
            val textWidth = if (visitReport) 470f else 435f
            val date = item.text("tanggal")
            val area = item.text("area")
            val person = item.text(if (visitReport) "area_koordinator_nama" else "nama")
            val detail = item.text(if (visitReport) "notice" else "uraian")
            canvas.drawText("${index + 1}. $date  |  $area", left, y + 18f, labelPaint)
            canvas.drawText("Penginput: $person", left, y + 36f, bodyPaint)
            wrapText(canvas, detail, left, y + 55f, bodyPaint, textWidth, 4)
            if (visitReport) {
                canvas.drawText("Keterangan: ${parseVisitTags(item.text("keterangan", "[]")).joinToString(", ")}", left, y + 118f, smallPaint)
                val image = dataUrlToBitmap(item.text("foto_kunjungan", ""), 520)
                if (image != null) drawPhoto(canvas, image, 625f, y + 10f, 160f, 112f, paint)
            } else {
                val photos: List<String> = listOf(item.text("foto_before", ""), item.text("foto_progress", ""), item.text("foto_after", ""))
                val labels = listOf("Before", "Progress", "After")
                photos.forEachIndexed { photoIndex, encoded ->
                    val image = dataUrlToBitmap(encoded, 320)
                    if (image != null) {
                        val x = 520f + photoIndex * 94f
                        drawPhoto(canvas, image, x, y + 10f, 86f, 85f, paint)
                        canvas.drawText(labels[photoIndex], x, y + 108f, smallPaint)
                    }
                }
            }
            canvas.drawLine(margin + 6f, y + rowHeight - 8f, pageWidth - margin - 6f, y + rowHeight - 8f, linePaint)
            y += rowHeight
        }
        canvas.drawText("Halaman ${pageNumber + 1} dari $totalPages", pageWidth - margin - 90f, pageHeight - 18f, smallPaint)
        document.finishPage(page)
    }

    val safeName = if (visitReport) "Rekap-Kunjungan-${todayIso()}.pdf" else "Rekap-Laporan-Pekerjaan-${todayIso()}.pdf"
    val file = fileFromCache(context, safeName)
    FileOutputStream(file).use { output -> document.writeTo(output) }
    document.close()
    shareExport(context, file, "application/pdf")
}

private fun wrapText(canvas: Canvas, value: String, x: Float, startY: Float, paint: Paint, maxWidth: Float, maxLines: Int) {
    val words = value.replace('\n', ' ').split(Regex("\\s+")).filter(String::isNotBlank)
    var line = ""
    var y = startY
    var lines = 0
    for (word in words) {
        val candidate = if (line.isBlank()) word else "$line $word"
        if (paint.measureText(candidate) > maxWidth && line.isNotBlank()) {
            canvas.drawText(line, x, y, paint)
            y += 13f
            lines++
            line = word
            if (lines >= maxLines) break
        } else line = candidate
    }
    if (lines < maxLines && line.isNotBlank()) canvas.drawText(line, x, y, paint)
}

private fun drawPhoto(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, width: Float, height: Float, paint: Paint) {
    val scale = minOf(width / bitmap.width, height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val dst = RectF(x + (width - w) / 2f, y + (height - h) / 2f, x + (width + w) / 2f, y + (height + h) / 2f)
    canvas.drawRoundRect(RectF(x, y, x + width, y + height), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    canvas.drawBitmap(bitmap, null, dst, paint)
    bitmap.recycle()
}

fun shareExport(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan hasil export OPSi"))
}

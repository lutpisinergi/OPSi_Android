package id.co.sinergi.opsi

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a small, standards-based XLSX workbook with embedded visit photos (not links). */
fun exportVisitXlsx(context: Context, rows: List<JSONObject>) {
    val file = fileFromCache(context, "Rekap-Laporan-Kunjungan-${todayIso()}.xlsx")
    val imageBytes = rows.map { row -> bitmapJpeg(row.text("foto_kunjungan", "")) }
    ZipOutputStream(FileOutputStream(file)).use { zip ->
        zipEntry(zip, "[Content_Types].xml", contentTypesXml(imageBytes.count { it != null }))
        zipEntry(zip, "_rels/.rels", rootRelsXml())
        zipEntry(zip, "xl/workbook.xml", workbookXml())
        zipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
        zipEntry(zip, "xl/worksheets/sheet1.xml", visitSheetXml(rows))
        zipEntry(zip, "xl/worksheets/_rels/sheet1.xml.rels", sheetRelsXml())
        zipEntry(zip, "xl/drawings/drawing1.xml", drawingXml(imageBytes))
        zipEntry(zip, "xl/drawings/_rels/drawing1.xml.rels", drawingRelsXml(imageBytes))
        imageBytes.forEachIndexed { index, bytes -> if (bytes != null) {
            zip.putNextEntry(ZipEntry("xl/media/image${index + 1}.jpg"))
            zip.write(bytes)
            zip.closeEntry()
        } }
    }
    shareExport(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

private fun bitmapJpeg(dataUrl: String): ByteArray? = runCatching {
    val bitmap = dataUrlToBitmap(dataUrl, 900) ?: return null
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
    bitmap.recycle()
    output.toByteArray()
}.getOrNull()

private fun zipEntry(zip: ZipOutputStream, name: String, content: String) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
}

private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

private fun cell(ref: String, value: String): String = "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"

private fun visitSheetXml(rows: List<JSONObject>): String {
    val headers = listOf("Tanggal", "Area", "Client", "Penginput", "Keterangan", "Notice", "Foto Kunjungan")
    val sheetRows = buildString {
        append("<row r=\"1\" ht=\"28\" customHeight=\"1\">")
        headers.forEachIndexed { index, header -> append(cell("${columnName(index + 1)}1", header)) }
        append("</row>")
        rows.forEachIndexed { index, row ->
            val rowNumber = index + 2
            append("<row r=\"$rowNumber\" ht=\"112\" customHeight=\"1\">")
            val values = listOf(
                row.text("tanggal"), row.text("area"), row.text("client"),
                row.text("area_koordinator_nama"), parseVisitTags(row.text("keterangan", "[]")).joinToString(", "),
                row.text("notice"), ""
            )
            values.forEachIndexed { col, value -> append(cell("${columnName(col + 1)}$rowNumber", value)) }
            append("</row>")
        }
    }
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>
<sheetFormatPr defaultRowHeight="21"/><cols>
<col min="1" max="1" width="15" customWidth="1"/><col min="2" max="2" width="25" customWidth="1"/><col min="3" max="3" width="24" customWidth="1"/><col min="4" max="4" width="25" customWidth="1"/><col min="5" max="5" width="25" customWidth="1"/><col min="6" max="6" width="42" customWidth="1"/><col min="7" max="7" width="32" customWidth="1"/>
</cols><sheetData>$sheetRows</sheetData><pageMargins left="0.25" right="0.25" top="0.5" bottom="0.5" header="0.2" footer="0.2"/><drawing r:id="rId1"/></worksheet>"""
}

private fun columnName(column: Int): String {
    var n = column
    var result = ""
    while (n > 0) {
        val rem = (n - 1) % 26
        result = ('A'.code + rem).toChar() + result
        n = (n - 1) / 26
    }
    return result
}

private fun drawingXml(images: List<ByteArray?>): String {
    val anchors = buildString {
        images.forEachIndexed { index, bytes -> if (bytes != null) {
            val row = index + 1
            val pictureId = index + 1
            append("""
<xdr:oneCellAnchor>
<xdr:from><xdr:col>6</xdr:col><xdr:colOff>76200</xdr:colOff><xdr:row>$row</xdr:row><xdr:rowOff>38100</xdr:rowOff></xdr:from>
<xdr:ext cx="1905000" cy="1333500"/>
<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="$pictureId" name="Foto Kunjungan $pictureId"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId$pictureId"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1905000" cy="1333500"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic>
<xdr:clientData/></xdr:oneCellAnchor>
""")
        } }
    }
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">$anchors</xdr:wsDr>"""
}

private fun contentTypesXml(imageCount: Int): String {
    val imageOverrides = if (imageCount > 0) "" else ""
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>$imageOverrides
</Types>"""
}

private fun rootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

private fun workbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Rekap Kunjungan" sheetId="1" r:id="rId1"/></sheets></workbook>"""

private fun workbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""

private fun sheetRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>"""

private fun drawingRelsXml(images: List<ByteArray?>): String {
    val relations = images.mapIndexedNotNull { index, bytes -> if (bytes != null) {
        "<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image${index + 1}.jpg\"/>"
    } else null }.joinToString("")
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$relations</Relationships>"""
}

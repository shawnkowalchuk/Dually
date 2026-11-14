package com.shawnkowalchuk.milo.data.transfer

import java.io.File
import java.io.OutputStream

// Android's files for the export and the import, whose work is shared with the iPhone since
// 2026-11-13 (DataExport and DataImport in :shared): the calls Android always made, with its
// streams turned into what the shared code reads and writes.

/** Writes the export to [out], as UTF-8. The stream is left open: the caller opened it. */
suspend fun DataExport.writeTo(out: OutputStream, includePoints: Boolean): ExportWritten {
    val writer = out.bufferedWriter(Charsets.UTF_8)
    val written = writeTo(writer as Appendable, includePoints)
    writer.flush()
    return written
}

/** Reads and checks the whole of [file], and changes nothing. */
suspend fun DataImport.check(file: File): ExportReading =
    check { file.bufferedReader(Charsets.UTF_8) }

/** See [DataImport.replacePoints]: the points of [file]. */
suspend fun DataImport.replacePoints(file: File, export: CheckedExport, highestTripId: Long): Long =
    replacePoints({ file.bufferedReader(Charsets.UTF_8) }, export, highestTripId)

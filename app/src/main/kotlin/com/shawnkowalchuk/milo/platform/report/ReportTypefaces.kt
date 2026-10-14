package com.shawnkowalchuk.milo.platform.report

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.theme.HEADLINE_WIDTH
import com.shawnkowalchuk.milo.core.designsystem.theme.TEXT_WIDTH
import com.shawnkowalchuk.milo.core.report.ReportFace
import com.shawnkowalchuk.milo.core.report.ReportTextStyle

// The one place the PDF asks for its typefaces. They are the screens' own, the same three
// files (`core/designsystem/theme/Type.kt` names them for Compose), so the report reads like
// the app it was made in.

/** The heaviest weight that is still drawn with the lighter of the two figure files. */
private const val FIGURES_MEDIUM_UP_TO = 500

/**
 * The app's font files, as Android reads them.
 *
 * @param archivo `res/font/archivo.ttf`: running text, and extra bold and widened the app's
 * name. A variable font, one file that holds every weight and width, so each paint tells it how
 * to draw itself; what it is not told, it draws at the file's default, which is semi-bold.
 * @param figuresMedium and [figuresSemiBold] are IBM Plex Mono, one file for each weight.
 */
internal class ReportTypefaces(
    val archivo: Typeface,
    val figuresMedium: Typeface,
    val figuresSemiBold: Typeface,
) {
    /** Sets [paint] up to draw [style]: its file, and for Archivo its weight and its width. */
    fun applyTo(paint: Paint, style: ReportTextStyle) {
        when (style.face) {
            ReportFace.TEXT -> paint.setArchivo(style.weight, TEXT_WIDTH)

            ReportFace.HEADLINE -> paint.setArchivo(style.weight, HEADLINE_WIDTH)

            ReportFace.FIGURES ->
                paint.typeface =
                    if (style.weight <= FIGURES_MEDIUM_UP_TO) figuresMedium else figuresSemiBold
        }
    }

    private fun Paint.setArchivo(weight: Int, width: Float) {
        typeface = archivo
        // "wght" and "wdth" are the names every variable font gives these two axes. Both are
        // named every time: an axis that is left out is drawn at the file's default.
        val taken = setFontVariationSettings("'wght' $weight, 'wdth' $width")
        check(taken) { "The report's typeface has no weight or no width to set: not Archivo" }
    }
}

/** Reads the three files. Called for each PDF, off the main thread. */
internal fun readReportTypefaces(context: Context): ReportTypefaces {
    val resources = context.applicationContext.resources
    return ReportTypefaces(
        archivo = resources.getFont(R.font.archivo),
        figuresMedium = resources.getFont(R.font.ibm_plex_mono_medium),
        figuresSemiBold = resources.getFont(R.font.ibm_plex_mono_semibold),
    )
}

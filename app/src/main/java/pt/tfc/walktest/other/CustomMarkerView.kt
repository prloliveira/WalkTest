package pt.tfc.walktest.other

import android.content.Context
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import kotlinx.android.synthetic.main.marker_view_filters.view.*
import pt.tfc.walktest.db.Session
import java.text.SimpleDateFormat
import java.util.*

class CustomMarkerView(
    val sessions: List<Session>,
    c: Context,
    layoutId: Int
) : MarkerView(c, layoutId) {

    override fun getOffset(): MPPointF {
        return MPPointF(-width / 2f, -height.toFloat())
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        super.refreshContent(e, highlight)
        if(e == null) {
            return
        }
        val curRunId = e.x.toInt()
        val run = sessions[curRunId]

        val calendar = Calendar.getInstance().apply {
            timeInMillis = run.timestamp
        }

        val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

        sessionDate.text = dateFormat.format(calendar.time)


        val distanceInKm = "${run.distanceInMeters / 1000f}km"
        sessionDistance.text = distanceInKm

        tvDuration.text = TrackingUtility.getFormattedStopWatchTime(run.timeInMillis)


    }

}
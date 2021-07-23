package pt.tfc.walktest.adapters

import android.app.AlertDialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.amplifyframework.core.Amplify
import com.androiddevs.walktest.R
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.item_session.view.*
import pt.tfc.walktest.db.Session
import pt.tfc.walktest.other.TrackingUtility
import pt.tfc.walktest.ui.viewmodels.MainViewModel
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RunAdapter(var viewModel: MainViewModel) : RecyclerView.Adapter<RunAdapter.RunViewHolder>() {

    inner class RunViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    val diffCallback = object : DiffUtil.ItemCallback<Session>() {
        override fun areItemsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Session, newItem: Session): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    fun submitList(list: List<Session>) = differ.submitList(list)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        return RunViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_session,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        val run = differ.currentList[position]
        holder.itemView.apply {
            Glide.with(this).load(run.img).into(sessionMap)

            val calendar = Calendar.getInstance().apply {
                timeInMillis = run.timestamp
            }

            val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            sessionDate.text = dateFormat.format(calendar.time)

            val distanceInKm = "${run.distanceInMeters / 1000f}km"
            sessionDistance.text = distanceInKm

            sessionTime.text = TrackingUtility.getFormattedStopWatchTime(run.timeInMillis)

            val exportBuilder: AlertDialog.Builder = AlertDialog.Builder(context)

            exportBuilder
                .setTitle("Export this session to AWS?")
                .setMessage("CAREFUL. Cost may apply to the student. TESTS ONLY.")
                .setCancelable(false)
                .setPositiveButton("Yes"
                ) { _, _ ->
                    //Timber.d("Capture Session: ${run.captureSession}"
                    val filename = "${run.captureSession[0].split(",")[0]}-WalkTest"
                    val sessionFile = File(context.filesDir, filename)

                    for (capture in run.captureSession) {
                        sessionFile.appendText(capture + "\n")
                    }

                    // Timber.d(sessionFile.name)
                    Timber.d("File content: ${sessionFile.readLines()}")

                    Amplify.Storage.uploadFile(filename, sessionFile,
                        { Log.i("MyAmplifyApp", "Successfully uploaded: ${it.key}") },
                        { Log.e("MyAmplifyApp", "Upload failed", it) }
                    ) }
                .setNegativeButton("No"
                ) { dialog, _ -> dialog.cancel() }

            val exportAlert: AlertDialog = exportBuilder.create()

            exportButton.setOnClickListener {
                Timber.d("EXPORT")
                exportAlert.show()
            }

            val deleteBuilder: AlertDialog.Builder = AlertDialog.Builder(context)

            deleteBuilder
                .setTitle("Delete this session?")
                .setMessage("All data from this session will be lost.")
                .setCancelable(false)
                .setPositiveButton("Yes"
                ) { _, _ -> viewModel.deleteSession(run) }
                .setNegativeButton("No"
                ) { dialog, _ -> dialog.cancel() }

            val deleteAlert: AlertDialog = deleteBuilder.create()

            deleteButton.setOnClickListener {
                Timber.d("DELETE")
                deleteAlert.show()
            }
        }
    }

}
package org.radarcns.detail

import android.content.Context
import android.os.AsyncTask
import android.widget.ProgressBar
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class DownloadFileFromUrl(context: Context, delegate: TaskDelegate) :
    AsyncTask<String?, Int?, String?>() {

    private val mDelegate: TaskDelegate = delegate

    private val mContext: Context = context

    var bar: ProgressBar? = null

    override fun doInBackground(vararg params: String?): String? {
        var input: InputStream? = null
        var output: OutputStream? = null
        var connection: HttpURLConnection? = null
        try {
            val url = URL(params[0])
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            // expect HTTP 200 OK, so we don't mistakenly save error report
            // instead of the file
            if (connection.responseCode !== HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + connection.responseCode
                    .toString() + " " + connection.responseMessage
            }

            // this will be useful to display download percentage
            // might be -1: server did not report the length
            val fileLength: Int = connection.contentLength

            // download the file
            input = connection.inputStream

            val outputFile = File(mContext.filesDir, DOWNLOADED_FILE)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            outputFile.setReadable(true, false)
            output = FileOutputStream(outputFile)

            val data = ByteArray(1024) //4096
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                // allow canceling with back button
                if (isCancelled) {
                    input.close()
                    return null
                }
                total += count.toLong()
                // publishing the progress....
                if (fileLength > 0) // only if total length is known
                    publishProgress((total * 100 / fileLength).toInt())
                output.write(data, 0, count)
            }
        } catch (e: Exception) {
            return e.toString()
        } finally {
            try {
                output?.close()
                input?.close()
            } catch (ignored: IOException) {
            }
            connection?.disconnect()
        }
        return null
    }

    override fun onProgressUpdate(vararg values: Int?) {
        super.onProgressUpdate(*values)
        bar?.progress = values[0]!!
    }

    override fun onPostExecute(s: String?) {
        super.onPostExecute(s)
        mDelegate.taskCompletionResult("Post Exec")
    }

    fun setProgressBar(bar: ProgressBar?) {
        this.bar = bar
    }

    companion object {
        const val DOWNLOADED_FILE = "app-release.apk"
    }
}

interface TaskDelegate {
    fun taskCompletionResult(result: String?)
}


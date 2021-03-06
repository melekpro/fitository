package `in`.kiranrao.fitository

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.google.android.gms.awareness.Awareness
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED
import com.google.android.gms.fitness.Fitness
import timber.log.Timber


private val REQUEST_CODE_PLAYSERVICES_RESOLUTION = 1000
private val PERMISSION_REQUEST_CODE_MULTIPLE = 1001


class MainActivity : AppCompatActivity(),
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private lateinit var googleApiClient: GoogleApiClient
    private var resolvingError = false

    private val awarenessApiController: AwarenessApiController by lazy {
        AwarenessApiController(this, googleApiClient)
    }

    private val fitController: FitController by lazy {
        FitController(this, googleApiClient)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions()
    }

    fun refreshTodayTotals(v: View): Unit {
        fitController.retrieveDailyTotals()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) googleApiClient.connect()
    }

    /**
     * Build a [GoogleApiClient] that will authenticate the user and allow the application
     * to connect to Awareness APIs. Authentication will occasionally fail intentionally,
     * and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     * can address. Examples of this include the user never having signed in before, or having
     * multiple accounts on the device and needing to specify which account to use, etc.
     */
    private fun buildGoogleApiClient() {
        googleApiClient = GoogleApiClient.Builder(this)
                .addApi(Awareness.API)
                .addApi(Fitness.RECORDING_API)
                .addApi(Fitness.HISTORY_API)
                .addScope(Fitness.SCOPE_ACTIVITY_READ_WRITE)
                .addConnectionCallbacks(this)
                .enableAutoManage(this, 0, this)
                .build()
    }

    private fun showPlayServicesError(result: ConnectionResult) {
        Toast.makeText(
                this,
                "Exception while connecting to Google Play services: ${result.errorMessage}",
                Toast.LENGTH_LONG).show()
    }

    private fun checkPermissions() {
        if (hasAllPermissions(ACCESS_FINE_LOCATION)) {
            startGooglePlayServices()
            return
        }
        requestPermissions()

    }

    private fun hasAllPermissions(vararg permissions: String): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PERMISSION_GRANTED) return false
        }
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this,
                arrayOf(ACCESS_FINE_LOCATION, WRITE_EXTERNAL_STORAGE,"com.google.android.gms.permission.ACTIVITY_RECOGNITION"),
                PERMISSION_REQUEST_CODE_MULTIPLE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE_MULTIPLE ->
                if (grantResults.all { it == PERMISSION_GRANTED }) startGooglePlayServices()
                else showNotStartedMessage()
        }
    }

    private fun startGooglePlayServices() {
        buildGoogleApiClient()
    }

    private fun showNotStartedMessage() {
        AlertDialog.Builder(this)
                .setMessage(R.string.permission_denied_quitting)
                .setNeutralButton(R.string.ok) { _, i -> quit() }
                .create()
                .show()

    }

    private fun quit() {
        finish()
    }


    override fun onConnected(bundle: Bundle?) {
        Timber.i("Connected!!!")
        useAwarenessAPi()
        useFitApi()
    }

    private fun useAwarenessAPi() {
        awarenessApiController.addSolarFence()
    }

    private fun useFitApi() {
        fitController.startRecordingFitnessData()
    }

    override fun onConnectionSuspended(i: Int) {
        val message = when (i) {
            CAUSE_NETWORK_LOST -> "Connection lost.  Cause: Network Lost."
            CAUSE_SERVICE_DISCONNECTED -> "Connection lost.  Reason: Service Disconnected"
            else -> "Connection lost. Unknown reason"
        }

        Timber.i(message)
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        if (resolvingError) return
        if (result.hasResolution()) {
            try {
                resolvingError = true
                result.startResolutionForResult(this, REQUEST_CODE_PLAYSERVICES_RESOLUTION)
            } catch (e: IntentSender.SendIntentException) {
                googleApiClient.connect()
            }

        } else {
            resolvingError = true
            Timber.i("Google Play services connection failed. Cause: ${result.toString()}")
            showPlayServicesError(result)
        }
    }
}


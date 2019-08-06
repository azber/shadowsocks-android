package com.github.shadowsocks

import android.app.Activity
import android.app.backup.BackupManager
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.util.Log
import android.widget.Button
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceDataStore
import com.crashlytics.android.Crashlytics
import com.github.shadowsocks.aidl.IShadowsocksService
import com.github.shadowsocks.aidl.ShadowsocksConnection
import com.github.shadowsocks.aidl.TrafficStats
import com.github.shadowsocks.bg.BaseService
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.ProfileManager
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.preference.OnPreferenceDataStoreChangeListener
import com.github.shadowsocks.utils.Key

import kotlinx.android.synthetic.main.activity_vpn.*

class VpnActivity : AppCompatActivity(), ShadowsocksConnection.Callback, OnPreferenceDataStoreChangeListener {

    var state = BaseService.State.Idle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        changeState(BaseService.State.Idle) // reset everything to init state
        connection.connect(this, this)

        val mBtnVpnOptions = findViewById<Button>(R.id.btn_vpn_options)
        mBtnVpnOptions.setOnClickListener { toggle() }

        val sharedStr = "ss://Y2hhY2hhMjA6N2Y2ZmU1QHYyLnNoZG93c3MueHl6OjE0NDg0"

        val feature = Core.currentProfile?.first
        val profiles = Profile.findAllUrls(sharedStr, feature).toList()

        if (profiles.isNotEmpty()) {
            profiles.forEach {
                ProfileManager.createProfile(it)
                Core.switchProfile(it.id)
            }
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.serviceMode -> handler.post {
                connection.disconnect(this)
                connection.connect(this, this)
            }
        }
    }

    private val handler = Handler()
    private val connection = ShadowsocksConnection(handler, true)

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) =
            changeState(state, msg, true)

    override fun onServiceConnected(service: IShadowsocksService) = changeState(try {
        BaseService.State.values()[service.state]
    } catch (_: DeadObjectException) {
        BaseService.State.Idle
    })

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }


    companion object {
        private const val TAG = "ShadowsocksVpnActivity"
        private const val REQUEST_CONNECT = 1

        var stateListener: ((BaseService.State) -> Unit)? = null
    }

    private fun changeState(state: BaseService.State, msg: String? = null, animate: Boolean = false) {
        this.state = state
        ProfilesFragment.instance?.profilesAdapter?.notifyDataSetChanged()  // refresh button enabled state
        VpnActivity.stateListener?.invoke(state)
    }


    override fun onStart() {
        super.onStart()
        connection.bandwidthTimeout = 500
    }

    override fun onStop() {
        connection.bandwidthTimeout = 0
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        DataStore.publicStore.unregisterChangeListener(this)
        connection.disconnect(this)
        BackupManager(this).dataChanged()
        handler.removeCallbacksAndMessages(null)
    }

    private fun toggle() = when {
        state.canStop -> {
            Core.stopService()
        }
        DataStore.serviceMode == Key.modeVpn -> {
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, VpnActivity.REQUEST_CONNECT)
            else onActivityResult(VpnActivity.REQUEST_CONNECT, Activity.RESULT_OK, null)
        }
        else -> Core.startService()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when {
            requestCode != VpnActivity.REQUEST_CONNECT -> super.onActivityResult(requestCode, resultCode, data)
            resultCode == Activity.RESULT_OK -> Core.startService()
            else -> {
                Crashlytics.log(Log.ERROR, VpnActivity.TAG, "Failed to start VpnService from onActivityResult: $data")
            }
        }
    }

    override fun trafficUpdated(profileId: Long, stats: TrafficStats) {}

    override fun trafficPersisted(profileId: Long) {}
}

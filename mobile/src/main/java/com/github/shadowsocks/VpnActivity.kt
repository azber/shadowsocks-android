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

// demo
class VpnActivity : BaseVpnActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_vpn)
        setSupportActionBar(toolbar)

        val sharedStr = "ss://Y2hhY2hhMjA6N2Y2ZmU1QHYyLnNoZG93c3MueHl6OjE0NDg0"
        vpnConnection.addProfile(sharedStr)

        val mBtnVpnOptions = findViewById<Button>(R.id.btn_vpn_options)
        mBtnVpnOptions.setOnClickListener { toggle() }
    }
}

abstract class BaseVpnActivity : AppCompatActivity() {

    protected lateinit var vpnConnection: VPNConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpnConnection = VPNConnection(this)
    }

    companion object {
        private const val TAG = "ShadowsocksVpnActivity"
        private const val REQUEST_CONNECT = 1
    }

    protected fun toggle() = vpnConnection.toggle(BaseVpnActivity.REQUEST_CONNECT)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when {
            requestCode != BaseVpnActivity.REQUEST_CONNECT -> super.onActivityResult(requestCode, resultCode, data)
            resultCode == Activity.RESULT_OK -> Core.startService()
            else -> {
                Crashlytics.log(Log.ERROR, BaseVpnActivity.TAG, "Failed to start VpnService from onActivityResult: $data")
            }
        }
    }
}

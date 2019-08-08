package com.github.shadowsocks

import android.app.Activity
import android.net.VpnService
import android.os.DeadObjectException
import android.os.Handler
import com.github.shadowsocks.aidl.IShadowsocksService
import com.github.shadowsocks.aidl.ShadowsocksConnection
import com.github.shadowsocks.bg.BaseService
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.ProfileManager
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.utils.Key


class VPNConnection(val mContext: Activity) : ShadowsocksConnection.Callback {

    companion object {
        private const val TAG = "ShadowsocksVpnConnection"
        private const val REQUEST_CONNECT = 1

        var stateListener: ((BaseService.State) -> Unit)? = null
    }

    private val handler = Handler()

    private val connection = ShadowsocksConnection(handler, true)
    var state = BaseService.State.Idle


    init {
        changeState(BaseService.State.Idle)
        // reset everything to init state
        connection.connect(mContext, this)
    }

    fun addProfile(data: String) {
        val feature = Core.currentProfile?.first
        val profiles = Profile.findAllUrls(data, feature).toList()

        if (profiles.isNotEmpty()) {
            profiles.forEach {
                ProfileManager.createProfile(it)
                // 切换代理
                Core.switchProfile(it.id)
            }
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) =
            changeState(state, msg, true)

    override fun onServiceConnected(service: IShadowsocksService) = changeState(try {
        BaseService.State.values()[service.state]
    } catch (_: DeadObjectException) {
        BaseService.State.Idle
    })

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
//        connection.disconnect(mContext)
//        connection.connect(mContext, this)
    }


    private fun changeState(state: BaseService.State, msg: String? = null, animate: Boolean = false) {
        this.state = state
        VPNConnection.stateListener?.invoke(state)
    }

    fun toggle(requestCode: Int) = when {
        state.canStop -> Core.stopService()
        DataStore.serviceMode == Key.modeVpn -> {
            val intent = VpnService.prepare(mContext)
            if (intent != null) mContext.startActivityForResult(intent, requestCode)
            else Core.startService()
        }
        else -> Core.startService()
    }
}

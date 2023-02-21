package zone.bitverse.connect

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import zone.bitverse.connect.impls.BCClient
import zone.bitverse.connect.impls.MoshiPayloadAdapter
import zone.bitverse.connect.impls.OkHttpTransport
import java.net.URLEncoder

class BitverseConnectApi(private val delegate: BitverseConnectDelegate?) {
    private var client: Client? = null
    private val okClient = OkHttpClient.Builder().build()
    private val moshi = Moshi.Builder().build()
    private var url: String? = null


    /**
     * dappName：dapp的名称
     * dappDescription：dapp的描述
     * dappUrl：dapp对应的链接
     * bitverseDapp：当前app的deeplink或者Universal Links，以便Bitverse产生相关结果后可以回调回到当前app
     */
    fun connect(
        context: Context,
        dappName: String,
        dappDescription: String,
        dappUrl: String,
        icons: List<String>,
        callbackUrl: String
    ) {
        client = BCClient(
            Session.PeerMeta(
                name = dappName,
                url = dappUrl,
                description = dappDescription,
                icons = icons
            ),
            MoshiPayloadAdapter(moshi),
            OkHttpTransport.Builder(okClient, moshi),
            { status ->
//                websocket status
                when (status) {
                    is Session.Transport.Status.Connected -> Log.d(TAG, "websocket status:$status")
                    is Session.Transport.Status.Disconnected -> delegate?.didDisconnect()
                    is Session.Transport.Status.Error -> delegate?.failedToConnect()
                }
            },
            { call ->
                // 处理用户拒绝连接
                Log.e(TAG, "handleMessages $call")
                when (call) {
                    is Session.MethodCall.SessionUpdate -> delegate?.didDisconnect()
                    else -> {}
                }
            }
        )
        val wsURL = client?.connect(
            Client.Config(
                bridge = "https://bridge.walletconnect.org",
                topic = null,
                key = null,
                peerId = null
            )
        ) { resp ->
            if (resp.result != null) {
                val param = resp.result as Map<*, *>
                val accounts = param["accounts"] as List<String>
                val chainId = param["chainId"] as Double
                delegate?.didConnect(chainId.toInt(), accounts)
            }
        }
        url = "$wsURL"+ URLEncoder.encode(URLEncoder.encode("&callbackUrl=${URLEncoder.encode(callbackUrl)}"));
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        context.startActivity(intent)
    }

    fun ethSign(
        context: Context,
        message: String,
        account: String,
        callback: (Session.MethodCall.Response) -> Unit
    ) {
        client?.ethSign(message, account, callback)
        startBitverseApp(context)
    }

    fun personalSign(
        context: Context,
        message: String,
        account: String,
        callback: (Session.MethodCall.Response) -> Unit
    ) {
        client?.personalSign(message, account, callback)
        startBitverseApp(context)
    }

    fun ethSignTypedData(
        context: Context,
        message: String,
        account: String,
        callback: (Session.MethodCall.Response) -> Unit
    ) {
        client?.ethSignTypedData(message, account, callback)
        startBitverseApp(context)
    }

    fun ethSendTransaction(
        context: Context,
        transaction: Transaction,
        callback: (Session.MethodCall.Response) -> Unit
    ) {
        client?.ethSendTransaction(transaction, callback)
        startBitverseApp(context)
    }

    fun disconnect() {
        client?.disconnect()
    }

    fun reconnectIfNeeded() {
        client?.reconnectIfNeeded()
    }

    private fun startBitverseApp(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("bitverseapp://open/wallet")
        }
        context.startActivity(intent)
    }

    companion object {
        const val TAG = "BitverseConnectApi"
    }
}
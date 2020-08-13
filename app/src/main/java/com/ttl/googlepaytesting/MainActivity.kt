package com.ttl.googlepaytesting

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Task
import com.google.android.gms.wallet.*
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*


class MainActivity : AppCompatActivity() {

    lateinit var payment: PaymentsClient
    var LOCATION_ID = 1
    var LOAD_PAYMENT_DATA_REQUEST_CODE = 432

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var walletOptions = Wallet.WalletOptions.Builder().setEnvironment(WalletConstants.ENVIRONMENT_TEST).build()
        payment = Wallet.getPaymentsClient(this, walletOptions)
        pay_with_google_button.setOnClickListener {
            requestPayment()
        }
    }

    fun requestPayment() {
        pay_with_google_button.isClickable = false

        // The price provided to the API should include taxes and shipping.
        // This price is not displayed to the user.
        try {
            val paymentDataRequestJson: Optional<JSONObject> =
                getPaymentDataRequest(1.00.toLong())
            if (!paymentDataRequestJson.isPresent) {
                return
            }
            val request =
                PaymentDataRequest.fromJson(paymentDataRequestJson.get().toString())

            // Since loadPaymentData may show the UI asking the user to select a payment method, we use
            // AutoResolveHelper to wait for the user interacting with it. Once completed,
            // onActivityResult will be called with the result.
            if (request != null) {
                AutoResolveHelper.resolveTask(
                    payment.loadPaymentData(request),
                    this, LOAD_PAYMENT_DATA_REQUEST_CODE
                )
            }
        } catch (e: JSONException) {
            throw RuntimeException("The price cannot be deserialized from the JSON object.")
        }
    }

    private fun handleError(statusCode: Int) {
        Log.w(
            "loadPaymentData failed",
            String.format("Error code: %d", statusCode)
        )
    }

    private fun handlePaymentSuccess(paymentData: PaymentData) {

        // Token will be null if PaymentDataRequest was not constructed using fromJson(String).
        val paymentInfo = paymentData.toJson() ?: return
        try {
            val paymentMethodData =
                JSONObject(paymentInfo).getJSONObject("paymentMethodData")
            // If the gateway is set to "example", no payment information is returned - instead, the
            // token will only consist of "examplePaymentMethodToken".
            val tokenizationData = paymentMethodData.getJSONObject("tokenizationData")
            val tokenizationType = tokenizationData.getString("type")
            val token = tokenizationData.getString("token")
            if ("PAYMENT_GATEWAY" == tokenizationType && "examplePaymentMethodToken" == token) {
                AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("HEYYYYYYYYY")
                    .setPositiveButton("OK", null)
                    .create()
                    .show()
            }
            val info = paymentMethodData.getJSONObject("info")
            val billingName = info.getJSONObject("billingAddress").getString("name")
            Toast.makeText(
                this, billingName,
                Toast.LENGTH_LONG
            ).show()

            // Logging token string.
            Log.d("Google Pay token: ", token)
        } catch (e: JSONException) {
            throw java.lang.RuntimeException("The selected garment cannot be parsed from the list of elements")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            LOAD_PAYMENT_DATA_REQUEST_CODE -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        val paymentData = PaymentData.getFromIntent(data!!)
                        handlePaymentSuccess(paymentData!!)
                    }
                    Activity.RESULT_CANCELED -> {
                    }
                    AutoResolveHelper.RESULT_ERROR -> {
                        val status: Status? = AutoResolveHelper.getStatusFromIntent(data)
                        handleError(status?.getStatusCode()!!)
                    }
                }

                // Re-enables the Google Pay payment button.
                pay_with_google_button.setClickable(true)
            }
        }
    }

    fun getPaymentDataRequest(priceCents: Long): Optional<JSONObject> {
        return try {
            val paymentDataRequest: JSONObject = getBaseRequest()
            paymentDataRequest.put(
                "allowedPaymentMethods", JSONArray().put(getCardPaymentMethod())
            )
            paymentDataRequest.put("transactionInfo", getTransactionInfo(priceCents.toString()))
            paymentDataRequest.put("merchantInfo", getMerchantInfo())

            /* An optional shipping address requirement is a top-level property of the PaymentDataRequest
          JSON object. */paymentDataRequest.put("shippingAddressRequired", false)
            val shippingAddressParameters = JSONObject()
            shippingAddressParameters.put("phoneNumberRequired", false)
            val allowedCountryCodes = JSONArray().put("CA")
            shippingAddressParameters.put("allowedCountryCodes", allowedCountryCodes)
            paymentDataRequest.put("shippingAddressParameters", shippingAddressParameters)
            Optional.of(paymentDataRequest)
        } catch (e: JSONException) {
            Optional.empty()
        }
    }

    @Throws(JSONException::class)
    private fun getTransactionInfo(price: String): JSONObject? {
        val transactionInfo = JSONObject()
        transactionInfo.put("totalPrice", price)
        transactionInfo.put("totalPriceStatus", "FINAL")
        transactionInfo.put("countryCode", "CA")
        transactionInfo.put("currencyCode", "CAD")
        transactionInfo.put("checkoutOption", "COMPLETE_IMMEDIATE_PURCHASE")
        return transactionInfo
    }

    fun setGooglePayAvailable(avail: Boolean?) {
        if (avail!!) {
            pay_with_google_button.visibility = View.VISIBLE
        } else {
            pay_with_google_button.visibility = View.GONE
        }
    }

    @Throws(JSONException::class)
    private fun getMerchantInfo(): JSONObject {
        return JSONObject().put("merchantName", "Just Boardrooms")
    }

    private fun possiblyShowGooglePayButton() {
        val isReadyToPayJson: Optional<JSONObject> = getIsReadyToPayRequest()
        if (!isReadyToPayJson.isPresent) {
            return
        }

        val request =
            IsReadyToPayRequest.fromJson(isReadyToPayJson.get().toString())
        val task: Task<Boolean> =
            payment.isReadyToPay(request)
        task.addOnCompleteListener(
            this
        ) { task ->
            if (task.isSuccessful) {
                setGooglePayAvailable(task.result)
            } else {
                Log.w("isReadyToPay failed", task.exception)
            }
        }
    }

    fun getIsReadyToPayRequest() : Optional<JSONObject> {
        try {
            var isReady = getBaseRequest()
            isReady.put("allowedPaymentMethods", getBaseCardPaymentMethod())
            return Optional.of(isReady)
        } catch (e : Exception) {
            Log.e("getIsReady", e.localizedMessage)
            return Optional.empty()
        }
    }

    fun getBaseRequest() : JSONObject {
        return JSONObject().put("apiVersion", 2).put("apiVersionMinor", 0)
    }
    fun getGatewayTokenizationSpecification() : JSONObject {
        return JSONObject()
            .put("type", "PAYMENT_GATEWAY")
            .put("parameters",
                JSONObject()
                    .put("gateway", "example")
                    .put("gatewayMerchantId", "exampleGatewayMerchantId")
                )
    }
    fun getAllowedCardNetworks() : JSONArray {
        return JSONArray()
            .put("MASTERCARD")
            .put("VISA")
    }
    fun getAllowedCardAuthMethods() : JSONArray {
        return JSONArray()
            .put("PAN_ONLY")
            .put("CRYPTOGRAM_3DS")
    }

    fun getBaseCardPaymentMethod(): JSONObject {
        val cardPaymentMethod = JSONObject()
        cardPaymentMethod.put("type", "CARD")
        val parameters = JSONObject()
        parameters.put("allowedAuthMethods", getAllowedCardAuthMethods())
        parameters.put("allowedCardNetworks", getAllowedCardNetworks())
        // Optionally, you can add billing address/phone number associated with a CARD payment method.
        parameters.put("billingAddressRequired", true)
        val billingAddressParameters = JSONObject()
        billingAddressParameters.put("format", "MIN")
        parameters.put("billingAddressParameters", billingAddressParameters)
        cardPaymentMethod.put("parameters", parameters)
        return cardPaymentMethod
    }

    @Throws(JSONException::class)
    private fun getCardPaymentMethod(): JSONObject {
        val cardPaymentMethod = getBaseCardPaymentMethod()
        cardPaymentMethod.put("tokenizationSpecification", getGatewayTokenizationSpecification())
        return cardPaymentMethod
    }
}

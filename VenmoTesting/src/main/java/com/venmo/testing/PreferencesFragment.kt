package com.venmo.testing

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.braintreepayments.api.BraintreeClient
import com.braintreepayments.api.Configuration
import com.braintreepayments.api.VenmoAccountNonce
import com.braintreepayments.api.VenmoClient
import com.braintreepayments.api.VenmoLineItem
import com.braintreepayments.api.VenmoListener
import com.braintreepayments.api.VenmoPaymentMethodUsage
import com.braintreepayments.api.VenmoRequest

class PreferencesFragment : PreferenceFragmentCompat(), VenmoListener {

    private lateinit var braintreeClient: BraintreeClient
    private lateinit var venmoClient: VenmoClient

    private var variant: SwitchPreferenceCompat? = null
    private var fallbackWeb: SwitchPreferenceCompat? = null
    private var webURLs: ListPreference? = null
    private var customWebURL: EditTextPreference? = null
    private var environment: SwitchPreferenceCompat? = null
    private var merchantId: EditTextPreference? = null
    private var merchants: ListPreference? = null
    private var paymentIntent: SwitchPreferenceCompat? = null
    private var amount: ListPreference? = null
    private var finalAmount: SwitchPreferenceCompat? = null
    private var shipping: SwitchPreferenceCompat? = null
    private var billing: SwitchPreferenceCompat? = null
    private var launch: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        initComponents()
        setClient()
        setListeners()
    }

    private fun initComponents() {
        variant = findPreference(VARIANT)
        fallbackWeb = findPreference(FALLBACK_WEB)
        webURLs = findPreference(WEB_URLS)
        customWebURL = findPreference(CUSTOM_WEB_URL)
        environment = findPreference(ENVIRONMENT)
        merchantId = findPreference(MERCHANT_ID)
        merchants = findPreference(MERCHANTS)
        paymentIntent = findPreference(PAYMENT_INTENT)
        amount = findPreference(AMOUNT)
        finalAmount = findPreference(FINAL_AMOUNT)
        shipping = findPreference(SHIPPING)
        billing = findPreference(BILLING)
        launch = findPreference(LAUNCH)

        fallbackWeb?.apply {
            webURLs?.isVisible = isChecked == true
            customWebURL?.isVisible = isChecked == true
        }

        webURLs?.apply {
            summary = "Using: $entry"
            customWebURL?.isVisible = fallbackWeb?.isChecked == true && value == CUSTOM_WEB
        }

        customWebURL?.apply {
            summary = if (text == null) {
                "Type custom URL here"
            } else {
                "Using: $text"
            }
        }

        environment?.apply {
            merchants?.apply {
                if (isChecked.not()) {
                    entries = resources.getStringArray(R.array.sandbox_merchants_entries)
                    entryValues = resources.getStringArray(R.array.sandbox_merchants_values)
                } else {
                    entries = resources.getStringArray(R.array.production_merchants_entries)
                    entryValues = resources.getStringArray(R.array.production_merchants_values)
                }
            }
        }

        merchantId?.apply {
            summary = if (this.text.isNullOrEmpty().not()) {
                "Profile selected: ${this.text}"
            } else {
                "Profile selected: default (no selection)"
            }
        }

        merchants?.apply {
            summary = if (value.isNullOrBlank().not() && value == merchantId?.text) {
                "Using: $entry"
            } else if (merchantId?.text.isNullOrEmpty().not()) {
                "Custom Merchant Profile Id selected"
            } else {
                "Profile selected: default (no selection)"
            }
        }

        amount?.apply {
            summary = "Selected option: $entry"
            finalAmount?.isVisible = value != null && value != "no_amount"
        }
    }

    private fun setClient() {
        context?.let {
            braintreeClient = BraintreeClient(
                it,
                if (environment?.isChecked == true) PRODUCTION_ENVIRONMENT else SANDBOX_ENVIRONMENT
            )
        }
        venmoClient =
            VenmoClient(this, braintreeClient).also {
                it.setListener(this)
            }
    }

    private fun setListeners() {
        fallbackWeb?.setOnPreferenceChangeListener { _, newValue ->
            webURLs?.isVisible = newValue as Boolean
            customWebURL?.isVisible = newValue
            return@setOnPreferenceChangeListener true
        }

        webURLs?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                customWebURL?.isVisible = newValue == CUSTOM_WEB

                val value = entries[findIndexOfValue(newValue as String?)]
                summary = "Using: $value"
                return@setOnPreferenceChangeListener true
            }
        }

        customWebURL?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                summary = if (newValue == null) {
                    "Type custom URL here"
                } else {
                    "Using: $newValue"
                }
                return@setOnPreferenceChangeListener true
            }
        }

        environment?.setOnPreferenceChangeListener { _, _ ->
            merchantId?.text = ""
            startActivity(Intent.makeRestartActivityTask(activity?.intent?.component))
            return@setOnPreferenceChangeListener true
        }

        merchantId?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                summary = if (newValue.toString().isNotEmpty()) {
                    "Profile selected: $newValue"
                } else {
                    "Profile selected: default (no selection)"
                }

                merchants?.apply {
                    summary = try {
                        val value = entries[findIndexOfValue(newValue as String?)]
                        "Using: $value"
                    } catch (e: Exception) {
                        if (newValue.toString().isNotEmpty()) {
                            "Custom Merchant Profile Id selected"
                        } else {
                            "Profile selected: default (no selection)"
                        }
                    }
                }
                return@setOnPreferenceChangeListener true
            }

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }

        merchants?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                merchantId?.apply {
                    text = newValue.toString()
                    summary = "Profile selected: $newValue"
                }

                val value = entries[findIndexOfValue(newValue as String?)]
                summary = "Using: $value"
                return@setOnPreferenceChangeListener true
            }
        }

        amount?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                finalAmount?.isVisible = newValue != "no_amount"
                val value = entries[findIndexOfValue(newValue as String?)]
                summary = "Selected option $value"
                return@setOnPreferenceChangeListener true
            }
        }

        launch?.setOnPreferenceClickListener {
            context?.let {
                if (fallbackWeb?.isChecked == true && webURLs?.value == CUSTOM_WEB
                    && customWebURL?.text == null
                ) {
                    Toast.makeText(it, "Type Custom Web URL", Toast.LENGTH_LONG).show()
                    return@setOnPreferenceClickListener true
                }
            }

            launch?.isEnabled = false

            braintreeClient.getConfiguration { _: Configuration?, _: Exception? ->
                val venmoRequest = VenmoRequest(getPaymentMethodUsage()).apply {
                    profileId = merchantId?.text
                    shouldVault = paymentIntent?.isChecked == true

                    collectCustomerShippingAddress = shipping?.isChecked == true
                    collectCustomerBillingAddress = billing?.isChecked == true

                    val lineItems = ArrayList<VenmoLineItem>()
                    if (amount?.value == "only_amount") {
                        totalAmount = "10"
                        isFinalAmount = finalAmount?.isChecked == true
                    } else if (amount?.value == "amount_line_items") {
                        lineItems.add(VenmoLineItem(VenmoLineItem.KIND_DEBIT, "Item 1", 1, "2"))
                        lineItems.add(VenmoLineItem(VenmoLineItem.KIND_DEBIT, "Item 2", 2, "5"))
                        lineItems.add(VenmoLineItem(VenmoLineItem.KIND_CREDIT, "Discount", 1, "2"))
                        subTotalAmount = "10"
                        taxAmount = "0.5"
                        shippingAmount = "0.5"
                        discountAmount = "1"
                        totalAmount = "10"
                        isFinalAmount = finalAmount?.isChecked == true
                    }
                    setLineItems(lineItems)
                    fallbackToWeb = fallbackWeb?.isChecked == true
                }

                with(venmoClient) {
                    setApplicationVariant(getVariant())
                    if (venmoRequest.fallbackToWeb) {
                        if (webURLs?.value == CUSTOM_WEB) {
                            setFallbackWebURL(customWebURL?.text.orEmpty())
                        } else {
                            setFallbackWebURL(webURLs?.value.orEmpty())
                        }
                    }
                    activity?.let { tokenizeVenmoAccount(it, venmoRequest) }
                }
            }
            return@setOnPreferenceClickListener true
        }
    }

    private fun getPaymentMethodUsage() =
        if (paymentIntent?.isChecked == true) VenmoPaymentMethodUsage.MULTI_USE else VenmoPaymentMethodUsage.SINGLE_USE

    private fun getVariant() =
        if (variant?.isChecked == true) VENMO_RELEASE_PACKAGE else VENMO_DEBUG_PACKAGE

    override fun onVenmoSuccess(venmoAccountNonce: VenmoAccountNonce) {
        val sb = StringBuilder("User approved transaction")
        sb.append("\nusername: ${venmoAccountNonce.username}")
        venmoAccountNonce.firstName?.let { sb.append("\nfirstname: $it") }
        venmoAccountNonce.lastName?.let { sb.append("\nlastname: $it") }
        venmoAccountNonce.email?.let { sb.append("\nemail: $it") }
        venmoAccountNonce.phoneNumber?.let { sb.append("\nphone-number: $it") }
        venmoAccountNonce.shippingAddress?.streetAddress?.let { sb.append("\nshipping-address: $it") }
        venmoAccountNonce.billingAddress?.streetAddress?.let { sb.append("\nbilling-address: $it") }
        sb.append("\npayment-nonce: ${venmoAccountNonce.string}")
        venmoAccountNonce.externalId?.let { sb.append("\nexternal-id: $it") }

        showDialog(sb.toString())
        launch?.isEnabled = true
    }

    override fun onVenmoFailure(error: Exception) {
        showDialog(error.toString())
        launch?.isEnabled = true
    }

    private fun showDialog(message: String) {
        activity?.let {
            AlertDialog.Builder(it)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                }
                .create()
                .show()
        }
    }

    private companion object {
        const val VENMO_RELEASE_PACKAGE = "com.venmo"
        const val VENMO_DEBUG_PACKAGE = "com.venmo.fifa"
        const val SANDBOX_ENVIRONMENT = "sandbox_zjxmgb4x_38kx88m8y5bf27c6"
        const val PRODUCTION_ENVIRONMENT = "production_4xbgn8rb_vwfg3wgq8b3n3xss"
        const val CUSTOM_WEB = "custom"

        const val VARIANT = "variant"
        const val FALLBACK_WEB = "fallback_web"
        const val WEB_URLS = "web_urls"
        const val CUSTOM_WEB_URL = "custom_web_url"
        const val ENVIRONMENT = "environment"
        const val MERCHANT_ID = "merchant_id"
        const val MERCHANTS = "merchants"
        const val PAYMENT_INTENT = "payment_intent"
        const val AMOUNT = "amount"
        const val FINAL_AMOUNT = "final_amount"
        const val SHIPPING = "shipping"
        const val BILLING = "billing"
        const val LAUNCH = "launch"
    }
}

package com.yonsn76.freeaiconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.yonsn76.freeaiconnect.providers.AIProvider
import com.yonsn76.freeaiconnect.storage.PrefsManager
import java.util.Locale

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var prefsManager: PrefsManager
    var onSettingsSaved: (() -> Unit)? = null

    private lateinit var btnClose: ImageButton
    private lateinit var spinnerProvider: Spinner
    private lateinit var spinnerModel: Spinner
    private lateinit var containerOpenRouter: LinearLayout
    private lateinit var containerGoogle: LinearLayout
    private lateinit var containerOpenAI: LinearLayout
    private lateinit var containerMistral: LinearLayout
    private lateinit var etKeyOpenRouter: EditText
    private lateinit var etKeyGoogle: EditText
    private lateinit var etKeyOpenAI: EditText
    private lateinit var etKeyMistral: EditText
    private lateinit var sliderTemperature: SeekBar
    private lateinit var tvTemperatureValue: TextView
    private lateinit var sliderMaxTokens: SeekBar
    private lateinit var tvMaxTokensValue: TextView
    private lateinit var etSystemPrompt: EditText
    private lateinit var btnSave: MaterialButton

    private val providers = AIProvider.getAllProviders()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefsManager = PrefsManager(requireContext())

        // ── Find all views ──────────────────────────────────────
        btnClose = view.findViewById(R.id.btn_close_settings)
        spinnerProvider = view.findViewById(R.id.spinner_provider)
        spinnerModel = view.findViewById(R.id.spinner_model)
        containerOpenRouter = view.findViewById(R.id.container_openrouter)
        containerGoogle = view.findViewById(R.id.container_google)
        containerOpenAI = view.findViewById(R.id.container_openai)
        containerMistral = view.findViewById(R.id.container_mistral)
        etKeyOpenRouter = view.findViewById(R.id.et_key_openrouter)
        etKeyGoogle = view.findViewById(R.id.et_key_google)
        etKeyOpenAI = view.findViewById(R.id.et_key_openai)
        etKeyMistral = view.findViewById(R.id.et_key_mistral)
        sliderTemperature = view.findViewById(R.id.slider_temperature)
        tvTemperatureValue = view.findViewById(R.id.tv_temperature_value)
        sliderMaxTokens = view.findViewById(R.id.slider_max_tokens)
        tvMaxTokensValue = view.findViewById(R.id.tv_max_tokens_value)
        etSystemPrompt = view.findViewById(R.id.et_system_prompt)
        btnSave = view.findViewById(R.id.btn_save_settings)

        // ── Close button ────────────────────────────────────────
        btnClose.setOnClickListener { dismiss() }

        // ── Provider Spinner ────────────────────────────────────
        val providerNames = providers.map { it.name }
        val providerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            providerNames
        )
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProvider.adapter = providerAdapter

        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                updateModelSpinner(position)
                showApiKeyFieldForProvider(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ── Temperature SeekBar ─────────────────────────────────
        sliderTemperature.max = 100
        sliderTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvTemperatureValue.text = String.format(Locale.US, "%.2f", progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ── Max Tokens SeekBar ──────────────────────────────────
        sliderMaxTokens.max = 8192
        sliderMaxTokens.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val tokens = maxOf(64, progress)
                tvMaxTokensValue.text = String.format(Locale.US, "%d", tokens)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ── Save button ─────────────────────────────────────────
        btnSave.setOnClickListener { saveSettings() }

        // ── Load saved values ───────────────────────────────────
        loadSettings()
    }

    private fun updateModelSpinner(providerIndex: Int) {
        val provider = providers.getOrNull(providerIndex) ?: return
        val modelAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            provider.models
        )
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModel.adapter = modelAdapter

        // Restore saved model index only if provider matches the saved one
        if (providerIndex == prefsManager.getSelectedProviderIndex()) {
            val savedModelIndex = prefsManager.getSelectedModelIndex()
            if (savedModelIndex < provider.models.size) {
                spinnerModel.setSelection(savedModelIndex)
            }
        } else {
            spinnerModel.setSelection(0)
        }
    }

    private fun showApiKeyFieldForProvider(providerIndex: Int) {
        containerOpenRouter.visibility = View.GONE
        containerGoogle.visibility = View.GONE
        containerOpenAI.visibility = View.GONE
        containerMistral.visibility = View.GONE

        when (providerIndex) {
            0 -> containerOpenRouter.visibility = View.VISIBLE
            1 -> containerGoogle.visibility = View.VISIBLE
            2 -> containerOpenAI.visibility = View.VISIBLE
            3 -> containerMistral.visibility = View.VISIBLE
        }
    }

    private fun loadSettings() {
        // Provider
        val savedProviderIndex = prefsManager.getSelectedProviderIndex()
        if (savedProviderIndex < providers.size) {
            spinnerProvider.setSelection(savedProviderIndex)
        }

        // Show API key field for selected provider
        showApiKeyFieldForProvider(savedProviderIndex.coerceAtMost(providers.size - 1))

        // API keys (all 4 providers)
        etKeyOpenRouter.setText(prefsManager.getApiKey("OpenRouter"))
        etKeyGoogle.setText(prefsManager.getApiKey("Google"))
        etKeyOpenAI.setText(prefsManager.getApiKey("OpenAI"))
        etKeyMistral.setText(prefsManager.getApiKey("Mistral"))

        // Temperature
        val tempRaw = prefsManager.getTemperatureRaw()
        sliderTemperature.progress = tempRaw
        tvTemperatureValue.text = String.format(Locale.US, "%.2f", tempRaw / 100f)

        // Max tokens
        val maxTokens = prefsManager.getMaxTokens()
        sliderMaxTokens.progress = maxTokens
        tvMaxTokensValue.text = String.format(Locale.US, "%d", maxTokens)

        // System prompt
        etSystemPrompt.setText(prefsManager.getSystemPrompt())
    }

    private fun saveSettings() {
        // Save provider index
        val providerIndex = spinnerProvider.selectedItemPosition
        prefsManager.setSelectedProviderIndex(providerIndex)

        // Save model index
        val modelIndex = spinnerModel.selectedItemPosition
        prefsManager.setSelectedModelIndex(modelIndex)

        // Save all API keys
        prefsManager.setApiKey("OpenRouter", etKeyOpenRouter.text.toString().trim())
        prefsManager.setApiKey("Google", etKeyGoogle.text.toString().trim())
        prefsManager.setApiKey("OpenAI", etKeyOpenAI.text.toString().trim())
        prefsManager.setApiKey("Mistral", etKeyMistral.text.toString().trim())

        // Save temperature
        prefsManager.setTemperature(sliderTemperature.progress / 100f)

        // Save max tokens
        val tokens = maxOf(64, sliderMaxTokens.progress)
        prefsManager.setMaxTokens(tokens)

        // Save system prompt
        prefsManager.setSystemPrompt(etSystemPrompt.text.toString())

        // Notify and dismiss
        onSettingsSaved?.invoke()
        dismiss()
    }
}

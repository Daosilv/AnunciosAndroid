package com.example.geradordeanncios.ui.telegram

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.geradordeanncios.R
import com.example.geradordeanncios.databinding.FragmentTelegramVideoBinding
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ShopeeResponse(
    val data: ShopeeData?
)

@Serializable
data class ShopeeData(
    @SerialName("productOfferV2")
    val productOffer: ProductOffer
)

@Serializable
data class ProductOffer(
    val nodes: List<ProductNode>?
)

@Serializable
data class ProductNode(
    val productName: String,
    val price: String? = null,
    val priceMin: String? = null,
    val priceMax: String? = null,
    val commissionRate: String? = null,
    val commission: String? = null,
    val imageUrl: String? = null,
    val productLink: String? = null,
    val offerLink: String? = null
)

@Serializable
data class ShortLinkResponse(
    val data: ShortLinkData?
)

@Serializable
data class ShortLinkData(
    val generateShortLink: GenerateShortLink
)

@Serializable
data class GenerateShortLink(
    val shortLink: String
)

class TelegramVideoFragment : Fragment() {

    private var _binding: FragmentTelegramVideoBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isFetching = false
    private var lastCheckedExclusivityId: Int = -1
    private var lastCheckedShippingId: Int = -1
    private var lastCheckedFromPriceId: Int = -1
    private var lastCheckedCouponLinkId: Int = -1
    private var lastCheckedGroupLinkId: Int = -1
    private var lastCheckedPaymentDiscountId: Int = -1

    private var couponLink = "https://s.shopee.com.br/1g76ck3c1x"
    private var groupLink = "https://t.me/+exemplo"
    private var currentVideoUri: Uri? = null

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    companion object {
        private const val APP_ID = "18344110677"
        private const val SECRET = "BEWYLTPASZH2TJXVMQUQVGU3YBSYX64T"
        private const val API_URL = "https://open-api.affiliate.shopee.com.br/graphql"
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            currentVideoUri = it
            loadVideoPreview(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTelegramVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        initializePreview()
    }
    
    private fun initializePreview() {
        binding.videoPreviewWebview.apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            visibility = View.VISIBLE
            loadDataWithBaseURL(null, getEmptyPreviewHtml(), "text/html", "UTF-8", null)
        }
    }
    
    private fun getEmptyPreviewHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { 
                        width: 100%; height: 100%; 
                        background: #f0f0f0; 
                        overflow: hidden;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .placeholder {
                        text-align: center;
                        color: #666;
                        font-family: Arial, sans-serif;
                    }
                </style>
            </head>
            <body>
                <div class="placeholder">
                    <p>Toque no botão "Anexar Vídeo" para selecionar um vídeo da galeria</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun setupListeners() {
        binding.attachVideoButton.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        binding.editCouponLinkButton.setOnClickListener {
            showEditLinkDialog("Editar Link de Cupons", couponLink) { newLink ->
                couponLink = newLink
                Toast.makeText(requireContext(), "Link de cupons atualizado!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editGroupLinkButton.setOnClickListener {
            showEditLinkDialog("Editar Link do Canal Telegram", groupLink) { newLink ->
                groupLink = newLink
                Toast.makeText(requireContext(), "Link do canal atualizado!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.clearButton.setOnClickListener { clearAllFields() }
        binding.copyAdButton.setOnClickListener { copyAdToClipboard() }
        setupUrlAutoFill()
        setupToggleableRadioButtons()
    }

    private fun setupToggleableRadioButtons() {
        setupToggleGroup(
            listOf(binding.primeRadio, binding.meliPlusRadio, binding.exclusiveRadio),
            binding.exclusivityGroup
        ) { lastCheckedExclusivityId = it }
        
        setupToggleGroup(
            listOf(binding.freeShippingRadio, binding.couponShippingRadio, binding.freeShippingAboveRadio),
            binding.shippingOptionsGroup
        ) { lastCheckedShippingId = it }
        
        binding.fromPriceCheckbox.setOnClickListener { v ->
            if (lastCheckedFromPriceId == v.id) {
                (v as android.widget.RadioButton).isChecked = false
                lastCheckedFromPriceId = -1
            } else {
                lastCheckedFromPriceId = v.id
            }
        }
        
        binding.couponLinkCheckbox.setOnClickListener { v ->
            if (lastCheckedCouponLinkId == v.id) {
                (v as android.widget.RadioButton).isChecked = false
                lastCheckedCouponLinkId = -1
            } else {
                lastCheckedCouponLinkId = v.id
            }
        }
        
        binding.groupLinkCheckbox.setOnClickListener { v ->
            if (lastCheckedGroupLinkId == v.id) {
                (v as android.widget.RadioButton).isChecked = false
                lastCheckedGroupLinkId = -1
            } else {
                lastCheckedGroupLinkId = v.id
            }
        }
        
        binding.paymentScreenDiscountCheckbox.setOnClickListener { v ->
            if (lastCheckedPaymentDiscountId == v.id) {
                (v as android.widget.CheckBox).isChecked = false
                lastCheckedPaymentDiscountId = -1
            } else {
                lastCheckedPaymentDiscountId = v.id
            }
        }
    }
    
    private fun setupToggleGroup(radioButtons: List<android.widget.RadioButton>, radioGroup: android.widget.RadioGroup, updateLastChecked: (Int) -> Unit) {
        var lastCheckedId = -1
        radioButtons.forEach { radioButton ->
            radioButton.setOnClickListener { v ->
                if (lastCheckedId == v.id) {
                    radioGroup.clearCheck()
                    lastCheckedId = -1
                    updateLastChecked(-1)
                } else {
                    lastCheckedId = v.id
                    updateLastChecked(v.id)
                }
            }
        }
    }

    private fun setupUrlAutoFill() {
        binding.originalLinkEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }
                val url = s.toString().trim()
                if (url.contains("shopee.com.br") && binding.originalLinkEditText.isFocused && !isFetching) {
                    searchRunnable = Runnable { generateShortLink(url) }
                    handler.postDelayed(searchRunnable!!, 800)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        binding.associateLinkEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }
                val url = s.toString().trim()
                if (url.contains("shopee.com.br") && binding.associateLinkEditText.isFocused && !isFetching) {
                    searchRunnable = Runnable { fetchProductDetails(url) }
                    handler.postDelayed(searchRunnable!!, 800)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun generateShortLink(originalUrl: String) {
        if (isFetching) return
        isFetching = true

        lifecycleScope.launch {
            try {
                val timestamp = System.currentTimeMillis() / 1000
                val mutation = """mutation{
    generateShortLink(input:{originUrl:"$originalUrl",subIds:["s1","s2","s3","s4","s5"]}){
        shortLink
    }
}"""
                val payload = buildJsonObject { put("query", mutation) }.toString()
                
                Log.d("ShortLinkAPI", "Timestamp: $timestamp")
                Log.d("ShortLinkAPI", "Payload: $payload")
                
                val signature = generateSignature(timestamp, payload)
                Log.d("ShortLinkAPI", "Signature gerada: $signature")

                val response = client.post(API_URL) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "SHA256 Credential=$APP_ID, Timestamp=$timestamp, Signature=$signature")
                    setBody(payload)
                }

                val responseText = response.bodyAsText()
                Log.d("ShortLinkAPI", "Resposta API: $responseText")

                if (responseText.contains("errors")) {
                    val errorMessage = responseText.substringAfter("message\":\"").substringBefore("\"")
                    Log.e("ShortLinkAPI", "Erro da API: $errorMessage")
                    Toast.makeText(requireContext(), "❌ Erro ao gerar shortLink: $errorMessage", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val shortLinkResponse = Json { ignoreUnknownKeys = true }.decodeFromString<ShortLinkResponse>(responseText)
                val shortLink = shortLinkResponse.data?.generateShortLink?.shortLink

                if (shortLink != null) {
                    Log.d("ShortLinkAPI", "ShortLink gerado: $shortLink")
                    binding.associateLinkEditText.setText(shortLink)
                    Toast.makeText(requireContext(), "✅ ShortLink gerado com sucesso!", Toast.LENGTH_SHORT).show()
                    
                    // Agora buscar detalhes do produto usando o shortLink
                    fetchProductDetails(shortLink)
                } else {
                    Log.w("ShortLinkAPI", "API retornou sucesso, mas sem shortLink.")
                    Toast.makeText(requireContext(), "❌ Falha ao gerar shortLink.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ShortLinkAPI", "Exceção ao gerar shortLink: ${e.message}", e)
                Toast.makeText(requireContext(), "❌ Falha na conexão com a API.", Toast.LENGTH_SHORT).show()
            } finally {
                isFetching = false
            }
        }
    }

    private fun fetchProductDetails(url: String) {
        if (isFetching) return
        isFetching = true

        lifecycleScope.launch {
            try {
                var finalUrl = url
                if (url.contains("s.shopee.com.br")) {
                    val response: HttpResponse = client.get(url)
                    finalUrl = response.request.url.toString()
                    Log.d("ShopeeAPI", "Link resolvido: $finalUrl")
                }

                val (shopId, itemId) = extractIds(finalUrl)
                if (shopId == null || itemId == null) {
                    Log.w("ShopeeAPI", "IDs não encontrados na URL: $finalUrl")
                    Toast.makeText(requireContext(), "❌ IDs não encontrados no link.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d("ShopeeAPI", "Tentando API - ShopID: $shopId, ItemID: $itemId")

                val timestamp = System.currentTimeMillis() / 1000
                val query = "{ productOfferV2(shopId: $shopId, itemId: $itemId) { nodes { commissionRate commission imageUrl price priceMin priceMax productLink offerLink productName } }}"
                val payload = buildJsonObject { put("query", query) }.toString()
                
                Log.d("ShopeeAPI", "Timestamp: $timestamp")
                Log.d("ShopeeAPI", "Payload: $payload")
                
                val signature = generateSignature(timestamp, payload)
                Log.d("ShopeeAPI", "Signature gerada: $signature")

                val response = client.post(API_URL) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "SHA256 Credential=$APP_ID, Timestamp=$timestamp, Signature=$signature")
                    setBody(payload)
                }

                val responseText = response.bodyAsText()
                Log.d("ShopeeAPI", "Resposta API: $responseText")

                if (responseText.contains("errors")) {
                    val errorMessage = responseText.substringAfter("message\":\"").substringBefore("\"")
                    Log.e("ShopeeAPI", "Erro da API: $errorMessage")
                    Toast.makeText(requireContext(), "❌ Erro da API: $errorMessage", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val shopeeResponse = Json { ignoreUnknownKeys = true }.decodeFromString<ShopeeResponse>(responseText)
                val product = shopeeResponse.data?.productOffer?.nodes?.firstOrNull()

                if (product != null) {
                    Log.d("ShopeeAPI", "Produto encontrado via API: ${product.productName}")
                    binding.adTitleEditText.setText(product.productName)
                    
                    val priceValue = product.priceMin ?: product.price
                    if (priceValue != null) {
                        binding.priceEditText.setText(formatPrice(priceValue))
                    }
                    
                    val priceMin = product.priceMin?.toDoubleOrNull()
                    val priceMax = product.priceMax?.toDoubleOrNull()
                    
                    if (priceMin != null && priceMax != null && priceMin < priceMax) {
                        binding.fromPriceCheckbox.isChecked = true
                        lastCheckedFromPriceId = binding.fromPriceCheckbox.id
                        Log.d("ShopeeAPI", "Checkbox 'A partir de' marcado: priceMin=$priceMin < priceMax=$priceMax")
                    } else {
                        binding.fromPriceCheckbox.isChecked = false
                        lastCheckedFromPriceId = -1
                        Log.d("ShopeeAPI", "Checkbox 'A partir de' desmarcado: priceMin=$priceMin, priceMax=$priceMax")
                    }
                    
                    Toast.makeText(requireContext(), "✅ Produto encontrado via API!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("ShopeeAPI", "API retornou sucesso, mas sem dados do produto.")
                    Toast.makeText(requireContext(), "❌ Produto não encontrado na API.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ShopeeAPI", "Exceção ao tentar API: ${e.message}", e)
                Toast.makeText(requireContext(), "❌ Falha na conexão com a API.", Toast.LENGTH_SHORT).show()
            } finally {
                isFetching = false
            }
        }
    }

    private fun loadVideoPreview(videoUri: Uri) {
        Log.d("VideoPreview", "Carregando preview do vídeo: $videoUri")
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { 
                        width: 100%; height: 100%; 
                        background: #000; 
                        overflow: hidden;
                    }
                    .video-container { 
                        width: 100%; height: 100%; 
                        display: flex; align-items: center; justify-content: center;
                    }
                    video { 
                        width: 100%; height: 100%; 
                        object-fit: contain;
                        display: block;
                    }
                </style>
            </head>
            <body>
                <div class="video-container">
                    <video controls preload="metadata">
                        <source src="$videoUri" type="video/mp4">
                        <source src="$videoUri" type="video/webm">
                        <source src="$videoUri" type="video/ogg">
                        Seu navegador não suporta o elemento de vídeo.
                    </video>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        binding.videoPreviewWebview.apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            visibility = View.VISIBLE
        }
    }

    private fun extractIds(url: String): Pair<String?, String?> {
        Regex(".*?/(\\d+)/(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                val shopId = it.groupValues[1]
                val itemId = it.groupValues[2]
                return Pair(shopId, itemId)
            }
        }
        Regex(".*i\\.(\\d+)\\.(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                val shopId = it.groupValues[1]
                val itemId = it.groupValues[2]
                return Pair(shopId, itemId)
            }
        }
        Regex(".*item_id=(\\d+).*shop_id=(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                val itemId = it.groupValues[1]
                val shopId = it.groupValues[2]
                return Pair(shopId, itemId)
            }
        }
        return Pair(null, null)
    }

    private fun generateSignature(timestamp: Long, payload: String): String {
        val factor = "$APP_ID$timestamp$payload$SECRET"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(factor.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun formatPrice(price: String): String {
        return try {
            val priceValue = price.toDoubleOrNull()
            if (priceValue != null) {
                String.format("%.2f", priceValue).replace('.', ',')
            } else {
                price.replace('.', ',')
            }
        } catch (e: Exception) {
            price.replace('.', ',')
        }
    }

    private fun showEditLinkDialog(title: String, currentLink: String, onSave: (String) -> Unit) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            val input = EditText(requireContext()).apply { setText(currentLink) }
            setView(input)
            setPositiveButton("Salvar") { dialog, _ ->
                onSave(input.text.toString())
                dialog.dismiss()
            }
            setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
            show()
        }
    }
    
    private fun clearAllFields() {
        binding.originalLinkEditText.text?.clear()
        binding.associateLinkEditText.text?.clear()
        binding.adTitleEditText.text?.clear()
        binding.exclusivityGroup.clearCheck()
        binding.exclusiveFieldEditText.text?.clear()
        binding.priceEditText.text?.clear()
        binding.fromPriceCheckbox.isChecked = false
        binding.couponEditText.text?.clear()
        binding.paymentScreenDiscountCheckbox.isChecked = false
        binding.shippingOptionsGroup.clearCheck()
        binding.freeShippingAboveEditText.text?.clear()
        binding.couponLinkCheckbox.isChecked = false
        binding.groupLinkCheckbox.isChecked = false
        initializePreview()
        currentVideoUri = null
    }
    
    private fun copyAdToClipboard() {
        AlertDialog.Builder(requireContext())
            .setTitle("Copiar Anúncio")
            .setMessage("Como deseja copiar o anúncio?")
            .setPositiveButton("Com Vídeo") { _, _ ->
                copyAdWithVideo()
            }
            .setNegativeButton("Sem Vídeo") { _, _ ->
                copyAdWithoutVideo()
            }
            .show()
    }

    private fun copyAdWithVideo() {
        if (currentVideoUri == null) {
            Toast.makeText(requireContext(), "Nenhum vídeo selecionado", Toast.LENGTH_SHORT).show()
            return
        }

        val adText = buildAdText()
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, currentVideoUri)
            putExtra(Intent.EXTRA_TEXT, adText)
        }
        
        startActivity(Intent.createChooser(shareIntent, "Compartilhar anúncio com vídeo"))
    }
    
    private fun buildAdText(): String {
        return buildString {
            val adTitle = binding.adTitleEditText.text.toString().trim()
            if (adTitle.isNotBlank()) {
                appendLine(adTitle)
                appendLine()
            }
            val exclusivityText = when (binding.exclusivityGroup.checkedRadioButtonId) {
                R.id.prime_radio -> "⭐ Exclusivo Membros Prime"
                R.id.meli_plus_radio -> "⭐ Exclusivo Membros Meli+"
                R.id.exclusive_radio -> {
                    val customExclusivity = binding.exclusiveFieldEditText.text.toString().trim()
                    if (customExclusivity.isNotEmpty()) "⭐ Exclusivo $customExclusivity" else "⭐ Exclusivo"
                }
                else -> ""
            }
            if (exclusivityText.isNotEmpty()) {
                appendLine(exclusivityText)
                appendLine()
            }
            val priceValue = binding.priceEditText.text.toString().trim()
            if (priceValue.isNotBlank()) {
                val pricePrefix = if (binding.fromPriceCheckbox.isChecked) "A partir de " else ""
                appendLine("💰 ${pricePrefix}R$ $priceValue")
                appendLine()
            }
            val coupon = binding.couponEditText.text.toString().trim()
            if (coupon.isNotBlank()) {
                appendLine("🎟️ Cupom: `$coupon`")
                if (binding.paymentScreenDiscountCheckbox.isChecked) {
                    appendLine("*(O desconto entra apenas na tela de pagamento)*")
                }
                appendLine()
            }
            val purchaseLink = binding.associateLinkEditText.text.toString().trim()
            if (purchaseLink.isNotBlank()) {
                appendLine("🛒 Link de Compra:")
                appendLine(purchaseLink)
                appendLine()
            }
            if (binding.couponLinkCheckbox.isChecked) {
                appendLine("🎟️ Link de Cupons:")
                appendLine(couponLink)
                appendLine()
            }
            if (binding.groupLinkCheckbox.isChecked) {
                appendLine("📱 Canal do Telegram:")
                appendLine(groupLink)
                appendLine()
            }
        }.trim()
    }

    private fun copyAdWithoutVideo() {
        val adText = buildAdText()

        if (adText.isEmpty()) {
            Toast.makeText(requireContext(), "Nada para copiar!", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Anúncio", adText)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), "Anúncio copiado sem vídeo!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
        client.close()
    }
}
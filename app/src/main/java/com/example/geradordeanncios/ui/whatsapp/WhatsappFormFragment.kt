package com.example.geradordeanncios.ui.whatsapp

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.geradordeanncios.R
import com.example.geradordeanncios.databinding.FragmentWhatsappFormBinding
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

class WhatsappFormFragment : Fragment() {

    private var _binding: FragmentWhatsappFormBinding? = null
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
    private var groupLink = "https://chat.whatsapp.com/LyGtLhQqxWbDqjiklHldOm"
    private var currentImageUrl: String? = null

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWhatsappFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.editCouponLinkButton.setOnClickListener {
            showEditLinkDialog("Editar Link de Cupons", couponLink) { newLink ->
                couponLink = newLink
                Toast.makeText(requireContext(), "Link de cupons atualizado!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editGroupLinkButton.setOnClickListener {
            showEditLinkDialog("Editar Link do Grupo", groupLink) { newLink ->
                groupLink = newLink
                Toast.makeText(requireContext(), "Link do grupo atualizado!", Toast.LENGTH_SHORT).show()
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
                    
                    if (product.imageUrl != null && product.imageUrl.isNotBlank()) {
                        Log.d("ShopeeAPI", "ImageURL encontrada: ${product.imageUrl}")
                        currentImageUrl = product.imageUrl
                        loadMediaPreview(product.imageUrl)
                    } else {
                        Log.w("ShopeeAPI", "ImageURL não disponível ou vazia")
                        currentImageUrl = null
                        binding.mediaPreviewWebview.visibility = View.GONE
                    }
                    
                    Toast.makeText(requireContext(), "✅ Produto encontrado via API!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("ShopeeAPI", "API retornou sucesso, mas sem dados do produto.")
                    binding.mediaPreviewWebview.visibility = View.GONE
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

    private fun loadMediaPreview(mediaUrl: String) {
        Log.d("MediaAnalysis", "Carregando preview: $mediaUrl")
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 0; background: #f5f5f5; height: 100vh; }
                    .media-container { 
                        width: 100%; height: 100vh; display: flex; 
                        align-items: center; justify-content: center;
                        padding: 8px; box-sizing: border-box;
                    }
                    img { 
                        max-width: 100%; max-height: 100%; object-fit: contain;
                        border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                    }
                </style>
            </head>
            <body>
                <div class="media-container">
                    <img src="$mediaUrl" onerror="this.src='data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjZGRkIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNCIgZmlsbD0iIzk5OSIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPkVycm8gYW8gY2FycmVnYXI8L3RleHQ+PC9zdmc+';">
                </div>
            </body>
            </html>
        """.trimIndent()
        
        binding.mediaPreviewWebview.apply {
            settings.javaScriptEnabled = true
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
        binding.mediaPreviewWebview.visibility = View.GONE
        currentImageUrl = null
    }
    
    private fun copyAdToClipboard() {
        AlertDialog.Builder(requireContext())
            .setTitle("Copiar Anúncio")
            .setMessage("Como deseja copiar o anúncio?")
            .setPositiveButton("Com Imagem") { _, _ ->
                copyAdWithImage()
            }
            .setNegativeButton("Sem Imagem") { _, _ ->
                copyAdWithoutImage()
            }
            .show()
    }

    private fun copyAdWithImage() {
        if (currentImageUrl == null) {
            Toast.makeText(requireContext(), "Nenhuma imagem disponível", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val imageBytes = client.get(currentImageUrl!!).body<ByteArray>()
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                
                val imageUri = MediaStore.Images.Media.insertImage(
                    requireContext().contentResolver,
                    bitmap,
                    "Produto_${System.currentTimeMillis()}",
                    "Imagem do produto"
                )
                
                val adText = buildAdText()
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUri))
                    putExtra(Intent.EXTRA_TEXT, adText)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Compartilhar anúncio com imagem"))
                
            } catch (e: Exception) {
                Log.e("CopyImage", "Erro ao processar imagem: ${e.message}")
                Toast.makeText(requireContext(), "Erro ao processar imagem. Copiando apenas texto.", Toast.LENGTH_SHORT).show()
                copyAdWithoutImage()
            }
        }
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
                appendLine("📱 Link do Grupo:")
                appendLine(groupLink)
                appendLine()
            }
        }.trim()
    }

    private fun copyAdWithoutImage() {
        val adText = buildAdText()

        if (adText.isEmpty()) {
            Toast.makeText(requireContext(), "Nada para copiar!", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Anúncio", adText)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), "Anúncio copiado sem imagem!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
        client.close()
    }
}
package com.example.geradordeanncios.ui.telegram

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.geradordeanncios.databinding.FragmentTelegramFormBinding
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

// --- Data Classes for Shopee API ---
@Serializable
data class ShopeeProductResponse(
    val data: ShopeeProductData?
)

@Serializable
data class ShopeeProductData(
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
    val priceMax: String? = null
)

@Serializable
data class GenerateShortLinkResponse(
    val data: GenerateShortLinkData?
)

@Serializable
data class GenerateShortLinkData(
    val generateShortLink: ShortLinkNode
)
@Serializable
data class ShortLinkNode(
    val shortLink: String
)



class TelegramFormFragment : Fragment() {

    private var _binding: FragmentTelegramFormBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isFetching = false

    private var couponLink = "https://s.shopee.com.br/1g76ck3c1x"
    private var groupLink = ""
    private var selectedVideoUri: Uri? = null

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedVideoUri = uri
                loadVideoPreview(uri)
            }
        }
    }

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
    }

    companion object {
        private const val APP_ID = "18344110677"
        private const val API_KEY = "BEWYLTPASZH2TJXVMQUQVGU3YBSYX64T"
        private const val API_URL = "https://open-api.affiliate.shopee.com.br/graphql"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTelegramFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mediaPreviewWebview.settings.javaScriptEnabled = true
        binding.mediaPreviewWebview.isClickable = false
        binding.mediaPreviewWebview.isFocusable = false
        initializePreview()
        setupListeners()
    }

    private fun initializePreview() {
        val html = """
            <!DOCTYPE html><html><head><style>
                * { margin: 0; padding: 0; }
                html, body { 
                    width: 100%; height: 100%; 
                    background: #f0f0f0; 
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-family: Arial, sans-serif;
                    cursor: pointer;
                }
                .add-video {
                    text-align: center;
                    color: #666;
                    padding: 20px;
                }
                .icon { font-size: 48px; margin-bottom: 10px; }
            </style></head><body>
                <div class="add-video">
                    <div class="icon">📹</div>
                    <p><strong>Adicionar Vídeo</strong></p>
                    <p>Toque para selecionar</p>
                </div>
            </body></html>
        """.trimIndent()
        binding.mediaPreviewWebview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        
        // Criar overlay transparente para capturar cliques apenas quando não há vídeo
        binding.mediaPreviewWebview.setOnTouchListener { _, event ->
            if (selectedVideoUri == null && event.action == android.view.MotionEvent.ACTION_DOWN) {
                Log.d("VideoSelection", "WebView tocado")
                selectVideo()
            }
            selectedVideoUri == null // Só consome o evento se não há vídeo
        }
    }

    private fun setupListeners() {
        binding.editCouponLinkButton.setOnClickListener {
            showEditLinkDialog("Editar Link de Cupons", couponLink) { newLink ->
                couponLink = newLink
                Toast.makeText(requireContext(), "Link de cupons atualizado!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editGroupLinkButton.setOnClickListener {
            showEditLinkDialog("Editar Link do Canal", groupLink) { newLink ->
                groupLink = newLink
                Toast.makeText(requireContext(), "Link do canal atualizado!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.clearButton.setOnClickListener { clearAllFields() }
        binding.copyAdButton.setOnClickListener { copyAdToClipboard() }
        setupUrlAutoFill()
    }
    
    private fun selectVideo() {
        Log.d("VideoSelection", "selectVideo() chamado")
        Toast.makeText(requireContext(), "Abrindo seletor de vídeo...", Toast.LENGTH_SHORT).show()
        
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        intent.type = "video/*"
        videoPickerLauncher.launch(intent)
    }

    private fun setupUrlAutoFill() {
        binding.telegramPostEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }
                val url = s.toString().trim()
                if (binding.telegramPostEditText.isFocused && !isFetching) {
                    when {
                        url.contains("t.me/") -> {
                            searchRunnable = Runnable { fetchTelegramPost(url) }
                            handler.postDelayed(searchRunnable!!, 800)
                        }
                        url.contains("shopee.com.br") -> {
                            searchRunnable = Runnable { generateAffiliateLink(url) }
                            handler.postDelayed(searchRunnable!!, 800)
                        }
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun fetchTelegramPost(url: String) {
        if (isFetching) return
        isFetching = true

        lifecycleScope.launch {
            try {
                Log.d("TelegramAPI", "Processando post do Telegram: $url")
                
                val response = client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                val html = response.bodyAsText()
                Log.d("TelegramAPI", "HTML recebido (primeiros 500 chars): ${html.take(500)}")
                
                val descRegex = Regex("<meta property=\"og:description\" content=\"([^\"]*)\">")
                val descMatch = descRegex.find(html)
                val description = descMatch?.groupValues?.get(1)?.let {
                    it.replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                } ?: ""
                
                val allLinksRegex = Regex("(https?://[^\\s]+)")
                val shopeeLink = allLinksRegex.findAll(description).map { it.value }.firstOrNull { it.contains("shopee.com.br") } ?: ""
                
                if (shopeeLink.isNotEmpty()) {
                    Log.d("TelegramAPI", "Link da Shopee encontrado: $shopeeLink")
                    Toast.makeText(requireContext(), "✅ Post processado! Gerando link de afiliado...", Toast.LENGTH_SHORT).show()
                    generateAffiliateLink(shopeeLink)
                } else {
                    Log.w("TelegramAPI", "Link da Shopee não encontrado na descrição: $description")
                    Toast.makeText(requireContext(), "❌ Link do Shopee não encontrado no post.", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e("TelegramAPI", "Erro ao processar post do Telegram: ${e.message}", e)
                Toast.makeText(requireContext(), "❌ Erro ao processar post do Telegram", Toast.LENGTH_SHORT).show()
            } finally {
                isFetching = false
            }
        }
    }

    private fun loadVideoPreview(videoUri: Uri) {
        Log.d("VideoPreview", "Carregando preview para: $videoUri")
        
        val html = """
            <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"><style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                video { width: 100%; height: 100%; object-fit: contain; display: block; }
                .controls { position: absolute; bottom: 10px; left: 50%; transform: translateX(-50%); z-index: 10; }
                .play-btn { background: rgba(255,255,255,0.8); border: none; border-radius: 50%; width: 50px; height: 50px; font-size: 20px; cursor: pointer; }
                .error { color: white; text-align: center; padding: 20px; font-family: Arial, sans-serif; }
            </style></head><body>
                <video id="video" muted preload="metadata">
                    <source src="$videoUri" type="video/mp4">
                    <source src="$videoUri">
                </video>
                <div class="controls">
                    <button class="play-btn" id="playBtn" onclick="togglePlay()">▶</button>
                </div>
                <div id="errorMsg" class="error" style="display:none;">
                    📹 Vídeo Selecionado<br>Preview não disponível
                </div>
                <script>
                    const video = document.getElementById('video');
                    const playBtn = document.getElementById('playBtn');
                    const errorMsg = document.getElementById('errorMsg');
                    
                    function togglePlay() {
                        if (video.paused) {
                            video.play().catch(e => console.log('Play error:', e));
                            playBtn.innerHTML = '⏸';
                        } else {
                            video.pause();
                            playBtn.innerHTML = '▶';
                        }
                    }
                    
                    video.addEventListener('loadeddata', function() {
                        console.log('Vídeo carregado com sucesso');
                    });
                    
                    video.addEventListener('error', function(e) {
                        console.log('Erro no vídeo:', e);
                        video.style.display = 'none';
                        document.querySelector('.controls').style.display = 'none';
                        errorMsg.style.display = 'block';
                    });
                    
                    video.addEventListener('ended', function() {
                        playBtn.innerHTML = '▶';
                    });
                    
                    video.muted = true;
                    video.volume = 0;
                    video.load();
                </script>
            </body></html>
        """.trimIndent()
        
        binding.mediaPreviewWebview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun generateAffiliateLink(originalUrl: String) {
        lifecycleScope.launch {
            try {
                Log.d("ShopeeAPI", "Gerando link de afiliado para: $originalUrl")

                val timestamp = System.currentTimeMillis() / 1000
                val mutation = "mutation{\n  generateShortLink(input:{originUrl:\"$originalUrl\"}){\n    shortLink\n  }\n}"
                val payload = buildJsonObject { put("query", mutation) }.toString()
                val signature = generateSignature(timestamp, payload)

                Log.d("ShopeeAPI", "Payload de afiliado: $payload")
                Log.d("ShopeeAPI", "Signature de afiliado: $signature")

                val response = client.post(API_URL) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "SHA256 Credential=$APP_ID, Timestamp=$timestamp, Signature=$signature")
                    setBody(payload)
                }

                val responseText = response.bodyAsText()
                Log.d("ShopeeAPI", "Resposta do link de afiliado: $responseText")

                if (responseText.contains("errors")) {
                    val errorMessage = responseText.substringAfter("message\":\"").substringBefore("\"")
                    throw Exception(errorMessage)
                }

                val linkResponse = Json { ignoreUnknownKeys = true }.decodeFromString<GenerateShortLinkResponse>(responseText)
                val shortLink = linkResponse.data?.generateShortLink?.shortLink

                if (shortLink != null) {
                    binding.associateLinkEditText.setText(shortLink)
                    Toast.makeText(requireContext(), "✅ Link de afiliado gerado!", Toast.LENGTH_SHORT).show()
                    // Buscar informações do produto após gerar o link
                    fetchProductDetails(originalUrl)
                } else {
                    throw Exception("shortLink nulo na resposta da API.")
                }

            } catch (e: Exception) {
                Log.e("ShopeeAPI", "Erro ao gerar link de afiliado: ${e.message}", e)
                Toast.makeText(requireContext(), "❌ Erro ao gerar link de afiliado.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun fetchProductDetails(url: String) {
        lifecycleScope.launch {
            try {
                var finalUrl = url
                if (url.contains("s.shopee.com.br")) {
                    val response = client.get(url)
                    finalUrl = response.request.url.toString()
                }

                val (shopId, itemId) = extractIds(finalUrl)
                if (shopId == null || itemId == null) return@launch

                val timestamp = System.currentTimeMillis() / 1000
                val query = "{ productOfferV2(shopId: $shopId, itemId: $itemId) { nodes { productName price priceMin priceMax } }}"
                val payload = buildJsonObject { put("query", query) }.toString()
                val signature = generateSignature(timestamp, payload)

                val response = client.post(API_URL) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "SHA256 Credential=$APP_ID, Timestamp=$timestamp, Signature=$signature")
                    setBody(payload)
                }

                val responseText = response.bodyAsText()
                if (responseText.contains("errors")) return@launch

                val shopeeResponse = Json { ignoreUnknownKeys = true }.decodeFromString<ShopeeProductResponse>(responseText)
                val product = shopeeResponse.data?.productOffer?.nodes?.firstOrNull()

                if (product != null) {
                    binding.adTitleEditText.setText(product.productName)
                    
                    val priceValue = product.priceMin ?: product.price
                    if (priceValue != null) {
                        binding.priceEditText.setText(formatPrice(priceValue))
                    }
                    
                    val priceMin = product.priceMin?.toDoubleOrNull()
                    val priceMax = product.priceMax?.toDoubleOrNull()
                    
                    if (priceMin != null && priceMax != null && priceMin < priceMax) {
                        binding.fromPriceCheckbox.isChecked = true
                    }
                    
                    Toast.makeText(requireContext(), "✅ Informações do produto carregadas!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ProductAPI", "Erro ao buscar produto: ${e.message}")
            }
        }
    }

    private fun extractIds(url: String): Pair<String?, String?> {
        Regex(".*?/(\\d+)/(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                return Pair(it.groupValues[1], it.groupValues[2])
            }
        }
        Regex(".*i\\.(\\d+)\\.(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                return Pair(it.groupValues[1], it.groupValues[2])
            }
        }
        Regex(".*item_id=(\\d+).*shop_id=(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                return Pair(it.groupValues[2], it.groupValues[1])
            }
        }
        return Pair(null, null)
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

    private fun generateSignature(timestamp: Long, payload: String): String {
        val factor = "$APP_ID$timestamp$payload$API_KEY"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(factor.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
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
        binding.telegramPostEditText.text?.clear()
        binding.associateLinkEditText.text?.clear()
        binding.adTitleEditText.text?.clear()
        binding.priceEditText.text?.clear()
        binding.couponEditText.text?.clear()
        binding.exclusivityGroup.clearCheck()
        binding.exclusiveFieldEditText.text?.clear()
        binding.fromPriceCheckbox.isChecked = false
        binding.paymentScreenDiscountCheckbox.isChecked = false
        binding.shippingOptionsGroup.clearCheck()
        binding.freeShippingAboveEditText.text?.clear()
        binding.couponLinkCheckbox.isChecked = false
        binding.groupLinkCheckbox.isChecked = false
        selectedVideoUri = null
        initializePreview()
        Toast.makeText(requireContext(), "Campos limpos!", Toast.LENGTH_SHORT).show()
    }
    
    private fun copyAdToClipboard() {
        val title = binding.adTitleEditText.text.toString().trim()
        val price = binding.priceEditText.text.toString().trim()
        val coupon = binding.couponEditText.text.toString().trim()
        val affiliateLink = binding.associateLinkEditText.text.toString().trim()
        
        if (title.isEmpty() || affiliateLink.isEmpty()) {
            Toast.makeText(requireContext(), "Preencha pelo menos o título e o link de afiliado", Toast.LENGTH_SHORT).show()
            return
        }
        
        val exclusivity = when {
            binding.primeRadio.isChecked -> "Prime"
            binding.meliPlusRadio.isChecked -> "Meli+"
            binding.exclusiveRadio.isChecked -> {
                val customExclusive = binding.exclusiveFieldEditText.text.toString().trim()
                if (customExclusive.isNotEmpty()) "Exclusivo para $customExclusive" else "Exclusivo"
            }
            else -> ""
        }
        
        val priceText = if (price.isNotEmpty()) {
            val prefix = if (binding.fromPriceCheckbox.isChecked) "A partir de " else ""
            "${prefix}R$ $price"
        } else ""
        
        val couponText = if (coupon.isNotEmpty()) {
            val suffix = if (binding.paymentScreenDiscountCheckbox.isChecked) " (desconto na tela de pagamento)" else ""
            "Cupom: $coupon$suffix"
        } else ""
        
        val shippingText = when {
            binding.freeShippingRadio.isChecked -> "Frete Grátis"
            binding.couponShippingRadio.isChecked -> "Frete Grátis com Cupom"
            binding.freeShippingAboveRadio.isChecked -> {
                val minValue = binding.freeShippingAboveEditText.text.toString().trim()
                if (minValue.isNotEmpty()) "Frete Grátis acima de R$ $minValue" else "Frete Grátis"
            }
            else -> ""
        }
        
        val additionalLinks = mutableListOf<String>()
        if (binding.couponLinkCheckbox.isChecked && couponLink.isNotEmpty()) {
            additionalLinks.add("🎟️ Cupons: $couponLink")
        }
        if (binding.groupLinkCheckbox.isChecked && groupLink.isNotEmpty()) {
            additionalLinks.add("📢 Canal: $groupLink")
        }
        
        val ad = buildString {
            appendLine(title)
            if (exclusivity.isNotEmpty()) appendLine("🔥 $exclusivity")
            if (priceText.isNotEmpty()) appendLine("💰 $priceText")
            if (couponText.isNotEmpty()) appendLine("🎫 $couponText")
            if (shippingText.isNotEmpty()) appendLine("🚚 $shippingText")
            appendLine()
            appendLine("🛒 $affiliateLink")
            if (additionalLinks.isNotEmpty()) {
                appendLine()
                additionalLinks.forEach { appendLine(it) }
            }
        }.trim()
        
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Anúncio Telegram", ad)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(requireContext(), "Anúncio copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { handler.removeCallbacks(it) }
        client.close()
        _binding = null
    }
}
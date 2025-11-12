package com.example.geradordeanncios.ui.mercadolivre2

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.geradordeanncios.R
import com.example.geradordeanncios.databinding.FragmentMercadolivre2Binding
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

@Serializable
data class MercadoLivreProduct(
    val id: String,
    val title: String,
    val price: Double,
    val currency_id: String,
    val condition: String,
    val pictures: List<MercadoLivrePicture>? = null,
    val shipping: MercadoLivreShipping? = null
)

@Serializable
data class MercadoLivrePicture(
    val secure_url: String
)

@Serializable
data class MercadoLivreShipping(
    val free_shipping: Boolean
)

class MercadoLivre2Fragment : Fragment() {

    private var _binding: FragmentMercadolivre2Binding? = null
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

    private var couponLink = ""
    private var groupLink = ""
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMercadolivre2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        initializePreview()
    }
    
    private fun initializePreview() {
        binding.mediaPreviewWebview.apply {
            settings.javaScriptEnabled = true
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
                    }
                </style>
            </head>
            <body></body>
            </html>
        """.trimIndent()
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
                if (url.contains("mercadolivre.com") && binding.associateLinkEditText.isFocused && !isFetching) {
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
                // Resolver shortlink para obter URL final
                var finalUrl = url
                Log.d("MercadoLivreAPI", "URL inicial: $url")
                
                if (url.contains("mercadolivre.com/sec/") || url.contains("mercadolivre.com/social/")) {
                    try {
                        val response: HttpResponse = client.get(url) {
                            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        }
                        finalUrl = response.request.url.toString()
                        Log.d("MercadoLivreAPI", "Link resolvido: $finalUrl")
                        
                        // Se chegou na página do afiliado, extrair o link do produto do HTML
                        if (finalUrl.contains("/social/")) {
                            val html = response.bodyAsText()
                            val productUrl = extractProductUrlFromAffiliatePage(html)
                            if (productUrl != null) {
                                finalUrl = productUrl
                                Log.d("MercadoLivreAPI", "Link do produto extraído: $finalUrl")
                            }
                        }
                        
                        // Tentar também o header Location se houver redirect
                        val locationHeader = response.headers["Location"]
                        if (locationHeader != null) {
                            Log.d("MercadoLivreAPI", "Location header: $locationHeader")
                            finalUrl = locationHeader
                        }
                    } catch (e: Exception) {
                        Log.e("MercadoLivreAPI", "Erro ao resolver shortlink: ${e.message}")
                        finalUrl = url  // Usar URL original se falhar
                    }
                }

                // Extrair ID do produto
                Log.d("MercadoLivreAPI", "Tentando extrair ID de: $finalUrl")
                val productId = extractProductId(finalUrl)
                if (productId == null) {
                    Log.w("MercadoLivreAPI", "ID do produto não encontrado na URL: $finalUrl")
                    Toast.makeText(requireContext(), "❌ ID do produto não encontrado no link. URL: $finalUrl", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                Log.d("MercadoLivreAPI", "ID do produto encontrado: $productId")

                Log.d("MercadoLivreAPI", "Buscando produto: $productId")

                // Tentar API pública do Mercado Livre
                var product: MercadoLivreProduct? = null
                try {
                    val response = client.get("https://api.mercadolibre.com/items/$productId") {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    }
                    val responseText = response.bodyAsText()
                    Log.d("MercadoLivreAPI", "Resposta API: $responseText")
                    
                    if (response.status.isSuccess()) {
                        product = Json { ignoreUnknownKeys = true }.decodeFromString<MercadoLivreProduct>(responseText)
                    } else {
                        Log.w("MercadoLivreAPI", "API retornou erro: ${response.status}")
                    }
                } catch (e: Exception) {
                    Log.e("MercadoLivreAPI", "Erro na API, tentando web scraping: ${e.message}")
                }
                
                // Se API falhou, tentar web scraping
                if (product == null) {
                    product = tryWebScraping(finalUrl)
                }
                
                // Se ainda não encontrou e é página de afiliado, tentar extrair da página atual
                if (product == null && finalUrl.contains("/social/")) {
                    product = tryWebScrapingFromAffiliatePage(finalUrl)
                }
                
                if (product == null) {
                    Toast.makeText(requireContext(), "❌ Não foi possível obter informações do produto.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Preencher campos automaticamente
                binding.adTitleEditText.setText(product.title)
                binding.priceEditText.setText(formatPrice(product.price.toString()))
                
                // Configurar frete grátis se disponível
                if (product.shipping?.free_shipping == true) {
                    binding.freeShippingRadio.isChecked = true
                }

                // Carregar imagem se disponível
                if (!product.pictures.isNullOrEmpty()) {
                    val rawImageUrl = product.pictures[0].secure_url
                    // Decodificar URL removendo caracteres de escape
                    val imageUrl = rawImageUrl.replace("\\u002F", "/")
                                              .replace("%2F", "/")
                                              .replace("&amp;", "&")
                    Log.d("MercadoLivreAPI", "ImageURL original: $rawImageUrl")
                    Log.d("MercadoLivreAPI", "ImageURL decodificada: $imageUrl")
                    currentImageUrl = imageUrl
                    loadMediaPreview(imageUrl)
                } else {
                    Log.w("MercadoLivreAPI", "Nenhuma imagem disponível")
                    currentImageUrl = null
                    initializePreview()
                }

                Toast.makeText(requireContext(), "✅ Produto encontrado!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("MercadoLivreAPI", "Erro ao buscar produto: ${e.message}", e)
                Toast.makeText(requireContext(), "❌ Erro ao buscar produto.", Toast.LENGTH_SHORT).show()
            } finally {
                isFetching = false
            }
        }
    }

    private fun extractProductUrlFromAffiliatePage(html: String): String? {
        Log.d("MercadoLivreAPI", "Extraindo link do produto da página do afiliado")
        
        // Padrões para encontrar o link do produto na página do afiliado
        val patterns = listOf(
            Regex("href=\"([^\"]*mercadolivre\\.com\\.br/[^\"]*p/MLB\\d+[^\"]*)\""),  // Link com /p/MLB
            Regex("href=\"([^\"]*produto\\.mercadolivre\\.com\\.br/MLB-[^\"]*)\""),  // Link produto.mercadolivre
            Regex("\"url\":\"([^\"]*mercadolivre\\.com\\.br/[^\"]*p/MLB\\d+[^\"]*)\""),  // JSON url
            Regex("data-href=\"([^\"]*mercadolivre\\.com\\.br/[^\"]*p/MLB\\d+[^\"]*)\""),  // data-href
            Regex("onclick=\"[^\"]*location\\.href='([^']*mercadolivre\\.com\\.br/[^']*p/MLB\\d+[^']*)'\"")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                var productUrl = match.groupValues[1]
                // Decodificar URL se necessário
                productUrl = productUrl.replace("\\u002F", "/")
                                       .replace("%2F", "/")
                                       .replace("&amp;", "&")
                
                Log.d("MercadoLivreAPI", "Link do produto encontrado: $productUrl")
                return productUrl
            }
        }
        
        // Tentar encontrar botão "Ir para produto" ou similar
        val buttonPatterns = listOf(
            Regex("Ir para produto[^>]*href=\"([^\"]*)\""),
            Regex("Ver produto[^>]*href=\"([^\"]*)\""),
            Regex("button[^>]*data-url=\"([^\"]*mercadolivre[^\"]*)\""),
            Regex("class=\"[^\"]*product[^\"]*\"[^>]*href=\"([^\"]*mercadolivre[^\"]*)\""),
            Regex("onclick=\"[^\"]*window\\.open\\('([^']*mercadolivre[^']*)'\\)\"")
        )
        
        for (pattern in buttonPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                var productUrl = match.groupValues[1]
                productUrl = productUrl.replace("\\u002F", "/")
                                       .replace("%2F", "/")
                                       .replace("&amp;", "&")
                
                Log.d("MercadoLivreAPI", "Link do produto encontrado via botão: $productUrl")
                return productUrl
            }
        }
        
        Log.w("MercadoLivreAPI", "Nenhum link de produto encontrado na página do afiliado")
        return null
    }

    private fun extractProductId(url: String): String? {
        Log.d("MercadoLivreAPI", "Extraindo ID da URL: $url")
        
        // Padrões baseados nos exemplos reais
        val patterns = listOf(
            Regex("/p/(MLB\\d+)"),  // /p/MLB37926038
            Regex("MLB-(\\d+)"),    // MLB-5399250468
            Regex("(MLB\\d+)"),     // MLB54019361 direto
            Regex("item_id=(MLB\\d+)"), // item_id=MLB123456
            Regex("wid=(MLB\\d+)"),    // wid=MLB3767501873
            Regex("/([A-Z]{3}\\d+)-"), // /MLB-123456-titulo
            Regex("([A-Z]{3}-\\d+)")   // Formato com hífen
        )
        
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                val id = if (match.groupValues.size > 1) {
                    match.groupValues[1]  // Grupo capturado
                } else {
                    match.value  // Match completo
                }
                Log.d("MercadoLivreAPI", "ID encontrado: $id usando padrão: ${pattern.pattern}")
                return id
            }
        }
        
        Log.w("MercadoLivreAPI", "Nenhum ID encontrado na URL")
        return null
    }

    private fun loadMediaPreview(mediaUrl: String) {
        Log.d("MediaAnalysis", "Carregando preview: $mediaUrl")
        
        val html = """
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
                    }
                    .media-container { 
                        width: 100%; height: 100%; 
                        display: flex; align-items: center; justify-content: center;
                        background: white;
                    }
                    img { 
                        max-width: 100%; max-height: 100%; 
                        object-fit: contain;
                        display: block;
                    }
                    .error-msg {
                        display: none;
                        color: #666;
                        text-align: center;
                        font-family: Arial, sans-serif;
                    }
                </style>
                <script>
                    function imageError() {
                        document.getElementById('image').style.display = 'none';
                        document.getElementById('error').style.display = 'block';
                        console.log('Erro ao carregar imagem: $mediaUrl');
                    }
                    function imageLoaded() {
                        console.log('Imagem carregada com sucesso: $mediaUrl');
                    }
                </script>
            </head>
            <body>
                <div class="media-container">
                    <img id="image" src="$mediaUrl" onload="imageLoaded()" onerror="imageError()">
                    <div id="error" class="error-msg">Erro ao carregar imagem</div>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        binding.mediaPreviewWebview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            visibility = View.VISIBLE
        }
    }

    private suspend fun tryWebScrapingFromAffiliatePage(url: String): MercadoLivreProduct? {
        return try {
            Log.d("MercadoLivreAPI", "Tentando web scraping da página do afiliado: $url")
            val response = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            }
            val html = response.bodyAsText()
            
            // Na página do afiliado, o produto aparece como um card
            // Extrair título do produto
            val titlePatterns = listOf(
                Regex("<h[1-6][^>]*>([^<]*Pantene[^<]*)</h[1-6]>"),  // Título em heading
                Regex("class=\"[^\"]*title[^\"]*\">([^<]*Pantene[^<]*)<"),  // Classe title
                Regex("alt=\"([^\"]*Pantene[^\"]*)\""),  // Alt da imagem
                Regex("\"name\":\"([^\"]*Pantene[^\"]*)\""),  // JSON name
                Regex("data-title=\"([^\"]*Pantene[^\"]*)\"")  // data-title
            )
            
            var title = "Produto"
            for (pattern in titlePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    title = match.groupValues[1].trim()
                    break
                }
            }
            
            // Extrair preço
            val pricePatterns = listOf(
                Regex("R\\$\\s*([\\d,]+)(?:\\.[\\d]{2})?"),  // R$ 30,91
                Regex("\"price\":\\s*([\\d,]+(?:\\.[\\d]{2})?)"),  // JSON price
                Regex("class=\"[^\"]*price[^\"]*\">.*?([\\d,]+)"),  // Classe price
                Regex("data-price=\"([\\d,]+(?:\\.[\\d]{2})?)\"")  // data-price
            )
            
            var price = 0.0
            for (pattern in pricePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val priceStr = match.groupValues[1].replace(",", ".")
                    price = priceStr.toDoubleOrNull() ?: 0.0
                    if (price > 0) break
                }
            }
            
            // Extrair imagem do produto em alta resolução da página do afiliado
            val imagePatterns = listOf(
                Regex("data-zoom=\"([^\"]*D_NQ_NP[^\"]*)\""),  // Imagem de zoom
                Regex("\"image\":\"([^\"]*D_NQ_NP[^\"]*-F[^\"]*)\""),  // JSON image full size
                Regex("src=\"([^\"]*D_NQ_NP[^\"]*-F[^\"]*)\""),  // Src full size
                Regex("\"image\":\"([^\"]*D_Q_NP[^\"]*)\""),  // JSON image fallback
                Regex("src=\"([^\"]*D_Q_NP[^\"]*)\"")  // Src fallback
            )
            
            var imageUrl: String? = null
            for (pattern in imagePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val candidateUrl = match.groupValues[1]
                    // Filtrar banners e imagens irrelevantes
                    if (candidateUrl.contains("http") && 
                        !candidateUrl.contains("banner") && 
                        !candidateUrl.contains("mshops-appearance") &&
                        !candidateUrl.contains("ui-ms-profile")) {
                        imageUrl = convertToHighResolution(candidateUrl)
                        break
                    }
                }
            }
            
            val pictures = if (imageUrl != null) {
                // Decodificar URL da imagem
                val decodedImageUrl = imageUrl.replace("\\u002F", "/")
                                              .replace("%2F", "/")
                                              .replace("&amp;", "&")
                listOf(MercadoLivrePicture(decodedImageUrl))
            } else null
            
            Log.d("MercadoLivreAPI", "Web scraping afiliado - Título: $title, Preço: $price, Imagem: $imageUrl")
            
            if (title != "Produto" || price > 0) {
                MercadoLivreProduct(
                    id = "affiliate-scraped",
                    title = title,
                    price = price,
                    currency_id = "BRL",
                    condition = "new",
                    pictures = pictures,
                    shipping = null
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MercadoLivreAPI", "Erro no web scraping da página do afiliado: ${e.message}")
            null
        }
    }

    private suspend fun tryWebScraping(url: String): MercadoLivreProduct? {
        return try {
            Log.d("MercadoLivreAPI", "Tentando web scraping: $url")
            val response = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            }
            val html = response.bodyAsText()
            
            // Extrair título - múltiplos padrões
            val titlePatterns = listOf(
                Regex("<title>([^|]+)"),  // Título antes do |
                Regex("\"name\":\"([^\"]+)\""),  // JSON name
                Regex("data-testid=\"product-title\">([^<]+)<"),  // Elemento do título
                Regex("class=\"ui-pdp-title\">([^<]+)<")  // Classe do título
            )
            
            var title = "Produto"
            for (pattern in titlePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    title = match.groupValues[1].trim()
                        .replace(" | Mercado Livre", "")
                        .replace(" - Mercado Livre", "")
                        .trim()
                    break
                }
            }
            
            // Extrair preço - múltiplos padrões
            val pricePatterns = listOf(
                Regex("\"price\":(\\d+(?:\\.\\d+)?)"),  // JSON price
                Regex("\"amount\":(\\d+(?:\\.\\d+)?)"),  // JSON amount
                Regex("data-testid=\"price\">.*?([\\d,]+)"),  // Elemento preço
                Regex("class=\"price-tag-fraction\">([\\d,]+)<"),  // Classe preço
                Regex("R\\$\\s*([\\d,]+(?:\\.[\\d]{2})?)")  // Formato R$ 123,45
            )
            
            var price = 0.0
            for (pattern in pricePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val priceStr = match.groupValues[1].replace(",", ".")
                    price = priceStr.toDoubleOrNull() ?: 0.0
                    if (price > 0) break
                }
            }
            
            // Extrair imagem do produto em alta resolução
            val imagePatterns = listOf(
                Regex("\"secure_url\":\"([^\"]*D_NQ_NP[^\"]*-F[^\"]*)\""),  // Imagem full size (-F)
                Regex("\"secure_url\":\"([^\"]*D_Q_NP[^\"]*-O[^\"]*)\""),  // Imagem original (-O)
                Regex("data-zoom=\"([^\"]*D_NQ_NP[^\"]*)\""),  // Imagem de zoom
                Regex("\"url\":\"([^\"]*D_NQ_NP[^\"]*-F[^\"]*)\""),  // URL full size
                Regex("src=\"([^\"]*D_NQ_NP[^\"]*-F[^\"]*)\""),  // Src full size
                Regex("\"secure_url\":\"([^\"]*D_Q_NP[^\"]*)\"")  // Fallback
            )
            
            var imageUrl: String? = null
            for (pattern in imagePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val candidateUrl = match.groupValues[1]
                    // Filtrar banners e imagens irrelevantes
                    if (candidateUrl.contains("http") && 
                        !candidateUrl.contains("banner") && 
                        !candidateUrl.contains("mshops-appearance") &&
                        !candidateUrl.contains("ui-ms-profile")) {
                        imageUrl = convertToHighResolution(candidateUrl)
                        break
                    }
                }
            }
            
            val pictures = if (imageUrl != null) {
                // Decodificar URL da imagem
                val decodedImageUrl = imageUrl.replace("\\u002F", "/")
                                              .replace("%2F", "/")
                                              .replace("&amp;", "&")
                listOf(MercadoLivrePicture(decodedImageUrl))
            } else null
            
            Log.d("MercadoLivreAPI", "Web scraping - Título: $title, Preço: $price, Imagem: $imageUrl")
            
            MercadoLivreProduct(
                id = "scraped",
                title = title,
                price = price,
                currency_id = "BRL",
                condition = "new",
                pictures = pictures,
                shipping = null
            )
        } catch (e: Exception) {
            Log.e("MercadoLivreAPI", "Erro no web scraping: ${e.message}")
            null
        }
    }

    private fun convertToHighResolution(imageUrl: String): String {
        return imageUrl.replace("-R.webp", "-F.webp")
                      .replace("-G.webp", "-F.webp")
                      .replace("-S.webp", "-F.webp")
                      .replace("-T.webp", "-F.webp")
                      .replace("-R.jpg", "-F.jpg")
                      .replace("-G.jpg", "-F.jpg")
                      .replace("-S.jpg", "-F.jpg")
                      .replace("-T.jpg", "-F.jpg")
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
        initializePreview()
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
                val imageBytes = client.get(currentImageUrl!!) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                }.body<ByteArray>()
                val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val croppedBitmap = removeWhiteBorders(originalBitmap)
                
                val imageUri = MediaStore.Images.Media.insertImage(
                    requireContext().contentResolver,
                    croppedBitmap,
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
    
    private fun removeWhiteBorders(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        var top = 0
        var bottom = height - 1
        var left = 0
        var right = width - 1
        
        // Encontrar borda superior
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (!isWhitePixel(bitmap.getPixel(x, y))) {
                    top = y
                    break@outer
                }
            }
        }
        
        // Encontrar borda inferior
        outer@ for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                if (!isWhitePixel(bitmap.getPixel(x, y))) {
                    bottom = y
                    break@outer
                }
            }
        }
        
        // Encontrar borda esquerda
        outer@ for (x in 0 until width) {
            for (y in top..bottom) {
                if (!isWhitePixel(bitmap.getPixel(x, y))) {
                    left = x
                    break@outer
                }
            }
        }
        
        // Encontrar borda direita
        outer@ for (x in width - 1 downTo 0) {
            for (y in top..bottom) {
                if (!isWhitePixel(bitmap.getPixel(x, y))) {
                    right = x
                    break@outer
                }
            }
        }
        
        val croppedWidth = right - left + 1
        val croppedHeight = bottom - top + 1
        
        return if (croppedWidth > 0 && croppedHeight > 0) {
            Bitmap.createBitmap(bitmap, left, top, croppedWidth, croppedHeight)
        } else {
            bitmap
        }
    }
    
    private fun isWhitePixel(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        return red > 240 && green > 240 && blue > 240
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
                appendLine("Cupons: $couponLink")
                appendLine("Os cupons de frete grátis aparecem na tela de pagamento")
                appendLine()
            }
            if (binding.groupLinkCheckbox.isChecked) {
                appendLine("Indique o grupo para amigos: $groupLink")
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

package com.example.geradordeanncios.ui.whatsapp

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import java.security.MessageDigest
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

    private var couponLink = "https://s.shopee.com.br/1g76ck3c1x"
    private var groupLink = "https://chat.whatsapp.com/LyGtLhQqxWbDqjiklHldOm"

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
        // Credentials
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

                // GraphQL query para buscar produto específico usando shopId e itemId
                val query = "{ productOfferV2(shopId: $shopId, itemId: $itemId) { nodes { commissionRate commission imageUrl price productLink offerLink productName } }}"

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
                    
                    // Usar price ou priceMin, o que estiver disponível
                    val priceValue = product.price ?: product.priceMin
                    if (priceValue != null) {
                        binding.priceEditText.setText(formatPrice(priceValue))
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

    private fun extractIds(url: String): Pair<String?, String?> {
        // Corrected Regex to handle paths like /opaanlp/shopId/itemId
        Regex(".*?/(\\d+)/(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                val shopId = it.groupValues[1]
                val itemId = it.groupValues[2]
                return Pair(shopId, itemId)
            }
        }
        // Handles formats like i.shopId.itemId
        Regex(".*i\\.(\\d+)\\.(\\d+)").find(url)?.let {
            if (it.groupValues.size == 3) {
                val shopId = it.groupValues[1]
                val itemId = it.groupValues[2]
                return Pair(shopId, itemId)
            }
        }
        // Handles formats like ?item_id=...&shop_id=...
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
        // Conforme documentação: AppId + Timestamp + Payload + Secret
        val factor = "$APP_ID$timestamp$payload$SECRET"
        
        // Usar SHA256 simples (não HMAC)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(factor.toByteArray(Charsets.UTF_8))
        
        // Converter para hexadecimal
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun formatPrice(price: String): String {
        return try {
            // A API da Shopee retorna o preço em formato decimal com ponto (ex: "679.42")
            // Converter para formato brasileiro com vírgula (ex: "679,42")
            val priceValue = price.toDoubleOrNull()
            if (priceValue != null) {
                // Formatar com 2 casas decimais e vírgula
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
    }
    private fun copyAdToClipboard() {
         val adText = buildString { 
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

        if (adText.isEmpty()) {
            Toast.makeText(requireContext(), "Nada para copiar!", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Anúncio", adText)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), "Anúncio copiado!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
        client.close()
    }
}

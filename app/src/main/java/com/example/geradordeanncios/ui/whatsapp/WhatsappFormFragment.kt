package com.example.geradordeanncios.ui.whatsapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.geradordeanncios.R
import com.example.geradordeanncios.databinding.FragmentWhatsappFormBinding
import org.json.JSONObject
import org.jsoup.Jsoup

class WhatsappFormFragment : Fragment() {

    private var _binding: FragmentWhatsappFormBinding? = null
    private val binding get() = _binding!!

    private var couponLink = "https://s.shopee.com.br/1g76ck3c1x"
    private var groupLink = "https://chat.whatsapp.com/LyGtLhQqxWbDqjiklHldOm"
    private val handler = Handler(Looper.getMainLooper())
    private var extractionRunnable: Runnable? = null

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
        // setupUrlAutoFill()

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

        binding.clearButton.setOnClickListener {
            clearAllFields()
        }

        binding.copyAdButton.setOnClickListener {
            copyAdToClipboard()
        }
    }

    private fun setupUrlAutoFill() {
        binding.associateLinkEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val url = s.toString().trim()
                // A verificação de foco é crucial para evitar loops infinitos!
                if (url.startsWith("http") && binding.associateLinkEditText.isFocused) {
                    fetchProductDetailsWithWebView(url)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchProductDetailsWithWebView(url: String) {
        val TAG = "ScrapingDebug"
        Log.d(TAG, "Iniciando busca com WebView para: $url")

        extractionRunnable?.let { handler.removeCallbacks(it) }

        val webView = binding.scrapingWebview

        // --- Configurações de Camuflagem Avançada ---
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
        webView.settings.blockNetworkImage = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)

        // Essencial: Bloqueia pop-ups e novas janelas
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                Log.w(TAG, "Pedido de nova janela (popup) bloqueado.")
                // De forma crucial, não fazemos nada com a mensagem, efetivamente bloqueando o pop-up.
                return false // Impede a criação de novas janelas
            }
        }

        extractionRunnable = Runnable {
            Log.d(TAG, "Tempo de espera finalizado. Tentando extrair HTML agora.")
            webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
                if (html != null && html.length > 2) { // Verifica se o html não é nulo ou \"null\"
                    val decodedHtml = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    parseHtmlWithJsoup(decodedHtml)
                } else {
                    Log.e(TAG, "Falha ao obter HTML do WebView, resultado nulo ou vazio.")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Falha ao obter dados da página.", Toast.LENGTH_SHORT).show()
                    }
                }
                webView.stopLoading()
            }
        }

        webView.loadUrl(url)
        Log.d(TAG, "URL carregada no WebView. Aguardando 10 segundos...")
        handler.postDelayed(extractionRunnable!!, 10000)
    }

    private fun parseHtmlWithJsoup(html: String) {
        val TAG = "ScrapingDebug"
        Log.d(TAG, "Iniciando parse do HTML com Jsoup.")
        var title = ""
        var price = ""
        try {
            val doc = Jsoup.parse(html)

            val scriptElements = doc.select("script[type=application/ld+json]")
            Log.d(TAG, "Encontrados ${scriptElements.size} elementos de script JSON-LD.")
            for (element in scriptElements) {
                val json = element.data()
                if (json.contains("\"@type\":\"Product\"")) {
                    val jsonObj = JSONObject(json)
                    title = jsonObj.optString("name", "")
                    val offers = jsonObj.optJSONObject("offers")
                    price = offers?.optString("price") ?: offers?.optJSONArray("offers")?.optJSONObject(0)?.optString("price") ?: ""
                    if (title.isNotBlank()) {
                        Log.d(TAG, "Título e preço encontrados no JSON-LD.")
                        break
                    }
                }
            }

            if (title.isBlank()) {
                 Log.d(TAG, "Título não encontrado no JSON-LD, tentando meta tags.")
                title = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: ""
            }
            if (price.isBlank()) {
                 Log.d(TAG, "Preço não encontrado no JSON-LD, tentando meta tags.")
                price = doc.selectFirst("meta[property=product:price:amount]")?.attr("content") ?: doc.selectFirst("meta[property=og:price:amount]")?.attr("content") ?: ""
            }

            val cleanedPrice = price.replace(Regex("[^0-9,.]"), "")

            activity?.runOnUiThread {
                Log.d(TAG, "Atualizando UI com Título='$title', Preço='$cleanedPrice'")
                if (title.isNotBlank() || cleanedPrice.isNotBlank()) {
                    binding.adTitleEditText.setText(title)
                    binding.priceEditText.setText(cleanedPrice)
                } else {
                    Log.w(TAG, "Nenhum título ou preço encontrado no HTML final.")
                    Toast.makeText(context, "Não foi possível extrair dados do link.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fazer parse do HTML com Jsoup", e)
        }
    }

    private fun showEditLinkDialog(title: String, currentLink: String, onSave: (String) -> Unit) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)

        val input = EditText(requireContext())
        input.setText(currentLink)
        builder.setView(input)

        builder.setPositiveButton("Salvar") { dialog, _ ->
            val newLink = input.text.toString()
            onSave(newLink)
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
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
        Toast.makeText(requireContext(), "Campos limpos!", Toast.LENGTH_SHORT).show()
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
            val shippingText = when (binding.shippingOptionsGroup.checkedRadioButtonId) {
                R.id.free_shipping_radio -> "🚚 Frete Grátis!"
                R.id.coupon_shipping_radio -> "🚚 Frete Grátis com Cupom!"
                R.id.free_shipping_above_radio -> {
                    val amount = binding.freeShippingAboveEditText.text.toString().trim()
                    if (amount.isNotEmpty()) "🚚 Frete Grátis acima de R$ $amount" else ""
                }
                else -> ""
            }
            if (shippingText.isNotEmpty()) {
                appendLine(shippingText)
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
        extractionRunnable?.let { handler.removeCallbacks(it) }
        _binding = null // O webview agora é parte do binding, seu ciclo de vida é gerenciado com o fragmento
    }
}
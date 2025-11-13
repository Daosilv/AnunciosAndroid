package com.example.geradordeanncios.ui.mercadolivre2

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.geradordeanncios.R
import com.example.geradordeanncios.databinding.FragmentMercadolivre2Binding
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider

class MercadoLivre2Fragment : Fragment() {

    private var _binding: FragmentMercadolivre2Binding? = null
    private val binding get() = _binding!!

    private var lastCheckedExclusivityId: Int = -1
    private var lastCheckedShippingId: Int = -1
    private var lastCheckedFromPriceId: Int = -1
    private var lastCheckedGroupLinkId: Int = -1
    private var lastCheckedPaymentDiscountId: Int = -1

    private var groupLink = ""
    private var currentImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            currentImageUri = it
            loadImagePreview(it)
            Toast.makeText(requireContext(), "Imagem selecionada!", Toast.LENGTH_SHORT).show()
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            when {
                // Imagem retornada como bitmap (return-data = true)
                data?.extras?.get("data") != null -> {
                    val bitmap = data.extras?.get("data") as? android.graphics.Bitmap
                    bitmap?.let {
                        val uri = saveBitmapToCache(it)
                        currentImageUri = uri
                        loadImagePreview(uri)
                        Toast.makeText(requireContext(), "Imagem recortada!", Toast.LENGTH_SHORT).show()
                    }
                }
                // URI da imagem recortada
                data?.data != null -> {
                    currentImageUri = data.data
                    loadImagePreview(data.data!!)
                    Toast.makeText(requireContext(), "Imagem recortada!", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(requireContext(), "Nenhuma imagem recortada recebida", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToCache(bitmap: android.graphics.Bitmap): Uri {
        val file = File(requireContext().cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
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
                    <p>Use "Obter Imagem" para abrir o link ou "Colar Imagem" para usar imagem do clipboard</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun setupListeners() {
        binding.getImageButton.setOnClickListener {
            val link = binding.associateLinkEditText.text.toString().trim()
            if (link.isNotEmpty()) {
                openLinkInBrowser(link)
            } else {
                Toast.makeText(requireContext(), "Insira um link de afiliado primeiro", Toast.LENGTH_SHORT).show()
            }
        }

        binding.pasteImageButton.setOnClickListener {
            pasteImageFromClipboard()
        }

        binding.editGroupLinkButton.setOnClickListener {
            showEditLinkDialog("Editar Link do Grupo", groupLink) { newLink ->
                groupLink = newLink
                Toast.makeText(requireContext(), "Link do grupo atualizado!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.clearButton.setOnClickListener { clearAllFields() }
        binding.copyAdButton.setOnClickListener { copyAdToClipboard() }
        setupToggleableRadioButtons()
    }

    private fun setupToggleableRadioButtons() {
        setupToggleGroup(
            listOf(binding.primeRadio, binding.meliPlusRadio),
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

    private fun openLinkInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            Toast.makeText(requireContext(), "Link aberto! Copie a imagem desejada e volte ao app", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao abrir link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteImageFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val uri = item.uri
            
            if (uri != null) {
                showImageOptions(uri)
            } else {
                imagePickerLauncher.launch("image/*")
            }
        } else {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun showImageOptions(sourceUri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle("Usar Imagem")
            .setMessage("Como deseja usar esta imagem?")
            .setPositiveButton("Usar Assim") { _, _ ->
                currentImageUri = sourceUri
                loadImagePreview(sourceUri)
            }
            .setNegativeButton("Escolher Outra") { _, _ ->
                imagePickerLauncher.launch("image/*")
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun startCropActivity(sourceUri: Uri) {
        // Mostrar diálogo de opções
        AlertDialog.Builder(requireContext())
            .setTitle("Recortar Imagem")
            .setMessage("Deseja recortar a imagem antes de usar?")
            .setPositiveButton("Recortar") { _, _ ->
                launchCropIntent(sourceUri)
            }
            .setNegativeButton("Usar Original") { _, _ ->
                currentImageUri = sourceUri
                loadImagePreview(sourceUri)
            }
            .show()
    }

    private fun launchCropIntent(sourceUri: Uri) {
        try {
            // Tentar diferentes abordagens de crop
            val cropIntents = listOf(
                // Intent padrão do Android
                Intent("com.android.camera.action.CROP").apply {
                    setDataAndType(sourceUri, "image/*")
                    putExtra("crop", "true")
                    putExtra("aspectX", 1)
                    putExtra("aspectY", 1)
                    putExtra("outputX", 600)
                    putExtra("outputY", 600)
                    putExtra("return-data", true)
                    putExtra("scale", true)
                    putExtra("scaleUpIfNeeded", true)
                },
                // Intent genérico de edição
                Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(sourceUri, "image/*")
                    putExtra("crop", "true")
                    putExtra("return-data", true)
                },
                // Intent de visualização com opção de edição
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(sourceUri, "image/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            
            var launched = false
            for (intent in cropIntents) {
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    cropLauncher.launch(intent)
                    launched = true
                    break
                }
            }
            
            if (!launched) {
                // Se nenhum crop funcionar, usar imagem original
                currentImageUri = sourceUri
                loadImagePreview(sourceUri)
                Toast.makeText(requireContext(), "Editor de imagem não encontrado. Usando imagem original.", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            currentImageUri = sourceUri
            loadImagePreview(sourceUri)
            Toast.makeText(requireContext(), "Erro ao abrir editor. Usando imagem original.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImagePreview(imageUri: Uri) {
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
                    .image-container { 
                        width: 100%; height: 100%; 
                        display: flex; align-items: center; justify-content: center;
                        background: white;
                        position: relative;
                    }
                    img { 
                        max-width: 100%; max-height: 100%; 
                        object-fit: contain;
                        display: block;
                    }
                    .crop-hint {
                        position: absolute;
                        top: 10px;
                        right: 10px;
                        background: rgba(0,0,0,0.7);
                        color: white;
                        padding: 5px 10px;
                        border-radius: 5px;
                        font-size: 12px;
                        font-family: Arial, sans-serif;
                    }
                </style>
            </head>
            <body>
                <div class="image-container">
                    <img src="$imageUri" alt="Preview da imagem">
                    <div class="crop-hint">✂️ Recortada</div>
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
        binding.freeTextEditText.text?.clear()
        binding.adTitleEditText.text?.clear()
        binding.exclusivityGroup.clearCheck()
        binding.priceEditText.text?.clear()
        binding.fromPriceCheckbox.isChecked = false
        binding.couponEditText.text?.clear()
        binding.paymentScreenDiscountCheckbox.isChecked = false
        binding.shippingOptionsGroup.clearCheck()
        binding.freeShippingAboveEditText.text?.clear()
        binding.groupLinkCheckbox.isChecked = false
        initializePreview()
        currentImageUri = null
    }
    
    private fun copyAdToClipboard() {
        if (currentImageUri != null) {
            copyAdWithImage()
        } else {
            copyAdWithoutImage()
        }
    }

    private fun copyAdWithImage() {
        val adText = buildAdText()
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, currentImageUri)
            putExtra(Intent.EXTRA_TEXT, adText)
        }
        
        startActivity(Intent.createChooser(shareIntent, "Compartilhar anúncio com imagem"))
    }
    
    private fun buildAdText(): String {
        return buildString {
            val freeText = binding.freeTextEditText.text.toString().trim()
            if (freeText.isNotBlank()) {
                appendLine(freeText)
                appendLine()
            }
            
            val adTitle = binding.adTitleEditText.text.toString().trim()
            if (adTitle.isNotBlank()) {
                appendLine(adTitle)
                appendLine()
            }
            
            val exclusivityText = when (binding.exclusivityGroup.checkedRadioButtonId) {
                R.id.prime_radio -> "⭐ Exclusivo Membros Prime"
                R.id.meli_plus_radio -> "⭐ Exclusivo Membros Meli+"
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
            
            when (binding.shippingOptionsGroup.checkedRadioButtonId) {
                R.id.free_shipping_radio -> {
                    appendLine("🚚 Frete Grátis!")
                    appendLine()
                }
                R.id.coupon_shipping_radio -> {
                    appendLine("🚚 Frete Grátis com Cupom!")
                    appendLine()
                }
                R.id.free_shipping_above_radio -> {
                    val minValue = binding.freeShippingAboveEditText.text.toString().trim()
                    if (minValue.isNotBlank()) {
                        appendLine("🚚 Frete Grátis acima de R$ $minValue")
                        appendLine()
                    }
                }
            }
            
            val purchaseLink = binding.associateLinkEditText.text.toString().trim()
            if (purchaseLink.isNotBlank()) {
                appendLine("🛒 Link de Compra:")
                appendLine(purchaseLink)
                appendLine()
            }
            
            if (binding.groupLinkCheckbox.isChecked && groupLink.isNotEmpty()) {
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

        Toast.makeText(requireContext(), "Anúncio copiado!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.geradordeanncios.ui.instagram.tabs

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.geradordeanncios.R
import com.example.geradordeanncios.databinding.FragmentReelsBinding

class ReelsFragment : Fragment() {

    private var _binding: FragmentReelsBinding? = null
    private val binding get() = _binding!!

    private var couponLink = "https://s.shopee.com.br/1g76ck3c1x"
    private var groupLink = "https://chat.whatsapp.com/LyGtLhQqxWbDqjiklHldOm"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            // 1 - Título do Anúncio
            val adTitle = binding.adTitleEditText.text.toString().trim()
            if (adTitle.isNotBlank()) {
                appendLine(adTitle)
                appendLine()
            }

            // 2 - Exclusividade
            val exclusivityText = when (binding.exclusivityGroup.checkedRadioButtonId) {
                R.id.prime_radio -> "⭐ Exclusivo Membros Prime"
                R.id.meli_plus_radio -> "⭐ Exclusivo Membros Meli+"
                R.id.exclusive_radio -> {
                    val customExclusivity = binding.exclusiveFieldEditText.text.toString().trim()
                    if (customExclusivity.isNotEmpty()) "⭐ Exclusivo ${customExclusivity}" else "⭐ Exclusivo"
                }
                else -> ""
            }
            if (exclusivityText.isNotEmpty()) {
                appendLine(exclusivityText)
                appendLine()
            }

            // 3 - Preço
            val price = binding.priceEditText.text.toString().trim()
            if (price.isNotBlank()) {
                val pricePrefix = if (binding.fromPriceCheckbox.isChecked) "A partir de " else ""
                appendLine("💰 ${pricePrefix}R$ ${price}")
                appendLine()
            }

            // 4 - Cupom
            val coupon = binding.couponEditText.text.toString().trim()
            if (coupon.isNotBlank()) {
                appendLine("🎟️ Cupom: `${coupon}`")
                if (binding.paymentScreenDiscountCheckbox.isChecked) {
                    appendLine("*(O desconto entra apenas na tela de pagamento)*")
                }
                appendLine()
            }

            // 5 - Frete
            val shippingText = when (binding.shippingOptionsGroup.checkedRadioButtonId) {
                R.id.free_shipping_radio -> "🚚 Frete Grátis!"
                R.id.coupon_shipping_radio -> "🚚 Frete Grátis com Cupom!"
                R.id.free_shipping_above_radio -> {
                    val amount = binding.freeShippingAboveEditText.text.toString().trim()
                    if (amount.isNotEmpty()) "🚚 Frete Grátis acima de R$ ${amount}" else ""
                }
                else -> ""
            }
            if (shippingText.isNotEmpty()) {
                appendLine(shippingText)
                appendLine()
            }

            // 6 - Link de Compra (Associado)
            val purchaseLink = binding.associateLinkEditText.text.toString().trim()
            if (purchaseLink.isNotBlank()) {
                appendLine("🛒 Link de Compra:")
                appendLine(purchaseLink)
                appendLine()
            }

            // 7 - Link de Cupons
            if (binding.couponLinkCheckbox.isChecked) {
                appendLine("🎟️ Link de Cupons:")
                appendLine(couponLink)
                appendLine()
            }

            // 8 - Link do Grupo
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
        _binding = null
    }
}

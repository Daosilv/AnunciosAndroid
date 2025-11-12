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
import com.example.geradordeanncios.databinding.FragmentPostBinding

class PostFragment : Fragment() {

    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!

    private var groupLink = "https://chat.whatsapp.com/LyGtLhQqxWbDqjiklHldOm"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        binding.linkOptionsGroup.clearCheck()
        binding.priceEditText.text?.clear()
        binding.productDescriptionEditText.text?.clear()
        binding.hashtagsEditText.text?.clear()
        binding.groupLinkCheckbox.isChecked = false
        Toast.makeText(requireContext(), "Campos limpos!", Toast.LENGTH_SHORT).show()
    }

    private fun copyAdToClipboard() {
        val adText = buildString {
            // 1. Descrição do Produto
            val description = binding.productDescriptionEditText.text.toString().trim()
            if (description.isNotBlank()) {
                appendLine(description)
                appendLine()
            }

            // 2. Preço
            val price = binding.priceEditText.text.toString().trim()
            if (price.isNotBlank()) {
                appendLine("💰 R$ ${price}")
                appendLine()
            }

            // 3. Link Options
            when (binding.linkOptionsGroup.checkedRadioButtonId) {
                R.id.inform_link_radio -> {
                    val purchaseLink = binding.associateLinkEditText.text.toString().trim()
                    if (purchaseLink.isNotBlank()) {
                        appendLine("🛒 Link de Compra:")
                        appendLine(purchaseLink)
                        appendLine()
                    }
                }
                R.id.link_in_bio_radio -> {
                    appendLine("🔗 Link na bio")
                    appendLine()
                }
            }

            // 5. Hashtags
            val hashtags = binding.hashtagsEditText.text.toString().trim()
            if (hashtags.isNotBlank()) {
                appendLine(hashtags)
                appendLine()
            }

            // 6. Link do Grupo
            if (binding.groupLinkCheckbox.isChecked) {
                appendLine("Indique o grupo para amigos: $groupLink")
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

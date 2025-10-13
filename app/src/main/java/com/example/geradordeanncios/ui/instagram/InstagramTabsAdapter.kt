package com.example.geradordeanncios.ui.instagram

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.geradordeanncios.ui.instagram.tabs.PostFragment
import com.example.geradordeanncios.ui.instagram.tabs.ReelsFragment
import com.example.geradordeanncios.ui.instagram.tabs.StoryFragment

class InstagramTabsAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PostFragment()
            1 -> StoryFragment()
            2 -> ReelsFragment()
            else -> throw IllegalStateException("Posição inválida: $position")
        }
    }
}
